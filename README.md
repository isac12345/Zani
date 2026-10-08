# Zani

Screen recorder ringan. Rekam dan stop lewat tombol melayang di atas game; tanpa iklan, tanpa internet, tanpa library tambahan.

- Java + framework Android saja (minSdk 26, targetSdk 34)
- Rekam: MediaProjection -> VirtualDisplay -> MediaRecorder (encoder hardware), 24/30/60/90/120 fps, H.264/HEVC, audio mic / audio internal (Android 10+, bisa pilih aplikasi) / keduanya
- Tombol melayang: rekam/stop, jeda, screenshot, mic, sembunyikan; geser + nempel tepi; hitung mundur
- Tile Pengaturan Cepat, pintasan ikon, notifikasi dengan aksi Jeda/Stop/Screenshot
- Hasil: Movies/Zani (video), Pictures/Zani (screenshot)

Build: lihat `BUILD_PROMPT.md` (lokal dengan Gradle 8.5 + JDK 17 + Android SDK 34, atau GitHub Actions).
