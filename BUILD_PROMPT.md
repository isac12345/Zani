# PROMPT BUILD — Zani (screen recorder)

Salin seluruh isi file ini ke AI coding agent (opencode / Claude Code / dll) yang membuka folder proyek ini.

---

Kamu bekerja di proyek Android bernama **Zani** (package `id.zani.rec`), screen recorder berbasis framework Android murni
(Java, TANPA dependency apa pun: tanpa AndroidX, Compose, Kotlin, Firebase, Material, ExoPlayer). Tugasmu: **build APK release**
dan perbaiki error build apa pun TANPA mengubah arsitektur.

## Aturan wajib (tidak boleh dilanggar)
1. **Ringan.** Jangan tambah library/dependency. APK harus tetap kecil (target < 2 MB). Biarkan `minifyEnabled` dan `shrinkResources` aktif.
2. **Smooth dan tidak bikin game stutter**, walau semua setelan rekam diset tinggi (1080p/Layar, 120 fps, bitrate tinggi, HEVC):
   - Jalur rekam HARUS tetap: `MediaProjection -> VirtualDisplay -> Surface MediaRecorder` (encoder hardware, tanpa salin frame di CPU).
     Jangan ganti ke ImageReader/MediaCodec manual/FFmpeg untuk rekaman video.
   - Selama merekam, JANGAN menambah thread, polling, animasi, `invalidate` periodik, atau log berat.
     Satu-satunya pekerjaan periodik yang boleh: update teks timer di tombol melayang 1x per detik.
   - Pertahankan "batas aman otomatis" (`Caps.resolve`) dan tangga fallback (`Caps.degrade` + loop retry di `RecorderService.startRec`)
     supaya setelan tinggi yang ditolak encoder otomatis turun (120 -> 60 -> 30 fps, HEVC -> H.264, lalu resolusi), bukan crash.
   - Thread screenshot memakai prioritas background dan hanya hidup sesaat.
3. **Semua tombol harus berfungsi tanpa error/crash:** saklar tombol melayang, tile Pengaturan Cepat, pintasan ikon,
   menu melayang (rekam/stop, jeda/lanjut, screenshot, mic, sembunyikan), geser + nempel tepi, hitung mundur 3-2-1,
   daftar rekaman (sortir, putar, bagikan, hapus), semua setelan, tombol izin.
4. Mendukung **120 fps**: opsi 24/30/60/90/120; nilai dibatasi ke kemampuan layar (refresh rate maks) dan encoder HP.
5. Jangan ubah desain/tema (Material You ungu, banner/wallpaper/ikon di `res/drawable-nodpi`). Jangan hapus fitur.

## Cara build (pilih yang tersedia)
**A. Lokal (JDK 17 + Android SDK platform 34 + build-tools 34 + Gradle 8.5):**
```
gradle assembleRelease --no-daemon
```
Jika `ANDROID_HOME` belum diset: set ke folder SDK dan buat `local.properties` berisi `sdk.dir=<path SDK>`.
Hasil: `app/build/outputs/apk/release/app-release.apk` (sudah ditandatangani debug key, langsung bisa di-install).

**B. GitHub Actions (kalau HP/PC tidak punya SDK):** push proyek ke repo GitHub. Workflow `.github/workflows/build.yml`
otomatis build dan mengunggah artifact `Zani-apk`. Baca log kalau gagal, perbaiki, push lagi sampai hijau.

## Kalau ada error compile
- Kode ini ditulis tanpa sempat dikompilasi, jadi mungkin ada salah ketik/impor/API kecil. Perbaiki seminimal mungkin
  (impor, tipe, casting, nama method). Jangan menulis ulang file atau mengganti pendekatan.
- Jangan menaikkan `targetSdk`/`compileSdk` dan jangan menambah dependency hanya untuk meredam error.
- Setelah build sukses, jalankan `grep -rn "TODO\|FIXME" app/src` dan pastikan kosong.

## Hasil akhir yang harus kamu laporkan
1. Path APK, ukurannya, dan daftar perubahan yang kamu buat (per file, singkat).
2. Konfirmasi tiap aturan wajib di atas masih terpenuhi.
3. Daftar hal yang tidak bisa kamu uji tanpa perangkat (misal rekaman 120 fps asli).

## Checklist uji manual di HP (untuk pengguna)
- [ ] Nyalakan "Tombol melayang" -> tombol Zani muncul di atas aplikasi lain, bisa digeser, nempel tepi.
- [ ] Buka game -> ketuk tombol Zani -> rekam -> dialog izin sistem -> hitung mundur 3-2-1 -> timer jalan di tombol.
- [ ] Jeda/lanjut, screenshot saat merekam, stop -> muncul di tab Rekaman, bisa diputar/dibagikan/dihapus.
- [ ] Setel 1080p/Layar + 120 fps + bitrate tinggi -> main game 5 menit, bandingkan fps game dengan dan tanpa rekam.
- [ ] Notifikasi punya aksi Jeda / Stop / Screenshot. Tile Pengaturan Cepat menyalakan/mematikan tombol melayang.
