# Bit-Perfect Audio Monitor & Bypass Suite for Android

[![Android](https://img.shields.io/badge/Android-8.0%20--%2017%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![KowSU](https://img.shields.io/badge/KowSU%20UAPI%205-Verified-7952B3?style=for-the-badge&logo=android)](https://github.com/kowx712)
[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-238636?style=for-the-badge&logo=android)](https://kernelsu.org/)
[![Magisk](https://img.shields.io/badge/Magisk-Supported-00B0FF?style=for-the-badge&logo=android)](https://github.com/topjohnwu/Magisk)
[![APatch](https://img.shields.io/badge/APatch-Supported-E91E63?style=for-the-badge&logo=android)](https://github.com/bmax121/APatch)
[![Author](https://img.shields.io/badge/Author-soju--sec-blue?style=for-the-badge)](https://github.com/soju-sec)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

Aplikasi Android native standalone untuk mem-bypass resampling 48 kHz bawaan Android, membuka jalur audio Bit-Perfect Hi-Res (Direct PCM, USBFS, dan Qualcomm DSP Offload), memantau telemetri clock hardware DAC secara real-time, serta mencegah stuttering pada pemutaran video melalui fitur Per-App Mixer Bypass.

---

## Fitur Utama

- **Aplikasi Standalone Native**: Menggantikan modul flashable Magisk/KernelSU. Injeksi audio policy (`audio_policy_configuration.xml`, `audio_io_policy.conf`), patch library direct output, dan restart audio HAL dikendalikan langsung secara on-demand melalui aplikasi dengan hak akses root.
- **Per-App Mixer Bypass**: Menghilangkan masalah desinkronisasi audio/video pada pemutar video. Pengguna dapat memilih aplikasi musik tertentu yang mem-bypass mixer (seperti Apple Music, TIDAL, Qobuz, Bitchord), sementara aplikasi video (YouTube, TikTok, Netflix, Instagram) tetap menggunakan mixer sistem 48 kHz agar video berjalan lancar tanpa frame drop.
- **Pemantauan Telemetri Real-Time**:
  - Pemutar Aktif: Mendeteksi nama aplikasi pemutar audio melalui `PackageManager`.
  - Informasi Trek: Menampilkan judul lagu dan artis dari sesi media Android.
  - Parameter Sumber: Menampilkan sample rate asli dan bit depth (16-bit, 24-bit, 32-bit).
  - Clock Hardware DAC: Frekuensi clock hardware aktif pada DAC (44.1 kHz hingga 384 kHz).
  - Status Jalur: Verifikasi status `Zero SRC 1:1 • Direct PCM (Bit-Perfect)` atau `Android AudioMixer (Resampled)`.
- **Navigasi Frosted Glass**: Antarmuka navigasi bawah bergaya frosted glass dengan batas translusen dan feedback sentuhan responsif.
- **Proteksi Re-Clocking**: Mencegah audio bocor ke speaker perangkat saat DAC eksternal berganti frekuensi clock atau saat reset engine melalui mekanisme mute sementara.
- **Sinkronisasi Clock Otomatis dan Manual**: Mendukung sinkronisasi frekuensi otomatis mengikuti trek serta pemilihan frekuensi DAC manual (44.1 kHz hingga 384 kHz).

---

## Lingkungan Pengujian

- Perangkat: POCO F4 (`munch`, Qualcomm Snapdragon 870)
- Sistem Operasi: RisingOS (Android 17)
- Manajemen Root: KowSU UAPI 5 / KernelSU / Magisk / APatch
- DAC Eksternal: Kiwi Ears AD1 (USB-C Audio DAC, stereo 2-channel, 44.1 kHz - 384 kHz PCM, 16/24/32-bit)
- Pemutar Teruji: Apple Music, TIDAL, Bitchord, Spotify, Qobuz, Poweramp, UAPP

---

## Pemasangan dan Penggunaan

### 1. Pemasangan APK
Unduh file APK dari halaman [Releases](../../releases) atau gunakan file `BitPerfectMonitor.apk`:

```bash
adb install -r BitPerfectMonitor.apk
```

File APK juga dapat disalin langsung ke penyimpanan perangkat dan dipasang melalui pengelola berkas.

### 2. Pemberian Izin Root
Saat aplikasi pertama kali dijalankan, berikan izin Superuser (Root) melalui prompt manajer root yang terpasang (KowSU, KernelSU, APatch, atau Magisk).

### 3. Konfigurasi Bypass
1. Buka tab Bypass pada menu navigasi bawah.
2. Pilih aplikasi pemutar musik yang ingin diarahkan ke jalur Direct PCM.
3. Buka tab Dashboard dan mulai pemutaran lagu Hi-Res.
4. Indikator status akan menampilkan status aktif `Zero SRC 1:1 • Direct PCM (Bit-Perfect)`.

---

## Verifikasi Teknis

Untuk memverifikasi aliran audio tanpa resampling pada level hardware melalui terminal:

```bash
su -c "dumpsys media.audio_flinger | grep -E -A 10 'Output thread.*type 1 \(DIRECT\)'"
```

Untuk memeriksa status driver kernel Qualcomm DSP:
```bash
su -c "dmesg | grep -E 'handle_uaudio_stream_req.*en:1' | tail -n 1"
```

Contoh keluaran driver kernel yang tersinkronisasi 1:1:
```text
[handle_uaudio_stream_req: card#:1 dev#:0 dir:0 en:1 fmt:10 rate:96000 #ch:2]
```

---

## Struktur Repositori

```text
├── BitPerfectApp/                   # Kode sumber aplikasi Android
│   ├── AndroidManifest.xml          # Manifest aplikasi
│   ├── assets/                      # Binary patch dan konfigurasi audio policy
│   ├── build_apk.sh                 # Skrip build mandiri
│   ├── res/                         # Tata letak, drawable, dan resource
│   └── src/dev/deco/bitperfect/     # Kode sumber Java (AudioEngine dan MainActivity)
├── BitPerfectMonitor.apk            # Paket instalasi APK rilis bertanda tangan
├── LICENSE                          # Lisensi MIT
└── README.md                        # Dokumentasi teknis proyek
```

---

## Author

- Pengembang: **soju-sec** ([@soju-sec](https://github.com/soju-sec))

---

## Lisensi

Proyek ini didistribusikan di bawah lisensi [MIT](LICENSE).
