# Download Manager Client

Aplikasi klien Android untuk mengontrol **Download Manager** (server HTTP) dari perangkat lain.

- Masukkan alamat server (IP:port) + PIN, lalu terhubung, atau tekan **Cari server di jaringan** untuk menemukan server secara otomatis (harus satu jaringan WiFi yang sama).
- **Tangkap link download**: share link dari Firefox/browser lain ke aplikasi ini (atau tempel link), lalu kirim langsung ke server — download langsung berjalan di perangkat server.
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
2. Di aplikasi ini: tekan **Cari server di jaringan** (harus satu jaringan yang sama) atau isi alamat IP + port (contoh: `192.168.1.10:8080`) + PIN.
3. Tekan **Hubungkan**. Untuk mengirim link download: di Firefox/browser lain, long-press link → **Bagikan** → pilih aplikasi ini.
