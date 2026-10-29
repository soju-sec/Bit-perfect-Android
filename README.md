# Bypass 48kHz Resampling (Bit-Perfect Hi-Res Audio for Android)

[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-238636?style=for-the-badge&logo=android)](https://kernelsu.org/)
[![Magisk](https://img.shields.io/badge/Magisk-Supported-00B0FF?style=for-the-badge&logo=android)](https://github.com/topjohnwu/Magisk)
[![APatch](https://img.shields.io/badge/APatch-Supported-E91E63?style=for-the-badge&logo=android)](https://github.com/bmax121/APatch)
[![Author](https://img.shields.io/badge/Author-Deco--Dev-blue?style=for-the-badge)](https://github.com/soju-sec)

Modul KernelSU / Magisk / APatch untuk **mem-bypass resampling paksa 48kHz Android**, membuka jalur audio **Bit-Perfect Hi-Res (Direct PCM & Qualcomm DSP USB Offload)**, serta dilengkapi **WebUI Real-Time Monitor** untuk melacak status frekuensi hardware DAC secara langsung.

---

## 🌟 Fitur Utama

- 🎵 **Bypass 48kHz SRC & Mixer Upsampling**: Membuka jalur streaming audio murni 1:1 di sample rate asli lagu (44.1kHz, 48kHz, 88.2kHz, 96kHz, 176.4kHz, hingga 192kHz/384kHz) tanpa upsampling paksa mixer ke 192kHz atau downsampling ke 48kHz.
- 🎧 **Dukungan Murni Bit-Perfect Apple Music & TIDAL**:
  - **TIDAL**: FLAC Hi-Res (Max 96kHz/192kHz) & FLAC HiFi (44.1kHz) langsung diteruskan ke `type 1 (DIRECT)` output thread.
  - **Apple Music**: ALAC Lossless (44.1kHz/48kHz) & Hi-Res Lossless (24-bit/96kHz & 192kHz) langsung ke DAC hardware endpoint.
- ⚡ **AudioPolicy Direct Output Unlock (`libaudiopolicymanagerdefault.so`)**:
  - Mem-bypass batasan Android yang memaksa audio media (`AUDIO_OUTPUT_FLAG_DEEP_BUFFER`) masuk ke software mixer.
  - Mengizinkan pembukaan jalur `direct_pcm` murni secara dinamis untuk endpoint USB Audio.
- 🛠️ **Fix Distorsi / Suara Sember pada USB DAC**:
  - Mengunci konfigurasi channel mask ke **Stereo (2-Channel) & Mono**.
  - Mencegah driver audio mencoba mengirim 8-channel (7.1 surround) ke DAC eksternal 2-channel yang menyebabkan distorsi sember atau error hening (*Invalid argument* di ALSA).
- 📊 **KernelSU WebUI Bit-Perfect Monitor**:
  - Tampilan monitor modern bawaan modul untuk melihat frekuensi real-time, format bit depth, jalur HAL, dan status sinkronisasi DAC.
  - Otomatis mendeteksi pemutar aktif (*TIDAL, Apple Music, Spotify, Qobuz, HiBy, Poweramp, dll.*).

---

## 📱 Hardware & Lingkungan Pengujian

- **Device**: POCO F4 (`munch`, Qualcomm Snapdragon 870 / Kona)
- **OS / ROM**: RisingOS (Android 15 / SDK 35)
- **Root Solution**: KernelSU v3.x / Next
- **External DAC Tested**: **Kiwi Ears AD1** (USB-C Audio DAC, 2-Channel Stereo, 44.1k–384k PCM, 16/24/32-bit)
- **Aplikasi Uji**: Apple Music (v4.9+) & TIDAL (v2.120+)

---

## 🚀 Cara Pemasangan

1. Unduh file zip flashable terbaru dari repositori ini:
   - [`Bypass_48kHz_Resampling_BitPerfect_v3.0.zip`](Bypass_48kHz_Resampling_BitPerfect_v3.0.zip)
2. Buka manajer root Anda (**KernelSU**, **APatch**, atau **Magisk**).
3. Masuk ke menu **Modul** -> **Pasang dari penyimpanan**.
4. Pilih file zip yang sudah diunduh, pasang, lalu **Reboot** perangkat Anda.

---

## 🔍 Cara Verifikasi Bit-Perfect

### 1. Lewat WebUI KernelSU
1. Buka aplikasi **KernelSU** -> Masuk ke tab **Modul**.
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
Output thread 0xb40000773026d820, name AudioOut_6D, tid 19700, type 1 (DIRECT):
  I/O handle: 109
  Standby: no
  Sample rate: 96000 Hz
  HAL frame count: 3840
  HAL format: 0x5 (AUDIO_FORMAT_PCM_FLOAT)
  Output devices: 0x4000000 (AUDIO_DEVICE_OUT_USB_HEADSET)
```

Untuk memeriksa driver kernel USB backend Qualcomm:
```bash
su -c "dmesg | grep 'msm_pcm_routing_reg_stream_app_type_cfg.*be_id 152' | tail -n 1"
```
**Output:**
```text
[43916.271689] msm_pcm_routing_reg_stream_app_type_cfg: fedai_id 0, session_type 0, be_id 152, app_type 69937, acdb_dev_id 10, sample_rate 96000
```
*(Sample rate kernel otomatis berganti mengikuti resolusi trek 1:1).*

---

## 📂 Struktur File Modul

```text
├── module.prop                                     # Metadata modul & identitas pembuat
├── service.sh                                      # Script otomatis saat boot selesai
├── post-fs-data.sh                                 # Bind-mount library & konfigurasi audio
├── system.prop                                     # Flag audio Android & Qualcomm properties
├── system/
│   ├── lib64/
│   │   └── libaudiopolicymanagerdefault.so        # Binary patch AudioPolicy openDirectOutput
│   └── vendor/
│       └── etc/
│           ├── audio_policy_configuration.xml     # Dynamic direct_pcm route USB DAC
│           └── audio_effects.xml                  # Konfigurasi efek audio
├── webroot/
│   └── index.html                                  # WebUI Monitor Bit-Perfect KernelSU
├── Bypass_48kHz_Resampling_BitPerfect_v3.0.zip     # Flashable Zip Installer siap pakai
└── README.md                                       # Dokumentasi
```

---

## 👨‍💻 Pembuat (Author)

- **Oleh**: **Deco-Dev** ([@soju-sec](https://github.com/soju-sec))
- **Email**: clashroyaledeco@gmail.com

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi [MIT](LICENSE). Bebas digunakan, dipelajari, dan dimodifikasi untuk kebutuhan audiofil Android.
