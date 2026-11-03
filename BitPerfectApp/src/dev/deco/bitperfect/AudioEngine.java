package dev.deco.bitperfect;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioMixerAttributes;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class AudioEngine {
    private static final String TAG = "BitPerfect_AudioEngine";
    private static final String PREF_NAME = "bitperfect_prefs";
    private static final String PREF_BYPASS_APPS = "bypassed_packages";

    public static final String PKG_APPLE_MUSIC = "com.apple.android.music";
    public static final String PKG_TIDAL = "com.aspiro.tidal";
    public static final String PKG_BITCHORD = "com.music.bitchord";
    public static final String PKG_SPOTIFY = "com.spotify.music";
    public static final String PKG_YT_MUSIC = "com.google.android.apps.youtube.music";

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
        for (int i = 0; i <= 3; i++) {
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
        for (int i = 0; i <= 3; i++) {
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
        if (pkg.contains("youtube")) return "YouTube";
        if (pkg.contains("tiktok")) return "TikTok";
        if (pkg.contains("instagram")) return "Instagram";
        if (pkg.contains("netflix")) return "Netflix";
        return pkg;
    }

    public static boolean isAppBypassed(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(PREF_BYPASS_APPS, null);
        if (set == null) {
            // Default bypassed apps
            return pkg.equals(PKG_APPLE_MUSIC) || pkg.equals(PKG_TIDAL) || pkg.equals(PKG_BITCHORD);
        }
        return set.contains(pkg);
    }

    public static void setAppBypassed(Context context, String pkg, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(PREF_BYPASS_APPS, null);
        Set<String> newSet = new HashSet<>();
        if (set != null) {
            newSet.addAll(set);
        } else {
            newSet.add(PKG_APPLE_MUSIC);
            newSet.add(PKG_TIDAL);
            newSet.add(PKG_BITCHORD);
        }
        if (enabled) {
            newSet.add(pkg);
        } else {
            newSet.remove(pkg);
        }
        prefs.edit().putStringSet(PREF_BYPASS_APPS, newSet).apply();
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
        Log.i(TAG, "setDacRate: " + targetRate + " Hz, safePause=" + safePause);

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

                if (targetRate == 0) {
                    am.clearPreferredMixerAttributes(mediaAttrs, usbDev);
                    success = true;
                } else {
                    AudioFormat format = new AudioFormat.Builder()
                            .setSampleRate(targetRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_24BIT_PACKED)
                            .build();

                    AudioMixerAttributes mixerAttr = new AudioMixerAttributes.Builder(format)
                            .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT)
                            .build();

                    success = am.setPreferredMixerAttributes(mediaAttrs, usbDev, mixerAttr);
                }
            }
        } catch (Exception ignored) {}

        // Root fallback
        if (!success) {
            try {
                if (targetRate == 0) {
                    runRootCommand("resetprop -n persist.vendor.audio.samplerates 44100,48000,88200,96000,176400,192000,352800,384000");
                } else {
                    runRootCommand("resetprop -n persist.vendor.audio.samplerates " + targetRate);
                }
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "Root fallback error", e);
            }
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
