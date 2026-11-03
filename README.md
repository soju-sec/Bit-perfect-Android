# Bypass 48kHz Resampling (Bit-Perfect Hi-Res Audio for Android)

[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-238636?style=for-the-badge&logo=android)](https://kernelsu.org/)
[![KowSU](https://img.shields.io/badge/KowSU%20UAPI%205-Verified-7952B3?style=for-the-badge&logo=android)](https://github.com/kowx712)
[![Magisk](https://img.shields.io/badge/Magisk-Supported-00B0FF?style=for-the-badge&logo=android)](https://github.com/topjohnwu/Magisk)
[![APatch](https://img.shields.io/badge/APatch-Supported-E91E63?style=for-the-badge&logo=android)](https://github.com/bmax121/APatch)
[![Author](https://img.shields.io/badge/Author-Deco--Dev-blue?style=for-the-badge)](https://github.com/soju-sec)

Modul KernelSU / KowSU / Magisk / APatch untuk **mem-bypass resampling paksa 48kHz Android**, membuka jalur audio **Bit-Perfect Hi-Res (Direct PCM & Qualcomm DSP USB Offload)**, mengatasi stuttering pada lagu Hi-Res, serta dilengkapi **WebUI Real-Time Monitor (kompatibel penuh dengan KowSU UAPI 5)** untuk melacak status frekuensi hardware DAC secara langsung.

---

## 🌟 Fitur Utama

- 🎵 **Bypass 48kHz SRC & Mixer Upsampling**: Membuka jalur streaming audio murni 1:1 di sample rate asli lagu (44.1kHz, 48kHz, 88.2kHz, 96kHz, 176.4kHz, hingga 192kHz/384kHz) tanpa upsampling paksa mixer ke 192kHz atau downsampling ke 48kHz.
- 🎧 **Dukungan Murni Bit-Perfect Apple Music & TIDAL**:
  - **TIDAL**: FLAC Hi-Res (Max 96kHz/192kHz) & FLAC HiFi (44.1kHz) langsung diteruskan ke `type 1 (DIRECT)` output thread tanpa software mixer.
  - **Apple Music**: ALAC Lossless (44.1kHz/48kHz) & Hi-Res Lossless (24-bit/96kHz & 192kHz) langsung ke DAC hardware endpoint.
- 💎 **Prioritas Profil 24-bit Packed & 32-bit Integer (Fix 16-bit Lock)**:
  - Mengatur urutan format profile pada `bit_perfect` & `direct_pcm` di `audio_policy_configuration.xml` dan `audio_io_policy.conf` agar `AUDIO_FORMAT_PCM_24_BIT_PACKED` dan `AUDIO_FORMAT_PCM_32_BIT` berada di urutan teratas sebelum 16-bit.
  - Memastikan DAC menerima stream dalam format hardware asli `S24_3LE` (Altset 2) via Qualcomm DSP USB offload (`fmt:10`) tanpa konversi mixer.
- 🚀 **Fix Stuttering / Sendat-sendat pada TIDAL Hi-Res**:
  - Menonaktifkan `vendor.audio.feature.usb_offload_burst_mode.enable=false` yang menyebabkan micro-underrun pada endpoint UAC2 USB DAC.
  - Menambahkan konfigurasi overlay `/vendor/etc/audio_io_policy.conf` untuk mengalokasikan sesi DSP Hi-Res 24-bit (`app_type 69940`) saat aplikasi meminta flag Direct PCM / Deep Buffer.
  - Menyetel multiplier buffer `af.fast_track_multiplier=2` dan standby timeout `ro.audio.flinger_standbytime_ms=1000` untuk transisi trek yang mulus tanpa tabrakan stream.
- 🚀 **Zero-Stutter Bit-Perfect & Clean Session Routing (v3.3)**:
  - Proteksi tabrakan audio track (`restoreTrack_l`) antara TIDAL dan Apple Music.
  - Penambahan tombol **Bebaskan Output** di WebUI untuk membersihkan sesi audio lama secara instan jika berpindah antar aplikasi pemutar musik.
  - Transisi streaming hardware DAC yang mulus tanpa underrun pada frekuensi 44.1kHz, 48kHz, 96kHz, hingga 192kHz.
- ⚡ **AudioPolicy Direct Output Unlock (`libaudiopolicymanagerdefault.so`)**:
  - Mem-bypass batasan Android yang memaksa audio media (`AUDIO_OUTPUT_FLAG_DEEP_BUFFER`) masuk ke software mixer.
  - Mengizinkan pembukaan jalur `direct_pcm` murni secara dinamis untuk endpoint USB Audio.
- 🛠️ **Fix Distorsi / Suara Sember pada USB DAC**:
  - Mengunci konfigurasi channel mask ke **Stereo (2-Channel) & Mono**.
  - Mencegah driver audio mencoba mengirim 8-channel (7.1 surround) ke DAC eksternal 2-channel yang menyebabkan distorsi sember atau error hening (*Invalid argument* di ALSA).
- 📊 **WebUI Bit-Perfect Tracker (Kompatibel KowSU UAPI 5)**:
  - Menggunakan modul script eksternal `monitor.sh` dan bridge `ksuExec` standar KowSU (`com.kowx712.supermanager`).
  - Responsif, tidak lag/hang, langsung mendeteksi status pemutaran real-time, frekuensi DAC hardware, bit depth kernel DSP, dan rute Direct HAL.

---

## 📱 Hardware & Lingkungan Pengujian

- **Device**: POCO F4 (`munch`, Qualcomm Snapdragon 870 / Kona)
- **OS / ROM**: RisingOS (Android 15 / SDK 35)
- **Root Solution**: KowSU UAPI 5 (`com.kowx712.supermanager`) / KernelSU v3.x / APatch / Magisk
- **External DAC Tested**: **Kiwi Ears AD1** (USB-C Audio DAC, 2-Channel Stereo, 44.1k–384k PCM, 16/24/32-bit)
- **Aplikasi Uji**: Apple Music (v4.9+), TIDAL (v2.120+), Bitchord

---

## 📱 Bit-Perfect Suite (Aplikasi Android Standalone)

Aplikasi **Bit-Perfect Suite** (`BitPerfectMonitor.apk`) kini menggantikan seluruh fungsionalitas modul Magisk/KernelSU lama. Anda dapat **mencopot/meng-uninstall modul Magisk lama**, karena seluruh kontrol bypass mixer, deteksi DAC, dan konfigurasi audio HAL kini dikendalikan secara on-demand langsung dari aplikasi!

### ❓ Mengapa Video Sempat Patah-patah pada Modul Global?
1. **Eksklusivitas Direct PCM**: Modul global memaksa audio media ke thread `type 1 (DIRECT)`. Thread Direct di AudioFlinger **tidak mendukung mixing**, hanya bisa memproses 1 stream tunggal.
2. **Desinkronisasi Frame ExoPlayer (PTS)**: Pemutar video (YouTube, TikTok, Instagram, browser) menyelaraskan tampilan frame video langsung dengan jam audio (*Presentation Timestamp / PTS* via `AudioTrack.getTimestamp()`).
3. Saat audio masuk ke jalur Direct PCM tanpa buffer mixer bersama, suara klik sistem, notifikasi, atau seek video menyebabkan stream underrun. Mesin video langsung menahan/membuang frame video karena menunggu jam audio, menyebabkan **video patah-patah / stutter parah**.

### 💡 Fitur Utama Bit-Perfect Suite:
- 🚀 **Bypass Mixer Tanpa Modul Magisk**: Membuka jalur streaming audio murni hingga 384 kHz, mengalokasikan DSP 24-bit, dan menonaktifkan burst mode langsung dari aplikasi via Root Engine.
- 🎯 **Pilihan Aplikasi (Per-App Mixer Bypass)**:
  - Anda bebas memilih aplikasi mana yang mem-bypass mixer (misal **Apple Music**, **TIDAL**, **Bitchord**).
  - Aplikasi video (YouTube, TikTok, Netflix, Gallery) **tetap di mixer 48 kHz** agar video lancar jaya 100% tanpa patah-patah.
- 📊 **Deteksi & Telemetri Lengkap**:
  - **Aplikasi Aktif**: Mendeteksi nama & package pemutar musik yang sedang aktif.
  - **Resolusi Lagu**: Sample rate (kHz) dan Bit Depth (16-bit / 24-bit / 32-bit).
  - **Status Jalur**: Direct PCM (1:1 Bit-Perfect), Mixer Aktif (48 kHz), Offload, atau Standby.
  - **Hardware DAC**: Nama perangkat (misal Kiwi Ears AD1), Clock hardware aktif (kHz), Format PCM (S24_3LE / S32_LE), dan interface Altset.
  - **Penyelarasan Jam**: Konfirmasi 1:1 Tanpa Resampling (*Zero SRC*).
- 🛡️ **Proteksi Re-Clocking (Jeda ±3.5 Detik)**: Musik otomatis dijeda sebentar saat perpindahan sample rate agar audio tidak bocor ke speaker ponsel saat DAC melakukan re-clocking.
- 🎛️ **Kontrol Frekuensi Manual**: Tombol instan untuk `44.1k`, `48k`, `88.2k`, `96k`, `176.4k`, `192k`, `384k`, dan `Reset (48k)`.
- 🗑️ **Detektor Modul Lama**: Memberikan status jika ada modul Magisk lama yang terpasang dan menyediakan panduan aman untuk menghapusnya.

### 📥 Pemasangan APK:
Install langsung file [`BitPerfectMonitor.apk`](BitPerfectMonitor.apk) di HP Anda:
```bash
adb install -r BitPerfectMonitor.apk
```

---

## 🚀 Cara Pemasangan

1. Unduh file zip flashable terbaru dari repositori ini:
   - [`Bypass_48kHz_Resampling_BitPerfect_v3.3.zip`](Bypass_48kHz_Resampling_BitPerfect_v3.3.zip)
2. Buka manajer root Anda (**KernelSU**, **KowSU**, **APatch**, atau **Magisk**).
3. Masuk ke menu **Modul** -> **Pasang dari penyimpanan**.
4. Pilih file zip yang sudah diunduh, pasang, lalu **Reboot** perangkat Anda.

---

## 🔍 Cara Verifikasi Bit-Perfect

### 1. Lewat WebUI KernelSU / KowSU
1. Buka aplikasi **KernelSU / KowSU** -> Masuk ke tab **Modul**.
2. Klik tombol **WebUI** pada modul **Bypass 48kHz Resampling (Hi-Res Bit-Perfect)**.
3. Putar lagu di Apple Music atau TIDAL.
4. Status badge akan langsung menyala hijau:
   - **`BIT-PERFECT 1:1 DIRECT (96.0 kHz)`** (saat lagu 96kHz diputar)
   - **`BIT-PERFECT 1:1 DIRECT (44.1 kHz)`** (saat lagu 44.1kHz diputar)
   - **`BIT-PERFECT 1:1 DIRECT (48.0 kHz)`** (saat lagu 48kHz diputar)
   - AudioFlinger Mixer: **`BYPASSED (Zero Mixer)`**

### 2. Lewat Terminal / Termux (Manual)
Jalankan perintah berikut dengan akses root saat musik sedang berputar:

```bash
su -c "dumpsys media.audio_flinger | grep -E -A 10 'Output thread.*type 1 \(DIRECT\)'"
```

**Contoh Output Berhasil (Direct PCM Passthrough):**
```text
Output thread 0xb40000790f62f010, name AudioOut_4D, tid 24059, type 1 (DIRECT):
  I/O handle: 77
  Standby: no
  Sample rate: 96000 Hz
  HAL frame count: 3840
  HAL format: 0x1 (AUDIO_FORMAT_PCM_16_BIT)
  Output devices: 0x4000000 (AUDIO_DEVICE_OUT_USB_HEADSET)
```

Untuk memeriksa driver kernel USB backend Qualcomm:
```bash
su -c "dmesg | grep 'handle_uaudio_stream_req.*en:1' | tail -n 1"
```
**Output:**
```text
[47590.760134] handle_uaudio_stream_req: card#:1 dev#:0 dir:0 en:1 fmt:10 rate:96000 #ch:2
```
*(Sample rate kernel otomatis sinkron 1:1 dengan audio trek tanpa upsampling).*

---

## 📂 Struktur File Modul

```text
├── module.prop                                     # Metadata modul & identitas pembuat
├── monitor.sh                                      # Script telemetry monitor audio (KowSU UAPI 5)
├── service.sh                                      # Script otomatis saat boot selesai
├── post-fs-data.sh                                 # Bind-mount library & konfigurasi audio
├── system.prop                                     # Flag audio Android & Qualcomm properties
├── system/
│   ├── lib64/
│   │   └── libaudiopolicymanagerdefault.so        # Binary patch AudioPolicy openDirectOutput
│   └── vendor/
│       └── etc/
│           ├── audio_policy_configuration.xml     # Dynamic direct_pcm route USB DAC
│           ├── audio_io_policy.conf               # Direct PCM & DSP 24-bit app_type mapping
│           └── audio_effects.xml                  # Konfigurasi efek audio
├── webroot/
│   └── index.html                                  # WebUI Monitor Bit-Perfect KernelSU
├── Bypass_48kHz_Resampling_BitPerfect_v3.3.zip     # Flashable Zip Installer siap pakai
└── README.md                                       # Dokumentasi
```

---

## 👨‍💻 Pembuat (Author)

- **Oleh**: **Deco-Dev** ([@soju-sec](https://github.com/soju-sec))
- **Email**: clashroyaledeco@gmail.com

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi [MIT](LICENSE). Bebas digunakan, dipelajari, dan dimodifikasi untuk kebutuhan audiofil Android.
