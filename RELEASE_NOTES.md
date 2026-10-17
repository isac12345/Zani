# Zani 1.1.0 - Release Notes

Rilis kedua. Tanggal: 18 Oktober 2026. Setiap baris di bawah bisa ditelusuri ke commit atau kode.

## Yang baru di 1.1.0

- Audio internal: rekam suara game tanpa mikrofon, digabung dengan video lewat mux (commit bc1cdff, mode `Prefs.INT`, butuh Android 10+).
- Pemilih audio dalam aplikasi: mikrofon, audio internal, atau keduanya (commit bc1cdff).
- Log crash lokal tanpa mengorbankan ringan APK (commit bc1cdff, tulis ke `crash.txt` di folder internal app).
- Perbaikan: drag tombol melayang tidak lagi membuat layar mati mendadak (snap-crash, commit bc1cdff).
- Perbaikan tata letak: baris dengan empat pilihan atau lebih ditumpuk vertikal supaya teks tidak terpotong (commit e98ea3b).
- Tiga berkas uji unit baru: degrade dan listener (b23172c), uji State.fmt dan Caps.desc (68c5361), keranjang 1 dengan 17 pemeriksaan (152ffb7).
- Tanda tangan APK rilis memakai kunci sendiri dari secrets, bukan kunci debug (commit 6a1a61a, perbaikan urutan signingConfigs 79f5282, perbaikan nama secret a5d023c).
- Dokumentasi: README ringkas dengan tiga tangkapan layar (f2d9811, d6ff7b4, 6730c46), versi Inggris (b50364a), LICENSE MIT (2fa4539), cara pasang (b2f37e6).

## Yang tidak berubah

- Versi 1.0.0 tetap ada sebagai tag dan tidak disentuh. Aplikasi tidak kehilangan data apa pun saat diperbarui karena applicationId tetap `id.zani.rec`.

## Belum diuji (jujur)

- Perilaku Android 14 untuk tipe foreground service sudah diperiksa di kode tapi belum diuji di HP Android 14 sungguhan.
- Kecepatan 120 fps belum diukur di perangkat mana pun.
- Belum ada uji UI atau instrumentation sama sekali; yang ada hanya tiga berkas uji unit.
- Belum diuji di semua merek dan versi Android. Perangkat yang membatasi rekam layar, overlay, atau audio internal bisa gagal.
- Belum diuji upgrade dari 1.0.0 ke 1.1.0 pada perangkat yang sama.
- APK rilis dengan obfuscation R8 belum diuji manual di perangkat; ada risiko nama kelas atau action Intent terpengaruh oleh obfuscation.

## Cara verifikasi build ini

1. `apksigner verify --print-certs Zani-1.1.0.apk` dan cocokkan sidik jari dengan kunci milikmu sendiri.
2. Cocokkan `sha256sum` dengan berkas `SHA256SUMS.txt` di halaman Releases.