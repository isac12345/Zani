package id.zani.rec;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Menangkap audio internal (suara internal/aplikasi) lewat AudioPlaybackCapture (Android 10+), opsional dicampur mikrofon,
 * lalu di-encode ke AAC di berkas sementara. Digabung dengan video setelah rekaman selesai (tanpa re-encode video).
 */
final class AudioCapture implements Runnable {
    static final int SR = 44100, N = 1024;

    private final AudioRecord inner, mic;
    private final File out;
    private volatile boolean stop, paused;
    private volatile long startNs;
    private Thread t;
    private MediaCodec enc;
    private MediaMuxer mux;
    private int track = -1;
    private boolean started;
    private final MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();

    private AudioCapture(AudioRecord i, AudioRecord m, File o) {
        inner = i;
        mic = m;
        out = o;
    }

    static AudioCapture create(Context c, MediaProjection proj, boolean useMic, String[] pkgs, File out) throws Exception {
        AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
        int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int buf = Math.max(min, N * 4) * 4;
        AudioPlaybackCaptureConfiguration.Builder cb = new AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN);
        PackageManager pm = c.getPackageManager();
        for (String p : pkgs) {
            try { cb.addMatchingUid(pm.getApplicationInfo(p, 0).uid); } catch (Exception ignore) { }
        }
        AudioRecord in = new AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(buf)
                .setAudioPlaybackCaptureConfig(cb.build())
                .build();
        if (in.getState() != AudioRecord.STATE_INITIALIZED) {
            in.release();
            throw new IllegalStateException("playback capture");
        }
        AudioRecord mc = null;
        if (useMic) {
            try {
                int mb = Math.max(AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), N * 2) * 2;
                mc = new AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mb);
                if (mc.getState() != AudioRecord.STATE_INITIALIZED) { mc.release(); mc = null; }
            } catch (Throwable e) {
                mc = null;
            }
        }
        return new AudioCapture(in, mc, out);
    }

    void start() {
        t = new Thread(this, "zani-audio");
        t.start();
    }

    void setPaused(boolean p) { paused = p; }

    /** Hentikan dan tunggu berkas AAC selesai ditulis. */
    void finish() {
        stop = true;
        if (t != null) {
            try { t.join(5000); } catch (InterruptedException ignore) { }
        }
    }

    void abort() {
        stop = true;
        if (t == null) {
            try { inner.release(); } catch (Exception ignore) { }
            if (mic != null) { try { mic.release(); } catch (Exception ignore) { } }
        }
    }

    long offsetUs(long videoStartNs) { return Math.max(0, (startNs - videoStartNs) / 1000); }

    private static short clip(int v) { return (short) Math.max(-32768, Math.min(32767, v)); }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        try {
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 2);
            f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            f.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, N * 8);
            enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            enc.start();
            mux = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            inner.startRecording();
            if (mic != null) mic.startRecording();
            startNs = System.nanoTime();
            short[] pcm = new short[N * 2];
            short[] mm = new short[N];
            long frames = 0;
            while (!stop) {
                int got = inner.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                if (got < 0) break;
                if (got == 0) continue;
                int fr = got / 2;
                if (paused) {
                    if (mic != null) mic.read(mm, 0, N, AudioRecord.READ_NON_BLOCKING);
                    continue;
                }
                if (mic != null) {
                    int mg = mic.read(mm, 0, fr, AudioRecord.READ_NON_BLOCKING);
                    if (mg < 0) mg = 0;
                    for (int i = 0; i < fr; i++) {
                        int mv = i < mg ? mm[i] : 0;
                        pcm[2 * i] = clip(pcm[2 * i] + mv);
                        pcm[2 * i + 1] = clip(pcm[2 * i + 1] + mv);
                    }
                }
                queue(pcm, fr * 2, frames);
                frames += fr;
                drain(false);
            }
            for (int tries = 0; tries < 50; tries++) {
                int idx = enc.dequeueInputBuffer(10000);
                if (idx >= 0) {
                    enc.queueInputBuffer(idx, 0, 0, frames * 1000000L / SR, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                }
                drain(false);
            }
            drain(true);
        } catch (Throwable ignore) {
            // audio gagal: video tetap disimpan tanpa suara
        } finally {
            try { inner.stop(); } catch (Throwable ignore) { }
            try { inner.release(); } catch (Throwable ignore) { }
            if (mic != null) {
                try { mic.stop(); } catch (Throwable ignore) { }
                try { mic.release(); } catch (Throwable ignore) { }
            }
            try { if (enc != null) { enc.stop(); enc.release(); } } catch (Throwable ignore) { }
            try { if (mux != null) { if (started) mux.stop(); mux.release(); } } catch (Throwable ignore) { }
        }
    }

    private void queue(short[] pcm, int n, long frames) {
        for (int tries = 0; tries < 6; tries++) {
            int idx = enc.dequeueInputBuffer(10000);
            if (idx >= 0) {
                ByteBuffer ib = enc.getInputBuffer(idx);
                ib.clear();
                ib.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, 0, n);
                enc.queueInputBuffer(idx, 0, n * 2, frames * 1000000L / SR, 0);
                return;
            }
            drain(false);
        }
    }

    private void drain(boolean end) {
        int spins = 0;
        while (true) {
            int o = enc.dequeueOutputBuffer(bi, end ? 10000 : 0);
            if (o == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!end || ++spins > 100) break;
                continue;
            }
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = mux.addTrack(enc.getOutputFormat());
                mux.start();
                started = true;
                continue;
            }
            if (o < 0) continue;
            ByteBuffer ob = enc.getOutputBuffer(o);
            if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
            if (bi.size > 0 && started && ob != null) {
                ob.position(bi.offset);
                ob.limit(bi.offset + bi.size);
                mux.writeSampleData(track, ob, bi);
            }
            boolean eos = (bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            enc.releaseOutputBuffer(o, false);
            if (eos) break;
        }
    }
}
