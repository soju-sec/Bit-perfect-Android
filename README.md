# Bit-Perfect Audio Monitor & Bypass Suite for Android

[![Android](https://img.shields.io/badge/Android-8.0%20--%2015%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![KowSU](https://img.shields.io/badge/KowSU%20UAPI%205-Verified-7952B3?style=for-the-badge&logo=android)](https://github.com/kowx712)
[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-238636?style=for-the-badge&logo=android)](https://kernelsu.org/)
[![Magisk](https://img.shields.io/badge/Magisk-Supported-00B0FF?style=for-the-badge&logo=android)](https://github.com/topjohnwu/Magisk)
[![APatch](https://img.shields.io/badge/APatch-Supported-E91E63?style=for-the-badge&logo=android)](https://github.com/bmax121/APatch)
[![Author](https://img.shields.io/badge/Author-Deco--Dev-blue?style=for-the-badge)](https://github.com/soju-sec)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

Aplikasi Android standalone native (tanpa perlu flash modul zip Magisk/KernelSU) untuk **mem-bypass resampling paksa 48kHz Android**, membuka jalur audio **Bit-Perfect Hi-Res (Direct PCM, USBFS, & Qualcomm DSP Offload)**, memantau telemetri clock hardware DAC secara real-time, serta mencegah masalah video patah-patah pada aplikasi non-musik dengan fitur **Per-App Mixer Bypass**.

---

## 🌟 Fitur Utama

- 💎 **100% Standalone APK (Tanpa Modul Flashable)**:
  - Menggantikan modul Magisk/KernelSU lama seutuhnya. Seluruh injeksi konfigurasi audio policy (`audio_policy_configuration.xml`, `audio_io_policy.conf`), patch library direct output, dan restart audio HAL dikendalikan langsung secara on-demand melalui aplikasi dengan izin root.
- 🎯 **Per-App Mixer Bypass (Solusi Bebas Stutter Video)**:
  - **Masalah modul global lama**: Memaksa semua audio ke thread `DIRECT (Type 1)` membuat pemutar video (YouTube, TikTok, Netflix, Instagram) mengalami desinkronisasi PTS frame video sehingga video menjadi patah-patah / stutter parah.
  - **Solusi aplikasi ini**: Anda bebas memilih aplikasi mana yang mem-bypass mixer (misal **Apple Music**, **TIDAL**, **Qobuz**, **Bitchord**). Aplikasi video tetap berjalan mulus di mixer sistem 48kHz tanpa gangguan.
- 📊 **Pemantauan Telemetri Real-Time (Live Bit-Perfect Dashboard)**:
  - **Player Aktif**: Mendeteksi nama aplikasi pemutar musik secara dinamis via `PackageManager`.
  - **Metadata Trek**: Judul lagu dan nama artis secara langsung dari sesi media Android.
  - **Source Stream**: Resolusi asli trek (kHz) dan Bit Depth (16-bit / 24-bit / 32-bit).
  - **Hardware DAC Clock**: Frekuensi clock hardware DAC yang aktif (44.1k, 48k, 88.2k, 96k, 176.4k, 192k, hingga 384k).
  - **Jalur Audio**: Konfirmasi status `Zero SRC 1:1 • Direct PCM (Bit-Perfect)` vs `Android AudioMixer (Resampled)`.
- 🪟 **Floating Frosted Glass Dock (Glassmorphism)**:
  - Desain navigasi bawah bergaya *frosted smoked glass* dengan specular flare lengkungan atas, batas kaca translusen 1.2dp, dan feedback haptic/ripple melengkung.
- 🛡️ **Proteksi Re-Clocking & Reset Aman**:
  - Mencegah audio bocor ke speaker ponsel saat DAC eksternal berganti frekuensi clock / reset engine dengan auto-mute protektif.
- 🎛️ **Manual & Auto Clock Sync**:
  - Pilihan sinkronisasi jam otomatis (*Auto-Follow Track Rate*) atau pengaturan frekuensi DAC manual (44.1k–384k).

---

## 📱 Hardware & Lingkungan Pengujian

- **Device**: POCO F4 (`munch`, Qualcomm Snapdragon 870 / Kona)
- **OS / ROM**: RisingOS / LineageOS (Android 15 / SDK 35)
- **Root Solution**: KowSU UAPI 5 / KernelSU / Magisk / APatch
- **External DAC Tested**: **Kiwi Ears AD1** (USB-C Audio DAC, 2-Channel Stereo, 44.1k–384k PCM, 16/24/32-bit)
- **Aplikasi Musik Teruji**: Apple Music, TIDAL, Bitchord, Spotify, Qobuz, Poweramp, UAPP

---

## 📥 Pemasangan & Penggunaan

### 1. Download & Install APK
Unduh file APK rilis terbaru dari halaman [Releases](../../releases) atau ambil langsung file [`BitPerfectMonitor.apk`](BitPerfectMonitor.apk):

```bash
adb install -r BitPerfectMonitor.apk
```

Atau salin file APK ke ponsel Anda dan pasang melalui file manager bawaan.

### 2. Berikan Izin Root
Saat pertama kali membuka aplikasi, berikan izin Superuser / Root melalui prompt manager root Anda (**KowSU**, **KernelSU**, **APatch**, atau **Magisk**).

### 3. Aktifkan Bypass
1. Buka tab **Bypass** di menu bawah.
2. Centang pemutar musik yang ingin Anda bypass (misal TIDAL atau Apple Music).
3. Buka tab **Dashboard** dan putar lagu Hi-Res.
4. Badge status akan langsung menyala hijau:
   * **`Zero SRC 1:1 • Direct PCM (Bit-Perfect)`**

---

## 🔍 Verifikasi Status di Terminal (Opsional)

Untuk memastikan bahwa audio mengalir tanpa resampling di level hardware, jalankan perintah ini via Termux atau ADB saat musik berputar:

```bash
su -c "dumpsys media.audio_flinger | grep -E -A 10 'Output thread.*type 1 \(DIRECT\)'"
```

Untuk memeriksa status driver kernel Qualcomm DSP:
```bash
su -c "dmesg | grep -E 'handle_uaudio_stream_req.*en:1' | tail -n 1"
```
Output:
```text
[handle_uaudio_stream_req: card#:1 dev#:0 dir:0 en:1 fmt:10 rate:96000 #ch:2]
```
*(Sample rate kernel otomatis terkunci 1:1 sesuai trek lagu tanpa upsampling mixer).*

---

## 📂 Struktur Repositori

```text
├── BitPerfectApp/                   # Source code proyek aplikasi Android
│   ├── AndroidManifest.xml          # Manifest aplikasi
│   ├── assets/                      # Binary patch & audio policy configuration
│   ├── build_apk.sh                 # Script build native tanpa Gradle
│   ├── res/                         # Asset layout, drawable glass dock, values
│   └── src/dev/deco/bitperfect/     # Source Java (AudioEngine & MainActivity)
├── BitPerfectMonitor.apk            # Pre-compiled signed release APK
├── LICENSE                          # Lisensi MIT
└── README.md                        # Dokumentasi resmi
```

---

## 👨‍💻 Pembuat (Author)

- **Oleh**: **Deco-Dev** ([@soju-sec](https://github.com/soju-sec))
- **Email**: clashroyaledeco@gmail.com

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi [MIT](LICENSE). Bebas digunakan, dipelajari, dan dimodifikasi untuk kebutuhan audiofil Android.
