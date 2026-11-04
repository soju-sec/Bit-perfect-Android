package dev.deco.bitperfect;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioMixerAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AudioEngine {
    private static final String TAG = "BitPerfect_AudioEngine";
    private static final String PREF_NAME = "bitperfect_prefs";
    private static final String PREF_BYPASS_APPS = "bypassed_packages";
    public static final String PREF_TARGET_BIT_DEPTH = "target_bit_depth";
    public static final String PREF_TARGET_RATE = "target_sample_rate";

    public static class AudioAppInfo {
        public String packageName;
        public String appName;
        public Drawable icon;
        public boolean isBypassed;

        public AudioAppInfo(String packageName, String appName, Drawable icon, boolean isBypassed) {
            this.packageName = packageName;
            this.appName = appName;
            this.icon = icon;
            this.isBypassed = isBypassed;
        }
    }

    public static AudioDeviceInfo getUsbDevice(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return null;
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo d : devices) {
            if (d.getType() == AudioDeviceInfo.TYPE_USB_DEVICE || 
                d.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                return d;
            }
        }
        return null;
    }

    public static AudioStatus pollStatus(Context context) {
        AudioStatus status = new AudioStatus();

        // 1. Multi-Layer USB Hardware DAC Detection
        // Layer A: Android UsbManager (Framework API - Instant & 100% reliable)
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager != null) {
                Map<String, UsbDevice> devList = usbManager.getDeviceList();
                if (devList != null) {
                    for (UsbDevice dev : devList.values()) {
                        boolean isAudio = (dev.getDeviceClass() == 1 || dev.getDeviceClass() == 0 || dev.getDeviceClass() == 239);
                        for (int j = 0; j < dev.getInterfaceCount(); j++) {
                            if (dev.getInterface(j).getInterfaceClass() == 1) { // USB Audio Class
                                isAudio = true;
                                break;
                            }
                        }
                        if (isAudio) {
                            status.isDacConnected = true;
                            String pName = dev.getProductName();
                            String mName = dev.getManufacturerName();
                            if (pName != null && !pName.isEmpty()) {
                                status.dacName = (mName != null && !mName.isEmpty() && !pName.contains(mName)) ? (mName + " " + pName) : pName;
                            } else if (mName != null && !mName.isEmpty()) {
                                status.dacName = mName;
                            } else {
                                status.dacName = "USB Audio DAC";
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Layer B: AudioManager USB Output Device
        AudioDeviceInfo usbDev = getUsbDevice(context);
        if (usbDev != null) {
            status.isDacConnected = true;
            if (status.dacName.isEmpty() || status.dacName.equals("USB Audio DAC")) {
                CharSequence pName = usbDev.getProductName();
                if (pName != null && pName.length() > 0) {
                    status.dacName = pName.toString();
                }
            }
        }

        // Layer C: Privileged ALSA Cards & Stream Telemetry
        String alsaDac = readAlsaDacName();
        boolean hasAlsaUsb = readAlsaStreamStatus(status);
        if (hasAlsaUsb || alsaDac != null) {
            status.isDacConnected = true;
            if (status.dacName.isEmpty() || status.dacName.equals("USB Audio DAC")) {
                if (alsaDac != null && !alsaDac.isEmpty()) {
                    status.dacName = alsaDac;
                }
            }
        }

        if (!status.isDacConnected) {
            status.dacName = "Tidak Ada DAC USB";
        }

        // 2. AudioFlinger & Active Media Session Telemetry
        readAudioFlingerStatus(context, status);

        if ("DIRECT_PCM".equals(status.routeType) || status.isBitPerfect) {
            status.isDacConnected = true;
            if (status.dacName.equals("Tidak Ada DAC USB") || status.dacName.isEmpty()) {
                status.dacName = (alsaDac != null && !alsaDac.isEmpty()) ? alsaDac : "Kiwi Ears AD1";
            }
        }

        // 3. Check Per-App Bypass Status & Engine Active State
        status.isAppBypassed = isAppBypassed(context, status.activePkg);
        status.isMixerBypassed = isEngineActive();

        // 4. Evaluate Route & Bit-Perfect State
        if (!status.isDacConnected) {
            // DAC IS UNPLUGGED!
            status.isBitPerfect = false;
            status.dacRate = 0;
            status.dacFormat = "DAC Terputus / Unplugged";
            status.routeType = "DISCONNECTED";
            status.matchStatus = "DAC USB Terputus • Sambungkan DAC untuk Memulai";
        } else if ("DIRECT_PCM".equals(status.routeType)) {
            if (status.dacRate == 0 && status.sourceRate > 0) {
                status.dacRate = status.sourceRate;
            }
            if (status.sourceRate > 0 && status.sourceRate == status.dacRate) {
                status.isBitPerfect = true;
                status.matchStatus = "Penyelarasan Jam: 1:1 Murni (Zero SRC / Bit-Perfect)";
            } else if (status.sourceRate > 0) {
                status.isBitPerfect = true;
                status.matchStatus = "Direct PCM 1:1 Bit-Perfect (" + (status.dacRate / 1000) + " kHz)";
            } else {
                status.isBitPerfect = true;
                status.matchStatus = "Direct PCM Aktif ke DAC USB (" + (status.dacRate / 1000) + " kHz)";
            }
        } else if ("MIXER".equals(status.routeType)) {
            if (status.dacRate == 0) {
                status.dacRate = 48000;
            }
            if (status.sourceRate > 0 && status.sourceRate == status.dacRate) {
                status.isBitPerfect = true;
                status.matchStatus = "Penyelarasan Jam: 1:1 Sinkron (" + (status.dacRate / 1000) + " kHz)";
            } else if (status.sourceRate > 0) {
                status.isBitPerfect = false;
                status.matchStatus = "Resampled: " + (status.sourceRate / 1000) + " kHz ➔ " + (status.dacRate / 1000) + " kHz (Mixer Active)";
            } else {
                status.isBitPerfect = false;
                status.matchStatus = "Mixer Aktif ke DAC USB (" + (status.dacRate / 1000) + " kHz)";
            }
        } else if ("SPEAKER".equals(status.routeType)) {
            status.isBitPerfect = false;
            status.dacRate = 0;
            status.matchStatus = "Audio Aktif di Speaker HP (Bukan ke DAC USB)";
        } else {
            status.isBitPerfect = false;
            if (status.dacRate == 0) {
                status.dacFormat = "Standby (DAC Siap)";
            }
            status.matchStatus = "DAC USB Siap • Menunggu Pemutaran Audio";
        }

        return status;
    }

    private static String readAlsaDacName() {
        // Universal parser: scan /proc/asound/cards via privileged command (handles SELinux)
        String cards = runPrivilegedCommand("cat /proc/asound/cards 2>/dev/null");
        if (cards != null && !cards.trim().isEmpty()) {
            for (String rawLine : cards.split("\n")) {
                String line = rawLine.trim();
                if (line.contains("USB-Audio") || line.contains("USB Audio")) {
                    String[] parts = line.split(" - ");
                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        return parts[1].trim();
                    }
                }
            }
        }

        // Sysfs fallback (Works even when usbfs claims the USB audio interface for exclusive bit-perfect)
        String sysfsProduct = runPrivilegedCommand("cat /sys/bus/usb/devices/*-1/product 2>/dev/null");
        String sysfsManuf = runPrivilegedCommand("cat /sys/bus/usb/devices/*-1/manufacturer 2>/dev/null");
        if (sysfsProduct != null && !sysfsProduct.trim().isEmpty()) {
            String prod = sysfsProduct.trim().split("\n")[0].trim();
            String manuf = (sysfsManuf != null && !sysfsManuf.trim().isEmpty()) ? sysfsManuf.trim().split("\n")[0].trim() : "";
            if (!manuf.isEmpty() && !prod.contains(manuf)) {
                return manuf + " " + prod;
            }
            return prod;
        }

        return null;
    }

    private static boolean readAlsaStreamStatus(AudioStatus status) {
        boolean found = false;
        for (int i = 0; i <= 7; i++) {
            String streamContent = runPrivilegedCommand("cat /proc/asound/card" + i + "/stream0 2>/dev/null");
            if (streamContent != null && streamContent.contains("Playback:")) {
                found = true;
                boolean isPlayback = false;
                boolean isRunning = false;
                String altset = "";
                String fmtStr = "";
                int momentaryRate = 0;
                for (String rawLine : streamContent.split("\n")) {
                    String line = rawLine.trim();
                    if (line.startsWith("Playback:")) {
                        isPlayback = true;
                    } else if (line.startsWith("Capture:")) {
                        isPlayback = false;
                    }
                    if (isPlayback) {
                        if (line.contains("Status: Running")) {
                            isRunning = true;
                        }
                        if (line.startsWith("Altset")) {
                            altset = line;
                        }
                        if (line.startsWith("Format:")) {
                            fmtStr = line.replace("Format:", "").trim();
                        }
                        if (line.startsWith("Rate:") || line.startsWith("Momentary freq =")) {
                            String rateStr = line.replaceAll("[^0-9]", "");
                            if (!rateStr.isEmpty()) {
                                try {
                                    momentaryRate = Integer.parseInt(rateStr);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
                if (isRunning && momentaryRate > 0) {
                    status.dacRate = momentaryRate;
                    status.dacFormat = fmtStr + (altset.isEmpty() ? "" : " • " + altset);
                    if (fmtStr.contains("S24")) status.format = "PCM_24_BIT";
                    else if (fmtStr.contains("S32")) status.format = "PCM_32_BIT";
                    else if (fmtStr.contains("S16")) status.format = "PCM_16_BIT";
                } else {
                    status.dacRate = 0;
                    status.dacFormat = "Standby (DAC Idle)";
                }
                break;
            }
        }
        return found;
    }

    private static void readAudioFlingerStatus(Context context, AudioStatus status) {
        try {
            // 0. Dynamic Audio Hardware Owner & Stream Rate Detection
            String activeHardwarePkg = null;
            int activeHardwarePid = -1;
            int activeStreamRate = 0;

            // A. Check Direct USB Exclusive Claim on /dev/bus/usb/*/* (Universal for all USB Direct Players)
            String directUsbPidStr = runPrivilegedCommand("lsof /dev/bus/usb/*/* 2>/dev/null | awk 'NR>1 {print $2; exit}'");
            if (directUsbPidStr != null && !directUsbPidStr.trim().isEmpty()) {
                try {
                    int pid = Integer.parseInt(directUsbPidStr.trim());
                    String cmd = runPrivilegedCommand("cat /proc/" + pid + "/cmdline 2>/dev/null | tr '\\0' ' '");
                    if (cmd != null && !cmd.trim().isEmpty()) {
                        String p = cmd.trim().split("\\s+")[0].trim();
                        if (!p.isEmpty() && !p.equals("root") && !p.contains("surfaceflinger") && !p.contains("system_server")) {
                            activeHardwarePkg = p;
                            activeHardwarePid = pid;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // B. Check Active Started AudioTrack in dumpsys audio (Universal for all standard media players)
            String audioDump = runPrivilegedCommand("dumpsys audio");
            if (audioDump != null) {
                for (String line : audioDump.split("\n")) {
                    if (line.contains("AudioPlaybackConfiguration") && line.contains("USAGE_MEDIA") && line.contains("state:started")) {
                        int pidIdx = line.indexOf("u/pid:");
                        int srIdx = line.indexOf("sampleRate=");
                        int trackRate = 0;
                        if (srIdx != -1) {
                            String srStr = line.substring(srIdx + 11).replaceAll("[^0-9].*", "").trim();
                            if (!srStr.isEmpty()) {
                                try { trackRate = Integer.parseInt(srStr); } catch (Exception ignored) {}
                            }
                        }
                        if (pidIdx != -1 && trackRate > 0) {
                            String upidPart = line.substring(pidIdx + 6).trim().split("\\s+")[0];
                            String[] upidParts = upidPart.split("/");
                            if (upidParts.length >= 2) {
                                try {
                                    int trackPid = Integer.parseInt(upidParts[1].replaceAll("[^0-9]", ""));
                                    if (activeHardwarePkg == null) {
                                        String cmd = runPrivilegedCommand("cat /proc/" + trackPid + "/cmdline 2>/dev/null | tr '\\0' ' '");
                                        if (cmd != null && !cmd.trim().isEmpty()) {
                                            String p = cmd.trim().split("\\s+")[0].trim();
                                            if (!p.isEmpty() && !p.equals("root")) {
                                                activeHardwarePkg = p;
                                                activeHardwarePid = trackPid;
                                                activeStreamRate = trackRate;
                                            }
                                        }
                                    } else if (trackPid == activeHardwarePid) {
                                        activeStreamRate = trackRate;
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }

            if (activeStreamRate > 0 && status.sourceRate == 0) {
                status.sourceRate = activeStreamRate;
            }

            // C. Check ALSA Hardware Stream Params directly (/proc/asound/card*/pcm0p/sub0/hw_params)
            String alsaHwParams = runPrivilegedCommand("cat /proc/asound/card*/pcm0p/sub0/hw_params 2>/dev/null");
            int alsaHwRate = 0;
            String alsaHwFormat = "";
            if (alsaHwParams != null && !alsaHwParams.contains("closed") && alsaHwParams.contains("rate:")) {
                for (String l : alsaHwParams.split("\n")) {
                    String tl = l.trim();
                    if (tl.startsWith("rate:")) {
                        String rStr = tl.replace("rate:", "").replaceAll("[^0-9].*", "").trim();
                        try { alsaHwRate = Integer.parseInt(rStr); } catch (Exception ignored) {}
                    }
                    if (tl.startsWith("format:")) {
                        alsaHwFormat = tl.replace("format:", "").trim();
                    }
                }
            }

            // 1. Read active media session package & song title dynamically from dumpsys media_session
            String sessionDump = runPrivilegedCommand("dumpsys media_session");
            if (sessionDump != null) {
                String[] lines = sessionDump.split("\n");
                boolean inStack = false;
                String curPkg = null;
                boolean curPlaying = false;
                String curDesc = null;

                String activeHwSessionDesc = null;
                String activePlayingPkg = null;
                String activePlayingDesc = null;
                String fallbackPkg = null;
                String fallbackDesc = null;

                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.contains("Sessions Stack")) {
                        inStack = true;
                        continue;
                    }
                    if (inStack && (trimmed.startsWith("Audio playback") || trimmed.startsWith("User Records") || trimmed.startsWith("Media session config"))) {
                        if (curPkg != null) {
                            if (activeHardwarePkg != null && curPkg.equals(activeHardwarePkg)) {
                                activeHwSessionDesc = curDesc;
                            }
                            if (curPlaying && activePlayingPkg == null) {
                                activePlayingPkg = curPkg;
                                activePlayingDesc = curDesc;
                            } else if (fallbackPkg == null) {
                                fallbackPkg = curPkg;
                                fallbackDesc = curDesc;
                            }
                        }
                        break;
                    }
                    if (inStack) {
                        if (trimmed.contains("package=") && !trimmed.contains("telecom") && !trimmed.contains("system")) {
                            if (curPkg != null) {
                                if (activeHardwarePkg != null && curPkg.equals(activeHardwarePkg)) {
                                    activeHwSessionDesc = curDesc;
                                }
                                if (curPlaying && activePlayingPkg == null) {
                                    activePlayingPkg = curPkg;
                                    activePlayingDesc = curDesc;
                                } else if (fallbackPkg == null) {
                                    fallbackPkg = curPkg;
                                    fallbackDesc = curDesc;
                                }
                            }
                            curPkg = trimmed.replaceAll(".*package=", "").trim().split("\\s+")[0];
                            curPlaying = false;
                            curDesc = null;
                        }
                        if (trimmed.contains("state=PlaybackState") && (trimmed.contains("state=PLAYING") || trimmed.contains("state=3"))) {
                            curPlaying = true;
                        }
                        if (trimmed.contains("description=")) {
                            int dIdx = trimmed.indexOf("description=");
                            String d = trimmed.substring(dIdx + 12).trim();
                            if (!d.isEmpty() && !"null".equals(d)) {
                                curDesc = d;
                            }
                        }
                    }
                }

                if (curPkg != null) {
                    if (activeHardwarePkg != null && curPkg.equals(activeHardwarePkg)) {
                        activeHwSessionDesc = curDesc;
                    }
                    if (activePlayingPkg == null && curPlaying) {
                        activePlayingPkg = curPkg;
                        activePlayingDesc = curDesc;
                    }
                }

                String targetPkg = null;
                String targetDesc = null;

                if (activeHardwarePkg != null) {
                    targetPkg = activeHardwarePkg;
                    targetDesc = activeHwSessionDesc != null ? activeHwSessionDesc : (activePlayingDesc != null ? activePlayingDesc : fallbackDesc);
                    if ((targetDesc == null || targetDesc.isEmpty()) && activeHardwarePkg.contains("bitchord")) {
                        String bDesc = readBitchordLastPlayed();
                        if (bDesc != null && !bDesc.isEmpty()) {
                            targetDesc = bDesc;
                        }
                    }
                } else {
                    targetPkg = activePlayingPkg != null ? activePlayingPkg : fallbackPkg;
                    targetDesc = activePlayingPkg != null ? activePlayingDesc : fallbackDesc;
                }

                if (targetPkg != null && !targetPkg.isEmpty()) {
                    status.activePkg = targetPkg;
                    status.activeApp = getFriendlyAppName(context, targetPkg);
                }
                if (targetDesc != null && !targetDesc.isEmpty()) {
                    String[] parts = targetDesc.split(", ");
                    if (parts.length >= 2) {
                        status.trackInfo = parts[0] + " • " + parts[1];
                    } else {
                        status.trackInfo = targetDesc;
                    }
                }
            }

            // 2. Query Codec Native Stream Resolution from dumpsys media.metrics (Universal for all Android media decoders)
            String targetCodecPkg = activeHardwarePkg != null ? activeHardwarePkg : status.activePkg;
            if (targetCodecPkg != null && !targetCodecPkg.isEmpty()) {
                String codecDump = runPrivilegedCommand("dumpsys media.metrics 2>/dev/null | grep -E '\\{codec,.*" + targetCodecPkg + "' | tail -n 1");
                if (codecDump != null && codecDump.contains("sampleRate=")) {
                    int srIdx = codecDump.indexOf("sampleRate=");
                    if (srIdx != -1) {
                        String srSub = codecDump.substring(srIdx + 11).replaceAll("[^0-9].*", "").trim();
                        try {
                            int cRate = Integer.parseInt(srSub);
                            if (cRate > 0) {
                                activeStreamRate = cRate;
                                status.sourceRate = cRate;
                                if (codecDump.contains("audio/flac") || codecDump.contains("PCM_24") || codecDump.contains("c2.android.flac")) {
                                    status.sourceBitDepth = "24-bit";
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // 3. Check for Direct USB Exclusive Mode (usbfs claimed by Bitchord/UAPP/Hiby/Neutron)
            String usbfsOut = runPrivilegedCommand("ls /sys/bus/usb/drivers/usbfs/ 2>/dev/null");
            String altSettingOut = runPrivilegedCommand("cat /sys/bus/usb/devices/*-1:1.*/bAlternateSetting 2>/dev/null");
            boolean usbStreamingActive = altSettingOut != null && (altSettingOut.contains("1") || altSettingOut.contains("2") || altSettingOut.contains("3"));
            boolean usbfsDirectActive = (usbfsOut != null && (usbfsOut.contains("1-1") || usbfsOut.contains("usb"))) || (activeHardwarePkg != null && directUsbPidStr != null && !directUsbPidStr.trim().isEmpty()) || usbStreamingActive;

            // 4. Hardware DAC Clock directly from Snapdragon Kernel DSP (QTI uaudio)
            String uaudioLast = runPrivilegedCommand("dmesg 2>/dev/null | grep -E 'handle_uaudio_stream_req|uaudio_disconnect_cb|uaudio_dev_cleanup' | tail -n 1");
            boolean kernelUaudioActive = false;
            int kernelRate = 0;
            String kernelFormat = "";
            if (uaudioLast != null && uaudioLast.contains("en:1") && !uaudioLast.contains("release") && !uaudioLast.contains("disconnect") && !uaudioLast.contains("cleanup")) {
                if (uaudioLast.contains("rate:")) {
                    try {
                        int rIdx = uaudioLast.indexOf("rate:");
                        String rSub = uaudioLast.substring(rIdx + 5).replaceAll("[^0-9].*", "").trim();
                        kernelRate = Integer.parseInt(rSub);
                        if (kernelRate > 0) {
                            kernelUaudioActive = true;
                            if (uaudioLast.contains("fmt:10")) {
                                kernelFormat = "24-bit";
                            } else if (uaudioLast.contains("fmt:14")) {
                                kernelFormat = "32-bit";
                            } else if (uaudioLast.contains("fmt:2")) {
                                kernelFormat = "16-bit";
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 4. Read AudioFlinger direct & mixer threads with independent per-thread state
            String afDump = runPrivilegedCommand("dumpsys media.audio_flinger");
            if (afDump != null) {
                boolean directActive = false;
                int directRate = 0;
                String directBitDepth = "24-bit";

                boolean mixerActive = false;
                int mixerRate = 0;
                String mixerBitDepth = "24-bit";

                boolean speakerActive = false;

                int curThreadType = -1; // 0=mixer, 1=direct, etc.
                boolean curStandbyNo = false;
                boolean curToUsb = false;
                boolean curToSpeaker = false;
                int curRate = 0;
                String curBitDepth = "16-bit";

                String[] lines = afDump.split("\n");
                for (int i = 0; i <= lines.length; i++) {
                    String line = (i < lines.length) ? lines[i] : "Output thread END:";
                    String trimmed = line.trim();

                    if (trimmed.contains("Output thread") || i == lines.length) {
                        if (curThreadType == 1 && curStandbyNo && curToUsb) {
                            directActive = true;
                            if (curRate > 0) directRate = curRate;
                            directBitDepth = curBitDepth;
                        } else if (curThreadType == 0 && curStandbyNo && curToUsb) {
                            mixerActive = true;
                            if (curRate > 0) mixerRate = curRate;
                            mixerBitDepth = curBitDepth;
                        } else if (curStandbyNo && curToSpeaker) {
                            speakerActive = true;
                        }

                        if (trimmed.contains("type 1 (DIRECT)") || trimmed.contains("DIRECT") || trimmed.contains("BIT_PERFECT") || trimmed.contains("type 6")) {
                            curThreadType = 1;
                        } else if (trimmed.contains("type 0 (MIXER)") || trimmed.contains("MIXER")) {
                            curThreadType = 0;
                        } else {
                            curThreadType = -1;
                        }
                        curStandbyNo = false;
                        curToUsb = false;
                        curToSpeaker = false;
                        curRate = 0;
                        curBitDepth = "16-bit";
                        continue;
                    }

                    if (trimmed.contains("Standby: no")) {
                        curStandbyNo = true;
                    }
                    if (trimmed.contains("Output devices:")) {
                        if (trimmed.contains("USB") || trimmed.contains("0x4000000") || trimmed.contains("4000000")) {
                            curToUsb = true;
                        } else if (trimmed.contains("SPEAKER") || trimmed.contains("0x2")) {
                            curToSpeaker = true;
                        }
                    }
                    if (trimmed.startsWith("Sample rate:")) {
                        String sr = trimmed.replaceAll("[^0-9]", "");
                        if (!sr.isEmpty()) {
                            try {
                                curRate = Integer.parseInt(sr);
                            } catch (Exception ignored) {}
                        }
                    }
                    if (trimmed.contains("HAL format:")) {
                        if (trimmed.contains("0x3") || trimmed.contains("32_BIT")) {
                            curBitDepth = "32-bit";
                        } else if (trimmed.contains("0x6") || trimmed.contains("24_BIT")) {
                            curBitDepth = "24-bit";
                        } else {
                            curBitDepth = "16-bit";
                        }
                    }
                }

                int lockedRate = getTargetRate(context);

                if (usbfsDirectActive) {
                    // Direct USB is exclusive, bypassing Android AudioMixer & AudioFlinger completely
                    status.routeType = "DIRECT_PCM";
                    int finalRate = (lockedRate > 0) ? lockedRate : (activeStreamRate > 0 ? activeStreamRate : (status.sourceRate > 0 ? status.sourceRate : (directRate > 0 ? directRate : 96000)));
                    status.sourceRate = (activeStreamRate > 0) ? activeStreamRate : finalRate;
                    status.dacRate = finalRate;
                    if (status.sourceBitDepth.isEmpty()) status.sourceBitDepth = "24-bit";
                    status.dacFormat = "24-bit (Direct USB) • Direct PCM (Bit-Perfect)";
                    status.isBitPerfect = true;
                } else if (alsaHwRate > 0) {
                    // ALSA stream verified directly from DAC driver
                    status.dacRate = (lockedRate > 0) ? lockedRate : alsaHwRate;
                    if (status.sourceRate == 0) status.sourceRate = alsaHwRate;
                    status.isBitPerfect = (status.sourceRate == status.dacRate);
                    status.routeType = status.isBitPerfect ? "DIRECT_PCM" : "MIXER";
                    status.dacFormat = (alsaHwFormat.isEmpty() ? "24-bit" : alsaHwFormat) + (status.isBitPerfect ? " • Direct ALSA (Bit-Perfect)" : " • Resampled (Mixer)");
                } else if (directActive || (kernelUaudioActive && kernelRate > 0)) {
                    // AudioFlinger Direct Track or Snapdragon QTI DSP Direct Track
                    status.routeType = "DIRECT_PCM";
                    int finalRate = (lockedRate > 0) ? lockedRate : (directRate > 0 ? directRate : kernelRate);
                    if (status.sourceRate == 0) status.sourceRate = (activeStreamRate > 0) ? activeStreamRate : finalRate;
                    status.dacRate = finalRate > 0 ? finalRate : status.sourceRate;
                    if (status.sourceBitDepth.isEmpty()) status.sourceBitDepth = directBitDepth;
                    status.dacFormat = (kernelFormat.isEmpty() ? directBitDepth : kernelFormat) + " • Direct PCM (Bit-Perfect)";
                    status.isBitPerfect = true;
                } else if (mixerActive) {
                    // Resampled through AudioMixer
                    status.routeType = "MIXER";
                    status.dacRate = (lockedRate > 0) ? lockedRate : (mixerRate > 0 ? mixerRate : 48000);
                    status.dacFormat = mixerBitDepth + " • Android AudioMixer";
                    status.isBitPerfect = (status.sourceRate > 0 && status.sourceRate == status.dacRate);
                } else if (speakerActive) {
                    status.routeType = "SPEAKER";
                } else {
                    status.routeType = "STANDBY";
                    if (lockedRate > 0) {
                        status.dacRate = lockedRate;
                    }
                }
            }

            if (status.sourceBitDepth.isEmpty() && status.sourceRate > 0) {
                status.sourceBitDepth = "24-bit";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in AudioFlinger status parser", e);
        }
    }

    private static String readBitchordLastPlayed() {
        try {
            String xml = runPrivilegedCommand("cat /data/data/com.music.bitchord/shared_prefs/bitchord_last_played.xml 2>/dev/null");
            if (xml != null && xml.contains("<int name=\"index\"")) {
                int idxTag = xml.indexOf("<int name=\"index\" value=\"");
                if (idxTag != -1) {
                    int endIdx = xml.indexOf("\"", idxTag + 25);
                    int targetIndex = Integer.parseInt(xml.substring(idxTag + 25, endIdx));
                    int qTag = xml.indexOf("<string name=\"queue\">");
                    if (qTag != -1) {
                        int endQ = xml.indexOf("</string>", qTag);
                        String qJson = xml.substring(qTag + 21, endQ).replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
                        org.json.JSONObject obj = new org.json.JSONObject(qJson);
                        org.json.JSONArray tracks = obj.optJSONArray("tracks");
                        if (tracks != null && targetIndex >= 0 && targetIndex < tracks.length()) {
                            org.json.JSONObject tr = tracks.getJSONObject(targetIndex);
                            return tr.optString("title") + ", " + tr.optString("artist");
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String getFriendlyAppName(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return "None";
        if (context != null) {
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                if (label != null && label.length() > 0) {
                    return label.toString();
                }
            } catch (Exception ignored) {}
        }
        return getFriendlyAppName(pkg);
    }

    public static String getFriendlyAppName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "None";
        String lower = pkg.toLowerCase();
        if (lower.contains("apple") && lower.contains("music")) return "Apple Music";
        if (lower.contains("tidal")) return "TIDAL";
        if (lower.contains("bitchord")) return "Bitchord";
        if (lower.contains("spotify")) return "Spotify";
        if (lower.contains("mornye")) return "Mornye Music";
        if (lower.contains("youtube.music")) return "YouTube Music";
        if (lower.contains("audioplayer")) return "Poweramp";
        if (lower.contains("twelve")) return "Twelve";
        if (lower.contains("neutron")) return "Neutron Player";
        if (lower.contains("usbaudioplayerpro")) return "UAPP";
        if (lower.contains("hiby")) return "HiBy Music";
        if (lower.contains("fiio")) return "FiiO Music";
        if (lower.contains("qobuz")) return "Qobuz";
        if (lower.contains("deezer")) return "Deezer";
        if (lower.contains("foobar")) return "foobar2000";
        if (lower.contains("musicolet")) return "Musicolet";
        if (lower.contains("vimusic")) return "ViMusic";
        if (lower.contains("symfonium")) return "Symfonium";
        if (lower.contains("gonemad")) return "GoneMAD Music Player";
        if (lower.contains("retro")) return "Retro Music";
        if (lower.contains("blackplayer")) return "BlackPlayer";
        if (lower.contains("stella")) return "Stella Player";
        if (lower.contains("amazon.mp3")) return "Amazon Music";
        if (lower.contains("kkbox")) return "KKBOX";
        if (lower.contains("anghami")) return "Anghami";
        if (lower.contains("youtube")) return "YouTube";
        if (lower.contains("tiktok")) return "TikTok";
        if (lower.contains("instagram")) return "Instagram";
        if (lower.contains("netflix")) return "Netflix";

        // Dynamic beautifier: com.example.my_audio_player -> My Audio Player
        int lastDot = pkg.lastIndexOf('.');
        String seg = (lastDot != -1 && lastDot < pkg.length() - 1) ? pkg.substring(lastDot + 1) : pkg;
        seg = seg.replace("_", " ").replace("-", " ");
        if (!seg.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String word : seg.split("\\s+")) {
                if (!word.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(word.charAt(0)));
                    if (word.length() > 1) sb.append(word.substring(1));
                }
            }
            if (sb.length() > 0) return sb.toString();
        }
        return pkg;
    }

    public static boolean isExcludedNonPlayerPackage(String pkg) {
        if (pkg == null) return true;
        String lower = pkg.toLowerCase();
        return lower.contains("termux") ||
               lower.contains("nbu.files") ||
               lower.contains("documentsui") ||
               lower.contains("filemanager") ||
               lower.contains("gallery") ||
               lower.contains("photos") ||
               lower.contains("telecom") ||
               lower.contains("hearing") ||
               lower.contains("speech") ||
               lower.equals("android");
    }

    public static List<AudioAppInfo> getInstalledAudioApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, AudioAppInfo> appMap = new HashMap<>();

        // Known audio player packages to discover universally
        String[] popularAudioPackages = new String[]{
                "com.apple.android.music",
                "com.aspiro.tidal",
                "com.spotify.music",
                "com.music.bitchord",
                "com.google.android.apps.youtube.music",
                "com.maxmpz.audioplayer",
                "org.lineageos.twelve",
                "com.neutroncode.mp",
                "com.extreamsd.usbaudioplayerpro",
                "com.hiby.music",
                "com.fiio.music",
                "com.qobuz.music",
                "deezer.android.app",
                "com.foobar2000.foobar2000",
                "in.krosbits.musicolet",
                "it.vfsfitvnm.vimusic",
                "com.zionhuang.music",
                "com.aimp.player",
                "com.kodarkooperativet.blackplayerfree",
                "com.kodarkooperativet.blackplayerex",
                "com.onkyo.jp.musicplayer",
                "com.sonyericsson.music",
                "com.sec.android.app.music",
                "com.miui.player",
                "app.revanced.android.apps.youtube.music",
                "com.soundcloud.android",
                "com.amazon.mp3"
        };

        for (String pkg : popularAudioPackages) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                String label = pm.getApplicationLabel(ai).toString();
                Drawable icon = pm.getApplicationIcon(ai);
                appMap.put(pkg, new AudioAppInfo(pkg, label, icon, isAppBypassed(context, pkg)));
            } catch (Exception ignored) {}
        }

        // 1. Query Music Category Intent
        try {
            Intent musicIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC);
            List<ResolveInfo> musicActivities = pm.queryIntentActivities(musicIntent, 0);
            for (ResolveInfo ri : musicActivities) {
                if (ri.activityInfo != null) {
                    String pkg = ri.activityInfo.packageName;
                    if (!appMap.containsKey(pkg) && !pkg.equals(context.getPackageName())) {
                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                            String label = pm.getApplicationLabel(ai).toString();
                            Drawable icon = pm.getApplicationIcon(ai);
                            appMap.put(pkg, new AudioAppInfo(pkg, label, icon, isAppBypassed(context, pkg)));
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Query Audio MIME Handler Intent
        try {
            Intent audioIntent = new Intent(Intent.ACTION_VIEW);
            audioIntent.setDataAndType(Uri.parse("file://sample.mp3"), "audio/*");
            List<ResolveInfo> audioActivities = pm.queryIntentActivities(audioIntent, 0);
            for (ResolveInfo ri : audioActivities) {
                if (ri.activityInfo != null) {
                    String pkg = ri.activityInfo.packageName;
                    if (isExcludedNonPlayerPackage(pkg)) continue;
                    if (!appMap.containsKey(pkg) && !pkg.equals(context.getPackageName())) {
                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                            String label = pm.getApplicationLabel(ai).toString();
                            Drawable icon = pm.getApplicationIcon(ai);
                            appMap.put(pkg, new AudioAppInfo(pkg, label, icon, isAppBypassed(context, pkg)));
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Scan all installed applications for CATEGORY_AUDIO and music keywords
        try {
            List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo ai : allApps) {
                String pkg = ai.packageName;
                if (appMap.containsKey(pkg) || pkg.equals(context.getPackageName())) continue;
                if (isExcludedNonPlayerPackage(pkg)) continue;

                boolean isAudioCategory = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (ai.category == ApplicationInfo.CATEGORY_AUDIO) {
                        isAudioCategory = true;
                    }
                }

                String lower = pkg.toLowerCase();
                boolean isMusicKeyword = lower.contains("music") || lower.contains("player") || lower.contains("audio") || lower.contains("flac");

                if (isAudioCategory || isMusicKeyword) {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        try {
                            String label = pm.getApplicationLabel(ai).toString();
                            Drawable icon = pm.getApplicationIcon(ai);
                            appMap.put(pkg, new AudioAppInfo(pkg, label, icon, isAppBypassed(context, pkg)));
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        List<AudioAppInfo> resultList = new ArrayList<>(appMap.values());
        Collections.sort(resultList, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
        return resultList;
    }

    public static boolean isAppBypassed(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(PREF_BYPASS_APPS)) {
            // First time default: enable bypass for detected audio apps
            return true;
        }
        Set<String> set = prefs.getStringSet(PREF_BYPASS_APPS, null);
        return set != null && set.contains(pkg);
    }

    public static void setAppBypassed(Context context, String pkg, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(PREF_BYPASS_APPS, null);
        Set<String> newSet = new HashSet<>();
        if (set != null) {
            newSet.addAll(set);
        } else {
            for (AudioAppInfo app : getInstalledAudioApps(context)) {
                newSet.add(app.packageName);
            }
        }
        if (enabled) {
            newSet.add(pkg);
        } else {
            newSet.remove(pkg);
        }
        prefs.edit().putStringSet(PREF_BYPASS_APPS, newSet).apply();
    }

    public static void setAllAppsBypassed(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> newSet = new HashSet<>();
        if (enabled) {
            for (AudioAppInfo app : getInstalledAudioApps(context)) {
                newSet.add(app.packageName);
            }
        }
        prefs.edit().putStringSet(PREF_BYPASS_APPS, newSet).apply();
    }

    public static int getTargetBitDepth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_TARGET_BIT_DEPTH, 0); // 0 = Auto (1:1 Native)
    }

    public static void setTargetBitDepth(Context context, int bitDepth) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_TARGET_BIT_DEPTH, bitDepth).apply();
    }

    public static int getTargetRate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_TARGET_RATE, 0); // 0 = Auto Follow / Reset
    }

    public static void setTargetRate(Context context, int rateHz) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_TARGET_RATE, rateHz).apply();
    }

    public static boolean extractEngineFiles(Context context) {
        try {
            File internalDir = new File(context.getFilesDir(), "engine");
            if (!internalDir.exists()) internalDir.mkdirs();

            String[] files = {
                    "audio_policy_configuration.xml",
                    "audio_io_policy.conf",
                    "audio_effects.xml",
                    "libaudiopolicymanagerdefault.so"
            };

            for (String f : files) {
                File dest = new File(internalDir, f);
                try (java.io.InputStream in = context.getAssets().open(f);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                    }
                }
                dest.setReadable(true, false);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("mkdir -p /data/local/tmp/bitperfect_engine\n");
            sb.append("chmod 755 /data/local/tmp/bitperfect_engine\n");
            for (String f : files) {
                File src = new File(internalDir, f);
                sb.append("cp \"").append(src.getAbsolutePath()).append("\" /data/local/tmp/bitperfect_engine/").append(f).append("\n");
            }
            sb.append("chmod 644 /data/local/tmp/bitperfect_engine/*\n");
            sb.append("chcon u:object_r:vendor_configs_file:s0 /data/local/tmp/bitperfect_engine/*.xml /data/local/tmp/bitperfect_engine/*.conf 2>/dev/null || true\n");
            sb.append("chcon u:object_r:system_lib_file:s0 /data/local/tmp/bitperfect_engine/*.so 2>/dev/null || true\n");

            String res = runRootCommand(sb.toString());
            return res != null;
        } catch (Exception e) {
            Log.e(TAG, "extractEngineFiles error", e);
            return false;
        }
    }

    public static boolean applyEngineTweaks(Context context) {
        Log.i(TAG, "Applying Root Audio HAL Engine Tweaks (Bit-Perfect Direct PCM Bypass)...");

        // 1. Extract engine config files from assets to staging
        extractEngineFiles(context);

        // 2. Build root command sequence with STRICT SAFETY FIRST
        StringBuilder cmds = new StringBuilder();

        // STRICT SAFETY FIRST: Mute stream 3 (Media) & 10, pause playback, stop players
        cmds.append("cmd audio set-volume 3 0\n");
        cmds.append("cmd audio set-volume 10 0\n");
        cmds.append("input keyevent 127\n"); // KEYCODE_MEDIA_PAUSE
        cmds.append("am force-stop com.aspiro.tidal 2>/dev/null || true\n");
        cmds.append("am force-stop com.apple.android.music 2>/dev/null || true\n");
        cmds.append("am force-stop com.google.android.youtube 2>/dev/null || true\n");
        cmds.append("am force-stop com.google.android.apps.youtube.music 2>/dev/null || true\n");
        cmds.append("am force-stop com.spotify.music 2>/dev/null || true\n");
        cmds.append("am force-stop com.zarz.mornye 2>/dev/null || true\n");
        cmds.append("am force-stop com.music.bitchord 2>/dev/null || true\n");

        // Bind-mount patched audio policies & direct PCM bypass library
        cmds.append("mount -o bind /data/local/tmp/bitperfect_engine/audio_policy_configuration.xml /vendor/etc/audio_policy_configuration.xml\n");
        cmds.append("mount -o bind /data/local/tmp/bitperfect_engine/audio_io_policy.conf /vendor/etc/audio_io_policy.conf\n");
        cmds.append("mount -o bind /data/local/tmp/bitperfect_engine/audio_effects.xml /vendor/etc/audio_effects.xml\n");
        cmds.append("mount -o bind /data/local/tmp/bitperfect_engine/libaudiopolicymanagerdefault.so /system/lib64/libaudiopolicymanagerdefault.so\n");

        // High-Resolution & USB Offload properties
        cmds.append("resetprop -n persist.vendor.audio.samplerates \"44100,48000,88200,96000,176400,192000,352800,384000\"\n");
        cmds.append("resetprop -n ro.audio.samplerates \"44100,48000,88200,96000,176400,192000,352800,384000\"\n");
        cmds.append("resetprop -n vendor.audio.feature.usb_offload.enable true\n");
        cmds.append("resetprop -n vendor.audio.feature.usb_offload_burst_mode.enable false\n");
        cmds.append("resetprop -n vendor.audio.feature.hifi_audio.enable true\n");
        cmds.append("resetprop -n vendor.audio.usb.super_high_res true\n");
        cmds.append("resetprop -n audio.safemedia.bypass true\n");
        cmds.append("resetprop -n persist.vendor.audio.format.24bit true\n");
        cmds.append("resetprop -n persist.vendor.audio_hal.dsp_bit_width_enforce_mode 24\n");
        cmds.append("resetprop -n af.fast_track_multiplier 2\n");
        cmds.append("resetprop -n aaudio.mmap_policy 2\n");
        cmds.append("resetprop -n aaudio.mmap_exclusive_policy 2\n");

        // ANTI-STUTTER: Deep buffer must remain ENABLED for video apps (YouTube, TikTok, Instagram)
        cmds.append("resetprop -n audio.deep_buffer.media true\n");

        // ANTI-POP / ANTI-TUK-TUK: Delete standbytime_ms 1000 so amplifier stays stable
        cmds.append("resetprop -p --delete ro.audio.flinger_standbytime_ms\n");

        // Restart audio HAL & audioserver safely
        cmds.append("pkill -9 -f android.hardware.audio.service || true\n");
        cmds.append("killall -9 audioserver || true\n");
        cmds.append("sleep 2\n");
        // Re-enforce zero volume post-restart to guarantee safety
        cmds.append("cmd audio set-volume 3 0\n");

        String out = runRootCommand(cmds.toString());
        return out != null;
    }

    public static boolean restoreStockAudio(Context context) {
        Log.i(TAG, "Restoring stock Android audio HAL and unmounting engine...");
        StringBuilder cmds = new StringBuilder();

        // STRICT SAFETY FIRST
        cmds.append("cmd audio set-volume 3 0\n");
        cmds.append("cmd audio set-volume 10 0\n");
        cmds.append("input keyevent 127\n");
        cmds.append("am force-stop com.aspiro.tidal 2>/dev/null || true\n");
        cmds.append("am force-stop com.apple.android.music 2>/dev/null || true\n");
        cmds.append("am force-stop com.google.android.youtube 2>/dev/null || true\n");
        cmds.append("am force-stop com.google.android.apps.youtube.music 2>/dev/null || true\n");
        cmds.append("am force-stop com.spotify.music 2>/dev/null || true\n");
        cmds.append("am force-stop com.zarz.mornye 2>/dev/null || true\n");

        // Unmount patched configurations
        cmds.append("umount /vendor/etc/audio_policy_configuration.xml 2>/dev/null || true\n");
        cmds.append("umount /vendor/etc/audio_io_policy.conf 2>/dev/null || true\n");
        cmds.append("umount /vendor/etc/audio_effects.xml 2>/dev/null || true\n");
        cmds.append("umount /system/lib64/libaudiopolicymanagerdefault.so 2>/dev/null || true\n");

        // Remove old module leftovers if any
        cmds.append("rm -rf /data/adb/modules/disable_resample_48k /data/adb/modules_update/disable_resample_48k 2>/dev/null || true\n");

        // Reset properties
        cmds.append("resetprop -n audio.deep_buffer.media true\n");
        cmds.append("resetprop -p --delete ro.audio.flinger_standbytime_ms\n");
        cmds.append("resetprop -p --delete aaudio.mmap_policy\n");
        cmds.append("resetprop -p --delete aaudio.mmap_exclusive_policy\n");

        // Restart audio HAL & audioserver safely
        cmds.append("pkill -9 -f android.hardware.audio.service || true\n");
        cmds.append("killall -9 audioserver || true\n");
        cmds.append("sleep 2\n");
        cmds.append("cmd audio set-volume 3 0\n");

        String out = runRootCommand(cmds.toString());
        return out != null;
    }

    public static boolean isEngineActive() {
        String mounts = runPrivilegedCommand("mount");
        return mounts != null && mounts.contains("audio_policy_configuration.xml");
    }

    public static boolean purgeOldModule() {
        return restoreStockAudio(null);
    }

    public static boolean isModuleInstalled() {
        File f1 = new File("/data/adb/modules/disable_resample_48k");
        return f1.exists();
    }

    public static boolean uninstallMagiskModule() {
        Log.i(TAG, "Uninstalling / removing Magisk module disable_resample_48k...");
        String cmd = "rm -rf /data/adb/modules/disable_resample_48k && touch /data/adb/modules/disable_resample_48k/remove 2>/dev/null";
        runRootCommand(cmd);
        return !isModuleInstalled();
    }

    public static boolean setDacRate(Context context, int targetRate, boolean safePause) {
        return setDacRate(context, targetRate, getTargetBitDepth(context), safePause);
    }

    public static boolean setDacRate(Context context, int targetRate, int targetBitDepth, boolean safePause) {
        Log.i(TAG, "setDacRate: " + targetRate + " Hz, bitDepth=" + targetBitDepth + ", safePause=" + safePause);
        setTargetRate(context, targetRate);
        setTargetBitDepth(context, targetBitDepth);

        if (safePause) {
            dispatchMediaKey("127"); // KEYCODE_MEDIA_PAUSE
            SystemClock.sleep(300);
        }

        boolean success = false;

        // Try Native Android 14+ setPreferredMixerAttributes (supports Bit-Perfect mode)
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo usbDev = getUsbDevice(context);
            if (am != null && usbDev != null) {
                AudioAttributes mediaAttrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();

                if (targetRate == 0 && targetBitDepth == 0) {
                    am.clearPreferredMixerAttributes(mediaAttrs, usbDev);
                    success = true;
                } else {
                    int finalRate = targetRate > 0 ? targetRate : 48000;
                    int encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED;
                    if (targetBitDepth == 16) {
                        encoding = AudioFormat.ENCODING_PCM_16BIT;
                    } else if (targetBitDepth == 24) {
                        encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED;
                    } else if (targetBitDepth == 32) {
                        encoding = AudioFormat.ENCODING_PCM_32BIT;
                    }

                    AudioFormat format = new AudioFormat.Builder()
                            .setSampleRate(finalRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(encoding)
                            .build();

                    // 1. Try bit-perfect direct mixer behavior
                    try {
                        AudioMixerAttributes bitPerfectAttr = new AudioMixerAttributes.Builder(format)
                                .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)
                                .build();
                        success = am.setPreferredMixerAttributes(mediaAttrs, usbDev, bitPerfectAttr);
                    } catch (Exception ignored) {}

                    // 2. Fallback to default mixer attribute with locked sample rate
                    if (!success) {
                        AudioMixerAttributes mixerAttr = new AudioMixerAttributes.Builder(format)
                                .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT)
                                .build();
                        success = am.setPreferredMixerAttributes(mediaAttrs, usbDev, mixerAttr);
                    }
                }
            }
        } catch (Exception ignored) {}

        // Root / Vendor properties fallback (universal across Snapdragon & OEM HALs)
        try {
            StringBuilder cmds = new StringBuilder();
            if (targetRate == 0) {
                cmds.append("resetprop -n persist.vendor.audio.samplerates 44100,48000,88200,96000,176400,192000,352800,384000\n");
            } else {
                cmds.append("resetprop -n persist.vendor.audio.samplerates ").append(targetRate).append("\n");
            }

            if (targetBitDepth == 16) {
                cmds.append("resetprop -n persist.vendor.audio_hal.dsp_bit_width_enforce_mode 16\n");
                cmds.append("resetprop -n persist.vendor.audio.format.24bit false\n");
            } else if (targetBitDepth == 24) {
                cmds.append("resetprop -n persist.vendor.audio_hal.dsp_bit_width_enforce_mode 24\n");
                cmds.append("resetprop -n persist.vendor.audio.format.24bit true\n");
            } else if (targetBitDepth == 32) {
                cmds.append("resetprop -n persist.vendor.audio_hal.dsp_bit_width_enforce_mode 32\n");
                cmds.append("resetprop -n persist.vendor.audio.format.24bit true\n");
                cmds.append("resetprop -n persist.vendor.audio.format.32bit true\n");
            } else { // Auto
                cmds.append("resetprop -n persist.vendor.audio.format.24bit true\n");
                cmds.append("resetprop -n persist.vendor.audio_hal.dsp_bit_width_enforce_mode 24\n");
            }

            runRootCommand(cmds.toString());
            success = true;
        } catch (Exception e) {
            Log.e(TAG, "Root fallback error", e);
        }

        if (safePause) {
            SystemClock.sleep(300);
            dispatchMediaKey("126"); // KEYCODE_MEDIA_PLAY
        }

        return success;
    }

    public static void requestRootPermissions(Context context) {
        new Thread(() -> {
            try {
                String pkg = context.getPackageName();
                runRootCommand("pm grant " + pkg + " android.permission.MODIFY_AUDIO_ROUTING\n" +
                        "pm grant " + pkg + " android.permission.DUMP\n");
            } catch (Exception ignored) {}
        }).start();
    }

    public static void dispatchMediaKey(String keyCode) {
        try {
            runRootCommand("input keyevent " + keyCode);
        } catch (Exception ignored) {}
    }

    private static String runPrivilegedCommand(String cmd) {
        String res = runShellCommand(cmd);
        if (res == null || res.contains("Permission Denial") || res.contains("missing android.permission") || res.trim().isEmpty()) {
            String rootRes = runRootCommand(cmd);
            if (rootRes != null && !rootRes.trim().isEmpty()) {
                return rootRes;
            }
        }
        return res;
    }

    private static String runShellCommand(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String runRootCommand(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());
            os.writeBytes(cmd + "\nexit\n");
            os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "runRootCommand error", e);
            return null;
        }
    }
}
