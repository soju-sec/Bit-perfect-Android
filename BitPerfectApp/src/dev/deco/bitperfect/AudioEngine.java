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

        // Sysfs fallback
        String sysfsProduct = runPrivilegedCommand("cat /sys/bus/usb/devices/*-1/product 2>/dev/null");
        if (sysfsProduct != null && !sysfsProduct.trim().isEmpty()) {
            String prod = sysfsProduct.trim().split("\n")[0].trim();
            if (!prod.isEmpty()) return prod;
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
            // 1. Read dumpsys audio for active AudioPlaybackConfiguration and sampleRate
            String audioDump = runPrivilegedCommand("dumpsys audio");
            if (audioDump != null) {
                for (String line : audioDump.split("\n")) {
                    if (line.contains("AudioPlaybackConfiguration") && line.contains("state:started") && line.contains("USAGE_MEDIA")) {
                        int idx = line.indexOf("sampleRate=");
                        if (idx != -1) {
                            String srPart = line.substring(idx + 11);
                            String srStr = srPart.replaceAll("[^0-9].*", "").trim();
                            if (!srStr.isEmpty()) {
                                try {
                                    status.sourceRate = Integer.parseInt(srStr);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }

            // 2. Read active media session package & song title via privileged command
            String sessionDump = runPrivilegedCommand("dumpsys media_session");
            if (sessionDump != null) {
                String[] lines = sessionDump.split("\n");
                String currentPkg = null;
                boolean isPlaying = false;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.contains("package=") && !trimmed.contains("telecom") && !trimmed.contains("system")) {
                        currentPkg = trimmed.replaceAll(".*package=", "").trim();
                    }
                    if (trimmed.contains("state=PlaybackState") && (trimmed.contains("state=PLAYING") || trimmed.contains("state=3"))) {
                        isPlaying = true;
                        if (currentPkg != null && !currentPkg.isEmpty()) {
                            status.activePkg = currentPkg;
                            status.activeApp = getFriendlyAppName(currentPkg);
                        }
                    }
                    if (trimmed.contains("description=")) {
                        int dIdx = trimmed.indexOf("description=");
                        String desc = trimmed.substring(dIdx + 12).trim();
                        if (!desc.isEmpty() && !"null".equals(desc) && isPlaying) {
                            String[] parts = desc.split(", ");
                            if (parts.length >= 2) {
                                status.trackInfo = parts[0] + " • " + parts[1];
                            } else {
                                status.trackInfo = desc;
                            }
                        }
                    }
                }
                if (status.activePkg.isEmpty() && currentPkg != null) {
                    status.activePkg = currentPkg;
                    status.activeApp = getFriendlyAppName(currentPkg);
                }
            }

            // 3. Hardware DAC Clock directly from Snapdragon Kernel DSP (QTI uaudio)
            String uaudioDmesg = runPrivilegedCommand("dmesg 2>/dev/null | grep -E 'handle_uaudio_stream_req.*en:1' | tail -n 1");
            boolean kernelUaudioActive = false;
            int kernelRate = 0;
            String kernelFormat = "";
            if (uaudioDmesg != null && uaudioDmesg.contains("rate:")) {
                try {
                    int rIdx = uaudioDmesg.indexOf("rate:");
                    String rSub = uaudioDmesg.substring(rIdx + 5).replaceAll("[^0-9].*", "").trim();
                    kernelRate = Integer.parseInt(rSub);
                    if (kernelRate > 0) {
                        kernelUaudioActive = true;
                        if (uaudioDmesg.contains("fmt:10")) {
                            kernelFormat = "24-bit";
                        } else if (uaudioDmesg.contains("fmt:14")) {
                            kernelFormat = "32-bit";
                        } else if (uaudioDmesg.contains("fmt:2")) {
                            kernelFormat = "16-bit";
                        }
                    }
                } catch (Exception ignored) {}
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

                // Temporary thread state
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
                        // Process the previous thread if it was finished
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

                        // Reset for new thread
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

                if (directActive || (kernelUaudioActive && kernelRate > 0)) {
                    status.routeType = "DIRECT_PCM";
                    int finalRate = directRate > 0 ? directRate : kernelRate;
                    if (status.sourceRate == 0) status.sourceRate = finalRate;
                    if (status.sourceBitDepth.isEmpty()) status.sourceBitDepth = directBitDepth;
                    status.dacRate = finalRate > 0 ? finalRate : status.sourceRate;
                    status.dacFormat = (kernelFormat.isEmpty() ? directBitDepth : kernelFormat) + " • Direct PCM (Bit-Perfect)";
                    status.isBitPerfect = true;
                } else if (mixerActive) {
                    status.routeType = "MIXER";
                    status.dacRate = mixerRate > 0 ? mixerRate : 48000;
                    status.dacFormat = mixerBitDepth + " • Android AudioMixer";
                } else if (speakerActive) {
                    status.routeType = "SPEAKER";
                } else {
                    status.routeType = "STANDBY";
                }
            }

            if (status.sourceBitDepth.isEmpty() && status.sourceRate > 0) {
                status.sourceBitDepth = "24-bit";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in AudioFlinger status parser", e);
        }
    }

    public static String getFriendlyAppName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "None";
        if (pkg.contains("apple") && pkg.contains("music")) return "Apple Music";
        if (pkg.contains("tidal")) return "TIDAL";
        if (pkg.contains("bitchord")) return "Bitchord";
        if (pkg.contains("spotify")) return "Spotify";
        if (pkg.contains("mornye")) return "Mornye Music";
        if (pkg.contains("youtube.music")) return "YouTube Music";
        if (pkg.contains("audioplayer")) return "Poweramp";
        if (pkg.contains("twelve")) return "Twelve";
        if (pkg.contains("neutron")) return "Neutron Player";
        if (pkg.contains("usbaudioplayerpro")) return "UAPP";
        if (pkg.contains("hiby")) return "HiBy Music";
        if (pkg.contains("fiio")) return "FiiO Music";
        if (pkg.contains("qobuz")) return "Qobuz";
        if (pkg.contains("deezer")) return "Deezer";
        if (pkg.contains("foobar")) return "foobar2000";
        if (pkg.contains("musicolet")) return "Musicolet";
        if (pkg.contains("vimusic")) return "ViMusic";
        if (pkg.contains("youtube")) return "YouTube";
        if (pkg.contains("tiktok")) return "TikTok";
        if (pkg.contains("instagram")) return "Instagram";
        if (pkg.contains("netflix")) return "Netflix";
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
