# Bypass 48kHz Resampling (Bit-Perfect Hi-Res Audio for Android)

[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-238636?style=for-the-badge&logo=android)](https://kernelsu.org/)
[![Magisk](https://img.shields.io/badge/Magisk-Supported-00B0FF?style=for-the-badge&logo=android)](https://github.com/topjohnwu/Magisk)
[![APatch](https://img.shields.io/badge/APatch-Supported-E91E63?style=for-the-badge&logo=android)](https://github.com/bmax121/APatch)
[![Author](https://img.shields.io/badge/Author-Deco--Dev-blue?style=for-the-badge)](https://github.com/soju-sec)

Modul KernelSU / Magisk / APatch untuk **mem-bypass resampling paksa 48kHz Android**, membuka jalur audio **Bit-Perfect Hi-Res (Direct PCM & Qualcomm DSP USB Offload)**, serta dilengkapi **WebUI Real-Time Monitor** untuk melacak status frekuensi hardware DAC secara langsung.

---

## 🌟 Fitur Utama

- 🎵 **Bypass 48kHz SRC Android**: Membuka jalur streaming audio langsung di sample rate asli lagu (44.1kHz, 88.2kHz, 96kHz, 176.4kHz, hingga 192kHz/384kHz).
- 🎧 **Dukungan Penuh Apple Music & TIDAL**:
  - **Apple Music**: ALAC Lossless (44.1kHz) & Hi-Res Lossless (24-bit/96kHz & 192kHz).
  - **TIDAL**: FLAC Hi-Res (Max 96kHz/192kHz) & HiFi (44.1kHz).
- 🛠️ **Fix Suara Sember / Hening pada USB DAC**:
  - Mengunci konfigurasi channel mask ke **Stereo (2-Channel) & Mono**.
  - Mencegah driver audio mencoba mengirim 8-channel (7.1 surround) ke DAC eksternal 2-channel yang menyebabkan distorsi sember atau error hening (*Invalid argument* di ALSA).
- ⚡ **Qualcomm Hexagon DSP USB Offload**:
  - Memaksimalkan efisiensi baterai dan latensi rendah dengan memanfaatkan coprocessor Hexagon DSP (`USB_AUDIO_RX`).
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
   - [`Bypass_48kHz_Resampling_BitPerfect_v2.5.zip`](Bypass_48kHz_Resampling_BitPerfect_v2.5.zip)
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
   - **`BIT-PERFECT HI-RES (96.0 kHz / 192.0 kHz)`**
   - **`BIT-PERFECT CD (44.1 kHz)`**

### 2. Lewat Terminal / Termux (Manual)
Jalankan perintah berikut dengan akses root saat musik sedang berputar:

```bash
su -c "dumpsys media.audio_flinger | grep -B 12 'Output devices: 0x4000000'"
```

**Contoh Output Berhasil:**
```text
Output thread 0xb400007dd8816790, name AudioOut_15, type 0 (MIXER):
  Standby: no
  Sample rate: 192000 Hz
  HAL format: 0x5 (AUDIO_FORMAT_PCM_FLOAT)
  Output devices: 0x4000000 (AUDIO_DEVICE_OUT_USB_HEADSET)
```

Untuk memeriksa konfigurasi backend DSP Qualcomm:
```bash
su -c "logcat -d | grep 'Override USB BE' | tail -n 3"
```
**Output:**
```text
Override USB BE configured as bit_width(24)sample_rate(192000)channels(2)
sn_device 105 device sr 192000 stream sr 192000 copp sr 192000
```

---

## 📂 Struktur File Modul

```text
├── module.prop                                     # Metadata modul & identitas pembuat
├── service.sh                                      # Script otomatis saat boot selesai
├── post-fs-data.sh                                 # Script inisialisasi awal sistem
├── system.prop                                     # Flag audio Android & Qualcomm properties
├── system/
│   └── vendor/
│       └── etc/
│           ├── audio_policy_configuration.xml     # Kebijakan routing Direct PCM & Deep Buffer
│           └── audio_effects.xml                  # Konfigurasi efek audio
├── webroot/
│   └── index.html                                  # WebUI Monitor Bit-Perfect KernelSU
├── Bypass_48kHz_Resampling_BitPerfect_v2.5.zip     # Flashable Zip Installer siap pakai
└── README.md                                       # Dokumentasi
```

---

## 👨‍💻 Pembuat (Author)

- **Oleh**: **Deco-Dev** ([@soju-sec](https://github.com/soju-sec))
- **Email**: clashroyaledeco@gmail.com

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi [MIT](LICENSE). Bebas digunakan, dipelajari, dan dimodifikasi untuk kebutuhan audiofil Android.
