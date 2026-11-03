package dev.deco.bitperfect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

    // Per-App Bypass Chips
    private TextView chipApple;
    private TextView chipTidal;
    private TextView chipBitchord;
    private TextView chipSpotify;

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

    // Settings Action
    private Button btnApplyEngine;

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
            boolean isAppBypassed = intent.getBooleanExtra("isAppBypassed", false);
            String matchStatus = intent.getStringExtra("matchStatus");

            updateTelemetry(isDacConnected, sourceRate, sourceBitDepth, dacRate, dacFormat, dacName, routeType, isBitPerfect, activeApp, isAppBypassed, matchStatus);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupNavigation();
        setupListeners();
        startMonitorService();
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

        // Apps
        chipApple = findViewById(R.id.chipApple);
        chipTidal = findViewById(R.id.chipTidal);
        chipBitchord = findViewById(R.id.chipBitchord);
        chipSpotify = findViewById(R.id.chipSpotify);

        // Rates
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

        refreshAppChips();
        updateAutoSyncPill();
        updateTelemetry(false, 0, "", 0, "--", "Tidak Ada DAC USB", "DISCONNECTED", false, "Tidak Ada Pemutaran", false, "DAC Terputus");
    }

    private void setupNavigation() {
        navTabDashboard.setOnClickListener(v -> switchTab(0));
        navTabApps.setOnClickListener(v -> switchTab(1));
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

        int activeColor = getColor(R.color.dock_icon_active);
        int inactiveColor = getColor(R.color.dock_icon_inactive);

        tvNavDashboard.setTextColor(tabIndex == 0 ? activeColor : inactiveColor);
        tvNavApps.setTextColor(tabIndex == 1 ? activeColor : inactiveColor);
        tvNavRate.setTextColor(tabIndex == 2 ? activeColor : inactiveColor);
        tvNavSettings.setTextColor(tabIndex == 3 ? activeColor : inactiveColor);
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

        chipApple.setOnClickListener(v -> toggleAppChip(AudioEngine.PKG_APPLE_MUSIC, "Apple Music"));
        chipTidal.setOnClickListener(v -> toggleAppChip(AudioEngine.PKG_TIDAL, "TIDAL"));
        chipBitchord.setOnClickListener(v -> toggleAppChip(AudioEngine.PKG_BITCHORD, "Bitchord"));
        chipSpotify.setOnClickListener(v -> toggleAppChip(AudioEngine.PKG_SPOTIFY, "Spotify"));

        btnRate44k.setOnClickListener(v -> selectRate(44100, btnRate44k));
        btnRate48k.setOnClickListener(v -> selectRate(48000, btnRate48k));
        btnRate88k.setOnClickListener(v -> selectRate(88200, btnRate88k));
        btnRate96k.setOnClickListener(v -> selectRate(96000, btnRate96k));
        btnRate176k.setOnClickListener(v -> selectRate(176400, btnRate176k));
        btnRate192k.setOnClickListener(v -> selectRate(192000, btnRate192k));
        btnRate384k.setOnClickListener(v -> selectRate(384000, btnRate384k));
        btnRateReset.setOnClickListener(v -> selectRate(0, btnRateReset));
    }

    private void applyEngineWithToast() {
        new Thread(() -> {
            boolean ok = AudioEngine.applyEngineTweaks(MainActivity.this);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, ok ? "⚡ Engine Bypass Mixer Aktif & Diterapkan!" : "Gagal menerapkan tweak (Periksa izin KernelSU)", Toast.LENGTH_SHORT).show();
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

    private void toggleAppChip(String pkg, String appName) {
        boolean current = AudioEngine.isAppBypassed(this, pkg);
        AudioEngine.setAppBypassed(this, pkg, !current);
        refreshAppChips();
        Toast.makeText(this, appName + ": " + (!current ? "Bypass Mixer Aktif" : "Audio Standar (Mixer)"), Toast.LENGTH_SHORT).show();
    }

    private void refreshAppChips() {
        setChipState(chipApple, AudioEngine.PKG_APPLE_MUSIC, "🍎 Apple Music");
        setChipState(chipTidal, AudioEngine.PKG_TIDAL, "🌊 TIDAL");
        setChipState(chipBitchord, AudioEngine.PKG_BITCHORD, "🎵 Bitchord");
        setChipState(chipSpotify, AudioEngine.PKG_SPOTIFY, "🟢 Spotify");
    }

    private void setChipState(TextView chip, String pkg, String label) {
        boolean active = AudioEngine.isAppBypassed(this, pkg);
        if (active) {
            chip.setText(label);
            chip.setTextColor(getColor(R.color.accent_coral));
            chip.setBackgroundResource(R.drawable.bg_chip_app_active);
        } else {
            chip.setText(label);
            chip.setTextColor(getColor(R.color.text_body));
            chip.setBackgroundResource(R.drawable.bg_chip_app_inactive);
        }
    }

    private void selectRate(int rateHz, Button clickedBtn) {
        for (Button b : mRateButtons) {
            b.setBackgroundResource(R.drawable.btn_rate_light);
            b.setTextColor(getColor(R.color.text_title));
        }
        clickedBtn.setBackgroundResource(R.drawable.btn_rate_light_active);
        clickedBtn.setTextColor(getColor(R.color.card_bg));

        new Thread(() -> {
            AudioEngine.setDacRate(MainActivity.this, rateHz, true);
            runOnUiThread(() -> {
                String text = rateHz == 0 ? "DAC direset ke 48 kHz (Default Mixer)" : "Clock DAC diubah ke " + formatRate(rateHz);
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void updateTelemetry(boolean isDacConnected, int sourceRate, String sourceBitDepth, int dacRate, String dacFormat, String dacName, String routeType, boolean isBitPerfect, String activeApp, boolean isAppBypassed, String matchStatus) {
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
                tvMatchBadge.setText("Resampled 48k");
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
