# Download Manager Client

Aplikasi klien Android untuk mengontrol **Download Manager** (server HTTP) dari perangkat lain.

- Masukkan alamat server (IP:port) + PIN, lalu terhubung.
- UI server dimuat di WebView dengan cookie login — semua fitur server tersedia:
  download, file manager, galeri, upload, dark mode, dan lainnya.
- Upload file dari perangkat klien via pemilih file Android (multi-file).
- Download disimpan oleh Download Manager sistem Android.

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Cara pakai

1. Buka aplikasi **Download Manager** di perangkat server, nyalakan server remote.
2. Catat alamat IP + port (contoh: `192.168.1.10:8080`) dan PIN jika dipasang.
3. Buka aplikasi ini, isi alamat + port + PIN, tekan **Hubungkan**.
