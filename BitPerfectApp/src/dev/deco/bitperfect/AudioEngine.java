package dev.deco.bitperfect;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
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

        // 1. Detect USB Hardware DAC Device (via AudioManager & ALSA)
        AudioDeviceInfo usbDev = getUsbDevice(context);
        String alsaDac = readAlsaDacName();
        boolean hasAlsaUsb = readAlsaStreamStatus(status);

        if (usbDev != null) {
            status.isDacConnected = true;
            status.dacName = usbDev.getProductName().toString();
        } else if (hasAlsaUsb && alsaDac != null) {
            status.isDacConnected = true;
            status.dacName = alsaDac;
        } else {
            status.isDacConnected = false;
            status.dacName = "Tidak Ada DAC USB";
        }

        // 2. AudioFlinger & Active Media Session Telemetry
        readAudioFlingerStatus(status);

        // 3. Check Per-App Bypass Status
        status.isAppBypassed = isAppBypassed(context, status.activePkg);

        // 4. Evaluate Route & Bit-Perfect State
        if (!status.isDacConnected) {
            // DAC IS UNPLUGGED!
            status.isBitPerfect = false;
            status.dacRate = 0;
            status.dacFormat = "DAC Terputus / Unplugged";
            status.routeType = "DISCONNECTED";
            status.matchStatus = "DAC USB Terputus • Sambungkan DAC untuk Memulai";
        } else if (status.routeType.equals("DIRECT_PCM")) {
            if (status.dacRate == 0 && status.sourceRate > 0) {
                status.dacRate = status.sourceRate;
            }
            if (status.sourceRate > 0 && status.sourceRate == status.dacRate) {
                status.isBitPerfect = true;
                status.matchStatus = "Penyelarasan Jam: 1:1 Murni (Zero SRC / Tanpa Resample)";
            } else if (status.sourceRate > 0) {
                status.isBitPerfect = false;
                status.matchStatus = "Direct PCM Aktif (Sinkronisasi Clock Berlangsung)";
            }
        } else if (status.routeType.equals("MIXER")) {
            status.isBitPerfect = false;
            status.dacRate = 48000;
            status.matchStatus = "Terkunci di 48 kHz oleh mixer Android (Mixer Active)";
        } else if (status.routeType.equals("SPEAKER")) {
            status.isBitPerfect = false;
            status.dacRate = 0;
            status.matchStatus = "Audio Aktif di Speaker HP (Bukan ke DAC USB)";
        } else {
            status.isBitPerfect = false;
            status.dacRate = 0;
            status.dacFormat = "Standby (Audio Idle)";
            status.matchStatus = "Standby • Menunggu Pemutaran Audio";
        }

        return status;
    }

    private static String readAlsaDacName() {
        // Universal parser: scan /proc/asound/cards for USB-Audio
        File cardsFile = new File("/proc/asound/cards");
        if (cardsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(cardsFile)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("USB-Audio") || line.contains("USB Audio")) {
                        String[] parts = line.split(" - ");
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            return parts[1].trim();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Scan card0 to card7 stream0 line 1
        for (int i = 0; i <= 7; i++) {
            File f = new File("/proc/asound/card" + i + "/stream0");
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                    String line = br.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        return line.replaceAll(" at .*", "").trim();
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static boolean readAlsaStreamStatus(AudioStatus status) {
        boolean found = false;
        for (int i = 0; i <= 7; i++) {
            File f = new File("/proc/asound/card" + i + "/stream0");
            if (f.exists()) {
                found = true;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                    String line;
                    boolean isPlayback = false;
                    boolean isRunning = false;
                    String altset = "";
                    String fmtStr = "";
                    int momentaryRate = 0;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
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
                } catch (Exception ignored) {}
                break;
            }
        }
        return found;
    }

    private static void readAudioFlingerStatus(AudioStatus status) {
        try {
            // Read active media session package
            String sessionDump = runShellCommand("dumpsys media_session");
            if (sessionDump != null) {
                for (String line : sessionDump.split("\n")) {
                    if (line.contains("package=") && !line.contains("telecom") && !line.contains("system")) {
                        String pkg = line.replaceAll(".*package=", "").trim();
                        if (!pkg.isEmpty()) {
                            status.activePkg = pkg;
                            status.activeApp = getFriendlyAppName(pkg);
                            break;
                        }
                    }
                }
            }

            // Read AudioFlinger direct & mixer threads
            String afDump = runShellCommand("dumpsys media.audio_flinger");
            if (afDump != null) {
                boolean inDirect = false;
                boolean inMixer = false;
                boolean directStandbyNo = false;
                boolean directToUsb = false;
                boolean mixerStandbyNo = false;
                boolean mixerToUsb = false;
                int currentDirectRate = 0;
                String currentDirectBitDepth = "16-bit";

                for (String line : afDump.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.contains("Output thread") && trimmed.contains("type 1 (DIRECT)")) {
                        inDirect = true;
                        inMixer = false;
                    } else if (trimmed.contains("Output thread") && trimmed.contains("type 0 (MIXER)")) {
                        inMixer = true;
                        inDirect = false;
                    } else if (trimmed.contains("Output thread")) {
                        inDirect = false;
                        inMixer = false;
                    }

                    if (inDirect) {
                        if (trimmed.contains("Standby: no")) {
                            directStandbyNo = true;
                        }
                        if (trimmed.contains("Output devices:") && (trimmed.contains("USB") || trimmed.contains("0x4000000"))) {
                            directToUsb = true;
                        }
                        if (trimmed.startsWith("Sample rate:")) {
                            String sr = trimmed.replaceAll("[^0-9]", "");
                            if (!sr.isEmpty()) {
                                currentDirectRate = Integer.parseInt(sr);
                            }
                        }
                        if (trimmed.contains("HAL format:")) {
                            if (trimmed.contains("0x6") || trimmed.contains("24_BIT")) {
                                currentDirectBitDepth = "24-bit";
                            } else if (trimmed.contains("0x3") || trimmed.contains("32_BIT")) {
                                currentDirectBitDepth = "32-bit";
                            } else {
                                currentDirectBitDepth = "16-bit";
                            }
                        }
                    } else if (inMixer) {
                        if (trimmed.contains("Standby: no")) {
                            mixerStandbyNo = true;
                        }
                        if (trimmed.contains("Output devices:") && (trimmed.contains("USB") || trimmed.contains("0x4000000"))) {
                            mixerToUsb = true;
                        }
                        if (trimmed.startsWith("Sample rate:") && status.sourceRate == 0) {
                            String sr = trimmed.replaceAll("[^0-9]", "");
                            if (!sr.isEmpty()) {
                                status.sourceRate = Integer.parseInt(sr);
                            }
                        }
                    }
                }

                if (directStandbyNo && directToUsb) {
                    status.routeType = "DIRECT_PCM";
                    status.sourceRate = currentDirectRate;
                    status.sourceBitDepth = currentDirectBitDepth;
                } else if (mixerStandbyNo && mixerToUsb) {
                    status.routeType = "MIXER";
                } else if (directStandbyNo || mixerStandbyNo) {
                    status.routeType = "SPEAKER";
                } else {
                    status.routeType = "STANDBY";
                }
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

    public static boolean applyEngineTweaks(Context context) {
        Log.i(TAG, "Applying Audio HAL Engine Tweaks via Root...");
        StringBuilder cmds = new StringBuilder();
        cmds.append("resetprop -n vendor.audio.feature.usb_offload.enable true\n");
        cmds.append("resetprop -n vendor.audio.usb.super_high_res true\n");
        cmds.append("resetprop -n persist.vendor.audio.samplerates 44100,48000,88200,96000,176400,192000,352800,384000\n");
        cmds.append("resetprop -n persist.vendor.audio.format.24bit true\n");
        cmds.append("resetprop -n persist.vendor.audio_hal.dsp_bit_width_enforce_mode 24\n");
        cmds.append("resetprop -n af.fast_track_multiplier 2\n");
        cmds.append("resetprop -n vendor.audio.feature.usb_offload_burst_mode.enable false\n");
        cmds.append("resetprop -n ro.audio.flinger_standbytime_ms 1000\n");
        
        String out = runRootCommand(cmds.toString());
        return out != null;
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
            SystemClock.sleep(3500);
        }

        boolean success = false;

        // Try Native Android 14+ setPreferredMixerAttributes
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

                    AudioMixerAttributes mixerAttr = new AudioMixerAttributes.Builder(format)
                            .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT)
                            .build();

                    success = am.setPreferredMixerAttributes(mediaAttrs, usbDev, mixerAttr);
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
            SystemClock.sleep(500);
            dispatchMediaKey("126"); // KEYCODE_MEDIA_PLAY
        }

        return success;
    }

    public static void dispatchMediaKey(String keyCode) {
        try {
            runRootCommand("input keyevent " + keyCode);
        } catch (Exception ignored) {}
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
