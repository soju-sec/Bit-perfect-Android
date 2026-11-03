package dev.deco.bitperfect;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioMixerAttributes;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

public class AudioEngine {
    private static final String TAG = "BitPerfect_AudioEngine";

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

        // 1. Detect USB Hardware DAC
        AudioDeviceInfo usbDev = getUsbDevice(context);
        if (usbDev != null) {
            status.dacName = usbDev.getProductName().toString();
        } else {
            status.dacName = readAlsaDacName();
        }

        // 2. Hardware DAC Clock from ALSA stream0
        readAlsaStreamStatus(status);

        // 3. AudioFlinger & MediaSession analysis via shell/dumpsys if available
        readAudioFlingerStatus(status);

        // 4. Bit-Perfect verification
        if (status.sourceRate > 0 && status.dacRate > 0 && status.sourceRate == status.dacRate) {
            status.isBitPerfect = true;
        }

        return status;
    }

    private static String readAlsaDacName() {
        for (int i = 0; i <= 3; i++) {
            File f = new File("/proc/asound/card" + i + "/stream0");
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                    String line = br.readLine();
                    if (line != null) {
                        return line.replaceAll(" at .*", "").trim();
                    }
                } catch (Exception ignored) {}
            }
        }
        return "Kiwi Ears AD1";
    }

    private static void readAlsaStreamStatus(AudioStatus status) {
        for (int i = 0; i <= 3; i++) {
            File f = new File("/proc/asound/card" + i + "/stream0");
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                    String line;
                    boolean isPlayback = false;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("Playback:")) {
                            isPlayback = true;
                        } else if (line.startsWith("Capture:")) {
                            isPlayback = false;
                        }
                        if (isPlayback) {
                            if (line.startsWith("Rate:") || line.startsWith("Momentary freq =")) {
                                String rateStr = line.replaceAll("[^0-9]", "");
                                if (!rateStr.isEmpty()) {
                                    try {
                                        status.dacRate = Integer.parseInt(rateStr);
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (line.startsWith("Format:")) {
                                String fmt = line.replace("Format:", "").trim();
                                if (fmt.contains("S24")) status.format = "PCM_24_BIT";
                                else if (fmt.contains("S32")) status.format = "PCM_32_BIT";
                                else if (fmt.contains("S16")) status.format = "PCM_16_BIT";
                            }
                        }
                    }
                } catch (Exception ignored) {}
                break;
            }
        }
    }

    private static void readAudioFlingerStatus(AudioStatus status) {
        try {
            // Read active media session package name
            String sessionDump = runShellCommand("dumpsys media_session");
            if (sessionDump != null) {
                for (String line : sessionDump.split("\n")) {
                    if (line.contains("package=")) {
                        String pkg = line.replaceAll(".*package=", "").trim();
                        if (!pkg.isEmpty()) {
                            status.activePkg = pkg;
                            status.activeApp = getFriendlyAppName(pkg);
                            break;
                        }
                    }
                }
            }

            // Read AudioFlinger direct & mixer rates
            String afDump = runShellCommand("dumpsys media.audio_flinger");
            if (afDump != null) {
                boolean inDirect = false;
                boolean inMixer = false;
                for (String line : afDump.split("\n")) {
                    line = line.trim();
                    if (line.contains("Output thread") && line.contains("type 1 (DIRECT)")) {
                        inDirect = true;
                        inMixer = false;
                        status.isDirectPcm = true;
                    } else if (line.contains("Output thread") && line.contains("type 0 (MIXER)")) {
                        inMixer = true;
                        inDirect = false;
                    } else if (line.contains("Output thread")) {
                        inDirect = false;
                        inMixer = false;
                    }

                    if (inDirect && line.startsWith("Sample rate:")) {
                        String sr = line.replaceAll("[^0-9]", "");
                        if (!sr.isEmpty()) {
                            int r = Integer.parseInt(sr);
                            status.sourceRate = r;
                            if (status.dacRate == 0) status.dacRate = r;
                        }
                    } else if (inMixer && line.startsWith("Sample rate:") && status.sourceRate == 0) {
                        String sr = line.replaceAll("[^0-9]", "");
                        if (!sr.isEmpty()) {
                            int r = Integer.parseInt(sr);
                            status.sourceRate = r;
                            if (status.dacRate == 0) status.dacRate = r;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading AudioFlinger status", e);
        }
    }

    public static String getFriendlyAppName(String pkg) {
        if (pkg == null) return "None";
        if (pkg.contains("tidal")) return "TIDAL";
        if (pkg.contains("apple") && pkg.contains("music")) return "Apple Music";
        if (pkg.contains("spotify")) return "Spotify";
        if (pkg.contains("qobuz")) return "Qobuz";
        if (pkg.contains("bitchord")) return "Bitchord";
        if (pkg.contains("poweramp")) return "Poweramp";
        if (pkg.contains("youtube") && pkg.contains("music")) return "YT Music";
        if (pkg.contains("youtube")) return "YouTube";
        return pkg;
    }

    public static boolean isMusicApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String lower = pkg.toLowerCase();
        return lower.contains("music") || lower.contains("tidal") || lower.contains("bitchord") ||
               lower.contains("qobuz") || lower.contains("spotify") || lower.contains("deezer") ||
               lower.contains("poweramp") || lower.contains("neutron") || lower.contains("hiby");
    }

    public static boolean setDacRate(Context context, int targetRate, boolean isOffload, boolean safePause) {
        Log.i(TAG, "setDacRate: " + targetRate + " Hz, isOffload=" + isOffload + ", safePause=" + safePause);

        // 1. Safe Pause Protection (Musik jeda ±4 detik saat pindah, supaya tidak bocor ke speaker)
        if (safePause) {
            dispatchMediaKey("127"); // KEYCODE_MEDIA_PAUSE
            SystemClock.sleep(3500);
        }

        boolean success = false;

        // 2. Try Native Android 14+ setPreferredMixerAttributes
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
                    Log.i(TAG, "Native setPreferredMixerAttributes result: " + success);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Native API failed, falling back to shell", e);
        }

        // 3. Root / Shell Fallback (if native failed or root requested)
        if (!success) {
            try {
                if (targetRate == 0) {
                    runRootCommand("resetprop -n persist.vendor.audio.samplerates 44100,48000,88200,96000,176400,192000,352800,384000");
                } else {
                    runRootCommand("resetprop -n persist.vendor.audio.samplerates " + targetRate);
                }
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "Root command execution error", e);
            }
        }

        // 4. Resume Playback
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
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
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
}
