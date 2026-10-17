# Zani 1.1.0 - Release Notes

Rilis publik pertama. Tanggal: 18 Oktober 2026. Setiap baris di bawah bisa ditelusuri ke commit atau kode.

## Isi rilis publik pertama

- Audio internal: rekam suara game tanpa mikrofon, digabung dengan video lewat mux (commit bc1cdff, mode `Prefs.INT`, butuh Android 10+). Fitur ini sudah ada di dalam tag v1.0.0, dibuktikan `git merge-base --is-ancestor bc1cdff v1.0.0` yang keluar rc=0.
- Pemilih audio dalam aplikasi: mikrofon, audio internal, atau keduanya (commit bc1cdff).
- Log crash lokal tanpa mengorbankan ringan APK (commit bc1cdff, tulis ke `crash.txt` di folder internal app).
- Perbaikan: drag tombol melayang tidak lagi membuat layar mati mendadak (snap-crash, commit bc1cdff).
- Perbaikan tata letak: baris dengan empat pilihan atau lebih ditumpuk vertikal supaya teks tidak terpotong (commit e98ea3b, juga sudah ada di dalam tag v1.0.0).
- Tiga berkas uji unit baru: degrade dan listener (b23172c), uji State.fmt dan Caps.desc (68c5361), keranjang 1 dengan 17 pemeriksaan (152ffb7).
- Tanda tangan APK rilis memakai kunci sendiri dari secrets, bukan kunci debug (commit 6a1a61a, perbaikan urutan signingConfigs 79f5282, perbaikan nama secret a5d023c).
- Dokumentasi: README ringkas dengan tiga tangkapan layar (f2d9811, d6ff7b4, 6730c46), versi Inggris (b50364a), LICENSE MIT (2fa4539), cara pasang (b2f37e6).

## Catatan versi

- Ini rilis publik pertama, jadi tidak ada pengguna lama yang perlu upgrade dari versi publik sebelumnya.
- Tag v1.0.0 tetap ada dan tidak disentuh. applicationId tetap `id.zani.rec`, jadi instalasi baru maupun yang ada tidak kehilangan data.

## Belum diuji (jujur)

- Perilaku Android 14 untuk tipe foreground service sudah diperiksa di kode tapi belum diuji di HP Android 14 sungguhan.
- Kecepatan 120 fps belum diukur di perangkat mana pun.
- Belum ada uji UI atau instrumentation sama sekali; yang ada hanya tiga berkas uji unit.
- Belum diuji di semua merek dan versi Android. Perangkat yang membatasi rekam layar, overlay, atau audio internal bisa gagal.
- Belum diuji upgrade dari 1.0.0 ke 1.1.0 pada perangkat yang sama.
- APK rilis dengan obfuscation R8 belum diuji manual di perangkat; ada risiko nama kelas atau action Intent terpengaruh oleh obfuscation.
- Skenario yang belum diuji di HP Android 14: audio disetel Mic, izin mikrofon sudah diberikan, lalu rekam dimulai dari tombol melayang saat aplikasi game berada di depan.

## Cara verifikasi build ini

1. `apksigner verify --print-certs Zani-1.1.0.apk` dan cocokkan sidik jari dengan kunci milikmu sendiri.
2. Cocokkan `sha256sum` dengan berkas `SHA256SUMS.txt` di halaman Releases.