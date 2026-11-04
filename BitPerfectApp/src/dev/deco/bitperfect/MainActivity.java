package dev.deco.bitperfect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    // Pages / Tabs
    private View pageDashboard;
    private View pageApps;
    private View pageRate;
    private View pageSettings;

    // Bottom Navigation Items
    private View navTabDashboard;
    private View navTabApps;
    private View navCenterAction;
    private View navTabRate;
    private View navTabSettings;

    private TextView tvNavDashboard;
    private TextView tvNavApps;
    private TextView tvNavRate;
    private TextView tvNavSettings;

    private ImageView ivNavDashboard;
    private ImageView ivNavApps;
    private ImageView ivNavRate;
    private ImageView ivNavSettings;

    // Dashboard Views
    private AudioPipelineGaugeView pipelineGaugeView;
    private TextView btnToggleAutoSync;
    private TextView tvActiveApp;
    private TextView tvSourceDetail;
    private TextView tvSourceRate;
    private TextView tvSourceBitDepth;
    private TextView tvDacName;
    private TextView tvDacFormat;
    private TextView tvDacRate;
    private TextView tvMatchBadge;

    // Dynamic Apps Bypass Views
    private TextView tvAppsCountBadge;
    private TextView btnSelectAllApps;
    private TextView btnDeselectAllApps;
    private LinearLayout containerInstalledApps;
    private TextView tvEmptyApps;
    private List<AudioEngine.AudioAppInfo> mCachedAudioApps;

    // Bit Depth Controller Buttons
    private Button btnBitAuto;
    private Button btnBit16;
    private Button btnBit24;
    private Button btnBit32;
    private Button[] mBitButtons;
    private int mSelectedBitDepth = 0; // 0 = Auto

    // Manual Rate Buttons
    private Button btnRate44k;
    private Button btnRate48k;
    private Button btnRate88k;
    private Button btnRate96k;
    private Button btnRate176k;
    private Button btnRate192k;
    private Button btnRate384k;
    private Button btnRateReset;
    private Button[] mRateButtons;
    private int mSelectedRate = 0;

    // Settings Action
    private Button btnApplyEngine;
    private Button btnPurgeModule;

    private boolean mAutoRate = true;

    private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isDacConnected = intent.getBooleanExtra("isDacConnected", false);
            int sourceRate = intent.getIntExtra("sourceRate", 0);
            String sourceBitDepth = intent.getStringExtra("sourceBitDepth");
            int dacRate = intent.getIntExtra("dacRate", 0);
            String dacFormat = intent.getStringExtra("dacFormat");
            String dacName = intent.getStringExtra("dacName");
            String routeType = intent.getStringExtra("routeType");
            boolean isBitPerfect = intent.getBooleanExtra("isBitPerfect", false);
            String activeApp = intent.getStringExtra("activeApp");
            String trackInfo = intent.getStringExtra("trackInfo");
            boolean isAppBypassed = intent.getBooleanExtra("isAppBypassed", false);
            String matchStatus = intent.getStringExtra("matchStatus");

            updateTelemetry(isDacConnected, sourceRate, sourceBitDepth, dacRate, dacFormat, dacName, routeType, isBitPerfect, activeApp, trackInfo, isAppBypassed, matchStatus);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupNavigation();
        setupListeners();
        AudioEngine.requestRootPermissions(this);
        startMonitorService();
        loadAndRenderInstalledApps();

        new Thread(() -> {
            boolean active = AudioEngine.isEngineActive();
            SharedPreferences prefs = getSharedPreferences("bitperfect_prefs", Context.MODE_PRIVATE);
            boolean autoApply = prefs.getBoolean("auto_apply_engine", true);
            if (!active && autoApply) {
                AudioEngine.applyEngineTweaks(MainActivity.this);
            }
            checkAndUpdateEngineStatus();
        }).start();
    }

    private void initViews() {
        // Pages
        pageDashboard = findViewById(R.id.pageDashboard);
        pageApps = findViewById(R.id.pageApps);
        pageRate = findViewById(R.id.pageRate);
        pageSettings = findViewById(R.id.pageSettings);

        // Navigation
        navTabDashboard = findViewById(R.id.navTabDashboard);
        navTabApps = findViewById(R.id.navTabApps);
        navCenterAction = findViewById(R.id.navCenterAction);
        navTabRate = findViewById(R.id.navTabRate);
        navTabSettings = findViewById(R.id.navTabSettings);

        tvNavDashboard = findViewById(R.id.tvNavDashboard);
        tvNavApps = findViewById(R.id.tvNavApps);
        tvNavRate = findViewById(R.id.tvNavRate);
        tvNavSettings = findViewById(R.id.tvNavSettings);

        ivNavDashboard = findViewById(R.id.ivNavDashboard);
        ivNavApps = findViewById(R.id.ivNavApps);
        ivNavRate = findViewById(R.id.ivNavRate);
        ivNavSettings = findViewById(R.id.ivNavSettings);

        // Dashboard
        pipelineGaugeView = findViewById(R.id.pipelineGaugeView);
        btnToggleAutoSync = findViewById(R.id.btnToggleAutoSync);

        tvActiveApp = findViewById(R.id.tvActiveApp);
        tvSourceDetail = findViewById(R.id.tvSourceDetail);
        tvSourceRate = findViewById(R.id.tvSourceRate);
        tvSourceBitDepth = findViewById(R.id.tvSourceBitDepth);

        tvDacName = findViewById(R.id.tvDacName);
        tvDacFormat = findViewById(R.id.tvDacFormat);
        tvDacRate = findViewById(R.id.tvDacRate);
        tvMatchBadge = findViewById(R.id.tvMatchBadge);

        // Apps Bypass
        tvAppsCountBadge = findViewById(R.id.tvAppsCountBadge);
        btnSelectAllApps = findViewById(R.id.btnSelectAllApps);
        btnDeselectAllApps = findViewById(R.id.btnDeselectAllApps);
        containerInstalledApps = findViewById(R.id.containerInstalledApps);
        tvEmptyApps = findViewById(R.id.tvEmptyApps);

        // Bit Depth Buttons
        btnBitAuto = findViewById(R.id.btnBitAuto);
        btnBit16 = findViewById(R.id.btnBit16);
        btnBit24 = findViewById(R.id.btnBit24);
        btnBit32 = findViewById(R.id.btnBit32);

        mBitButtons = new Button[]{
                btnBitAuto, btnBit16, btnBit24, btnBit32
        };

        // Rates Buttons
        btnRate44k = findViewById(R.id.btnRate44k);
        btnRate48k = findViewById(R.id.btnRate48k);
        btnRate88k = findViewById(R.id.btnRate88k);
        btnRate96k = findViewById(R.id.btnRate96k);
        btnRate176k = findViewById(R.id.btnRate176k);
        btnRate192k = findViewById(R.id.btnRate192k);
        btnRate384k = findViewById(R.id.btnRate384k);
        btnRateReset = findViewById(R.id.btnRateReset);

        mRateButtons = new Button[]{
                btnRate44k, btnRate48k, btnRate88k, btnRate96k,
                btnRate176k, btnRate192k, btnRate384k, btnRateReset
        };

        btnApplyEngine = findViewById(R.id.btnApplyEngine);
        btnPurgeModule = findViewById(R.id.btnPurgeModule);

        mSelectedBitDepth = AudioEngine.getTargetBitDepth(this);
        highlightBitDepthButton(mSelectedBitDepth);

        updateAutoSyncPill();
        updateTelemetry(false, 0, "", 0, "--", "Tidak Ada DAC USB", "DISCONNECTED", false, "Tidak Ada Pemutaran", "", false, "DAC Terputus");
    }

    private void setupNavigation() {
        navTabDashboard.setOnClickListener(v -> switchTab(0));
        navTabApps.setOnClickListener(v -> {
            switchTab(1);
            if (mCachedAudioApps == null || mCachedAudioApps.isEmpty()) {
                loadAndRenderInstalledApps();
            }
        });
        navTabRate.setOnClickListener(v -> switchTab(2));
        navTabSettings.setOnClickListener(v -> switchTab(3));

        navCenterAction.setOnClickListener(v -> {
            applyEngineWithToast();
        });
    }

    private void switchTab(int tabIndex) {
        pageDashboard.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        pageApps.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        pageRate.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);

        if (tabIndex == 3) {
            checkAndUpdateEngineStatus();
        }

        int activeColor = getColor(R.color.dock_icon_active);
        int inactiveColor = getColor(R.color.dock_icon_inactive);

        tvNavDashboard.setTextColor(tabIndex == 0 ? activeColor : inactiveColor);
        tvNavApps.setTextColor(tabIndex == 1 ? activeColor : inactiveColor);
        tvNavRate.setTextColor(tabIndex == 2 ? activeColor : inactiveColor);
        tvNavSettings.setTextColor(tabIndex == 3 ? activeColor : inactiveColor);

        if (ivNavDashboard != null) ivNavDashboard.setColorFilter(tabIndex == 0 ? activeColor : inactiveColor);
        if (ivNavApps != null) ivNavApps.setColorFilter(tabIndex == 1 ? activeColor : inactiveColor);
        if (ivNavRate != null) ivNavRate.setColorFilter(tabIndex == 2 ? activeColor : inactiveColor);
        if (ivNavSettings != null) ivNavSettings.setColorFilter(tabIndex == 3 ? activeColor : inactiveColor);
    }

    private void setupListeners() {
        btnToggleAutoSync.setOnClickListener(v -> {
            mAutoRate = !mAutoRate;
            updateAutoSyncPill();
            restartMonitorService();
            Toast.makeText(MainActivity.this, "Auto-Sync Clock: " + (mAutoRate ? "Aktif" : "Nonaktif"), Toast.LENGTH_SHORT).show();
        });

        if (btnApplyEngine != null) {
            btnApplyEngine.setOnClickListener(v -> applyEngineWithToast());
        }

        if (btnPurgeModule != null) {
            btnPurgeModule.setOnClickListener(v -> {
                Toast.makeText(MainActivity.this, "Mematikan media & mengembalikan audio ke mode stock...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    boolean ok = AudioEngine.restoreStockAudio(MainActivity.this);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, ok ? "Audio dikembalikan ke mode stock normal. Volume dibisukan demi keselamatan." : "Gagal mengembalikan audio", Toast.LENGTH_LONG).show();
                        checkAndUpdateEngineStatus();
                    });
                }).start();
            });
        }

        if (btnSelectAllApps != null) {
            btnSelectAllApps.setOnClickListener(v -> toggleAllApps(true));
        }

        if (btnDeselectAllApps != null) {
            btnDeselectAllApps.setOnClickListener(v -> toggleAllApps(false));
        }

        // Bit Depth Listeners
        btnBitAuto.setOnClickListener(v -> selectBitDepth(0, btnBitAuto));
        btnBit16.setOnClickListener(v -> selectBitDepth(16, btnBit16));
        btnBit24.setOnClickListener(v -> selectBitDepth(24, btnBit24));
        btnBit32.setOnClickListener(v -> selectBitDepth(32, btnBit32));

        // Sample Rate Listeners
        btnRate44k.setOnClickListener(v -> selectRate(44100, btnRate44k));
        btnRate48k.setOnClickListener(v -> selectRate(48000, btnRate48k));
        btnRate88k.setOnClickListener(v -> selectRate(88200, btnRate88k));
        btnRate96k.setOnClickListener(v -> selectRate(96000, btnRate96k));
        btnRate176k.setOnClickListener(v -> selectRate(176400, btnRate176k));
        btnRate192k.setOnClickListener(v -> selectRate(192000, btnRate192k));
        btnRate384k.setOnClickListener(v -> selectRate(384000, btnRate384k));
        btnRateReset.setOnClickListener(v -> selectRate(0, btnRateReset));
    }

    private void loadAndRenderInstalledApps() {
        if (tvEmptyApps != null) {
            tvEmptyApps.setVisibility(View.VISIBLE);
            tvEmptyApps.setText("Memindai aplikasi pemutar audio di sistem...");
        }

        new Thread(() -> {
            List<AudioEngine.AudioAppInfo> apps = AudioEngine.getInstalledAudioApps(MainActivity.this);
            mCachedAudioApps = apps;

            runOnUiThread(() -> {
                if (containerInstalledApps == null) return;
                containerInstalledApps.removeAllViews();

                if (tvAppsCountBadge != null) {
                    tvAppsCountBadge.setText(apps.size() + " Terdeteksi");
                }

                if (apps.isEmpty()) {
                    if (tvEmptyApps != null) {
                        tvEmptyApps.setText("Tidak ada aplikasi audio yang terdeteksi");
                        tvEmptyApps.setVisibility(View.VISIBLE);
                    }
                    return;
                }

                if (tvEmptyApps != null) {
                    tvEmptyApps.setVisibility(View.GONE);
                }

                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                for (AudioEngine.AudioAppInfo app : apps) {
                    View itemView = inflater.inflate(R.layout.item_app_bypass, containerInstalledApps, false);
                    ImageView ivIcon = itemView.findViewById(R.id.ivAppIcon);
                    TextView tvName = itemView.findViewById(R.id.tvAppName);
                    TextView tvPkg = itemView.findViewById(R.id.tvAppPkg);
                    TextView tvBadge = itemView.findViewById(R.id.tvAppBypassBadge);
                    Switch swBypass = itemView.findViewById(R.id.swAppBypass);

                    if (app.icon != null) {
                        ivIcon.setImageDrawable(app.icon);
                    }
                    tvName.setText(app.appName);
                    tvPkg.setText(app.packageName);
                    swBypass.setChecked(app.isBypassed);
                    updateAppItemBadge(tvBadge, app.isBypassed);

                    View.OnClickListener toggleListener = v -> {
                        boolean newState = !swBypass.isChecked();
                        swBypass.setChecked(newState);
                        app.isBypassed = newState;
                        AudioEngine.setAppBypassed(MainActivity.this, app.packageName, newState);
                        updateAppItemBadge(tvBadge, newState);
                        Toast.makeText(MainActivity.this, app.appName + ": " + (newState ? "Bypass Mixer Aktif" : "Mixer Standar"), Toast.LENGTH_SHORT).show();
                    };

                    itemView.setOnClickListener(toggleListener);
                    swBypass.setOnClickListener(toggleListener);

                    containerInstalledApps.addView(itemView);
                }
            });
        }).start();
    }

    private void updateAppItemBadge(TextView badge, boolean isBypassed) {
        if (isBypassed) {
            badge.setText("Bypass Mixer: AKTIF");
            badge.setTextColor(getColor(R.color.accent_coral));
        } else {
            badge.setText("Mixer Standar (Resample)");
            badge.setTextColor(getColor(R.color.text_muted));
        }
    }

    private void toggleAllApps(boolean enabled) {
        new Thread(() -> {
            AudioEngine.setAllAppsBypassed(MainActivity.this, enabled);
            runOnUiThread(() -> {
                loadAndRenderInstalledApps();
                Toast.makeText(MainActivity.this, enabled ? "Semua aplikasi diizinkan bypass mixer" : "Semua aplikasi diarahkan ke mixer standar", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void applyEngineWithToast() {
        Toast.makeText(MainActivity.this, "Menerapkan Engine Bit-Perfect & mematikan audio...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = AudioEngine.applyEngineTweaks(MainActivity.this);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, ok ? "⚡ Engine Bit-Perfect Aktif! Volume dibisukan demi keselamatan." : "Gagal menerapkan tweak (Periksa izin Root)", Toast.LENGTH_LONG).show();
                checkAndUpdateEngineStatus();
            });
        }).start();
    }

    private void checkAndUpdateEngineStatus() {
        new Thread(() -> {
            boolean active = AudioEngine.isEngineActive();
            runOnUiThread(() -> {
                if (btnApplyEngine != null) {
                    btnApplyEngine.setText(active ? "⚡ Engine Bit-Perfect AKTIF (Refresh)" : "⚡ Terapkan Engine Root Standalone");
                }
            });
        }).start();
    }

    private void updateAutoSyncPill() {
        if (mAutoRate) {
            btnToggleAutoSync.setText("+  Ikuti Rate Otomatis: AKTIF");
            btnToggleAutoSync.setTextColor(getColor(R.color.accent_coral));
        } else {
            btnToggleAutoSync.setText("+  Ikuti Rate Otomatis: NONAKTIF");
            btnToggleAutoSync.setTextColor(getColor(R.color.text_muted));
        }
    }

    private void selectBitDepth(int bitDepth, Button clickedBtn) {
        mSelectedBitDepth = bitDepth;
        for (Button b : mBitButtons) {
            b.setBackgroundResource(R.drawable.btn_rate_light);
            b.setTextColor(getColor(R.color.text_title));
        }
        clickedBtn.setBackgroundResource(R.drawable.btn_rate_light_active);
        clickedBtn.setTextColor(getColor(R.color.card_bg));

        AudioEngine.setTargetBitDepth(this, bitDepth);

        new Thread(() -> {
            AudioEngine.setDacRate(MainActivity.this, mSelectedRate, bitDepth, true);
            runOnUiThread(() -> {
                String text;
                if (bitDepth == 0) {
                    text = "Bit Depth: Auto 1:1 (Mengikuti sumber track)";
                } else {
                    text = "Bit Depth DAC disetel ke " + bitDepth + "-bit PCM";
                }
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void highlightBitDepthButton(int bitDepth) {
        Button target = btnBitAuto;
        if (bitDepth == 16) target = btnBit16;
        else if (bitDepth == 24) target = btnBit24;
        else if (bitDepth == 32) target = btnBit32;

        for (Button b : mBitButtons) {
            b.setBackgroundResource(R.drawable.btn_rate_light);
            b.setTextColor(getColor(R.color.text_title));
        }
        if (target != null) {
            target.setBackgroundResource(R.drawable.btn_rate_light_active);
            target.setTextColor(getColor(R.color.card_bg));
        }
    }

    private void selectRate(int rateHz, Button clickedBtn) {
        mSelectedRate = rateHz;
        for (Button b : mRateButtons) {
            b.setBackgroundResource(R.drawable.btn_rate_light);
            b.setTextColor(getColor(R.color.text_title));
        }
        clickedBtn.setBackgroundResource(R.drawable.btn_rate_light_active);
        clickedBtn.setTextColor(getColor(R.color.card_bg));

        new Thread(() -> {
            AudioEngine.setDacRate(MainActivity.this, rateHz, mSelectedBitDepth, true);
            runOnUiThread(() -> {
                String text = rateHz == 0 ? "DAC direset ke 48 kHz (Default Mixer)" : "Clock DAC diubah ke " + formatRate(rateHz);
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void updateTelemetry(boolean isDacConnected, int sourceRate, String sourceBitDepth, int dacRate, String dacFormat, String dacName, String routeType, boolean isBitPerfect, String activeApp, String trackInfo, boolean isAppBypassed, String matchStatus) {
        // 1. Update the big Hero Arch Gauge View!
        if (pipelineGaugeView != null) {
            int displayRate = (dacRate > 0) ? dacRate : sourceRate;
            pipelineGaugeView.updateStatus(isDacConnected, displayRate, sourceBitDepth, isBitPerfect, routeType, activeApp, dacName);
        }

        // 2. Update Source Stream Card
        if (activeApp != null && !activeApp.isEmpty() && !"None".equals(activeApp)) {
            tvActiveApp.setText(activeApp);
        } else {
            tvActiveApp.setText("Tidak Ada Pemutaran");
        }

        if (trackInfo != null && !trackInfo.isEmpty()) {
            tvSourceDetail.setText(trackInfo);
        } else if (sourceRate > 0) {
            tvSourceDetail.setText("Sedang Memutar Audio");
        } else {
            tvSourceDetail.setText("Audio Idle");
        }

        tvSourceRate.setText(formatRate(sourceRate));
        if (sourceBitDepth != null && !sourceBitDepth.isEmpty() && sourceRate > 0) {
            tvSourceBitDepth.setText(sourceBitDepth + " PCM");
        } else {
            tvSourceBitDepth.setText("Audio Idle");
        }

        // 3. Update Hardware DAC Card
        if (isDacConnected) {
            tvDacName.setText(dacName != null ? dacName : "USB Audio DAC");
            tvDacRate.setText(formatRate(dacRate));
            tvDacFormat.setText(dacFormat != null ? dacFormat : "Terhubung");
            if (isBitPerfect) {
                tvMatchBadge.setText("Zero SRC 1:1");
                tvMatchBadge.setTextColor(getColor(R.color.accent_coral));
                tvDacRate.setTextColor(getColor(R.color.accent_coral));
            } else if ("MIXER".equals(routeType)) {
                int displayK = dacRate > 0 ? (dacRate / 1000) : 48;
                tvMatchBadge.setText("Resampled " + displayK + "k");
                tvMatchBadge.setTextColor(getColor(R.color.accent_amber));
                tvDacRate.setTextColor(getColor(R.color.accent_amber));
            } else {
                tvMatchBadge.setText("Standby");
                tvMatchBadge.setTextColor(getColor(R.color.text_muted));
                tvDacRate.setTextColor(getColor(R.color.text_muted));
            }
        } else {
            tvDacName.setText("Tidak Ada DAC USB");
            tvDacFormat.setText("Status: Terputus (Unplugged)");
            tvDacRate.setText("-- kHz");
            tvDacRate.setTextColor(getColor(R.color.text_muted));
            tvMatchBadge.setText("Offline");
            tvMatchBadge.setTextColor(getColor(R.color.text_muted));
        }

        highlightRateButton(dacRate);
    }

    private void highlightRateButton(int rateHz) {
        if (rateHz <= 0) return;
        Button target = null;
        if (rateHz == 44100) target = btnRate44k;
        else if (rateHz == 48000) target = btnRate48k;
        else if (rateHz == 88200) target = btnRate88k;
        else if (rateHz == 96000) target = btnRate96k;
        else if (rateHz == 176400) target = btnRate176k;
        else if (rateHz == 192000) target = btnRate192k;
        else if (rateHz == 384000) target = btnRate384k;

        if (target != null) {
            for (Button b : mRateButtons) {
                b.setBackgroundResource(R.drawable.btn_rate_light);
                b.setTextColor(getColor(R.color.text_title));
            }
            target.setBackgroundResource(R.drawable.btn_rate_light_active);
            target.setTextColor(getColor(R.color.card_bg));
        }
    }

    private String formatRate(int rateHz) {
        if (rateHz <= 0) return "-- kHz";
        if (rateHz % 1000 == 0) {
            return (rateHz / 1000) + ".0 kHz";
        } else {
            return String.format("%.1f kHz", rateHz / 1000.0);
        }
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, BitPerfectService.class);
        intent.putExtra("autoSwitch", mAutoRate);
        intent.putExtra("safePause", true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void restartMonitorService() {
        startMonitorService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndUpdateEngineStatus();
        IntentFilter filter = new IntentFilter(BitPerfectService.ACTION_STATUS_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mStatusReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mStatusReceiver);
        } catch (Exception ignored) {}
    }
}
