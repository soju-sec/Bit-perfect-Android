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
    private TextView btnEngineStatus;
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

    private TextView chipApple;
    private TextView chipTidal;
    private TextView chipBitchord;
    private TextView chipSpotify;

    private Button btnRate44k;
    private Button btnRate48k;
    private Button btnRate88k;
    private Button btnRate96k;
    private Button btnRate176k;
    private Button btnRate192k;
    private Button btnRate384k;
    private Button btnRateReset;

    private View dockCenterAction;

    private Button[] mRateButtons;
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
        setupListeners();
        startMonitorService();
    }

    private void initViews() {
        btnEngineStatus = findViewById(R.id.btnEngineStatus);
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

        chipApple = findViewById(R.id.chipApple);
        chipTidal = findViewById(R.id.chipTidal);
        chipBitchord = findViewById(R.id.chipBitchord);
        chipSpotify = findViewById(R.id.chipSpotify);

        btnRate44k = findViewById(R.id.btnRate44k);
        btnRate48k = findViewById(R.id.btnRate48k);
        btnRate88k = findViewById(R.id.btnRate88k);
        btnRate96k = findViewById(R.id.btnRate96k);
        btnRate176k = findViewById(R.id.btnRate176k);
        btnRate192k = findViewById(R.id.btnRate192k);
        btnRate384k = findViewById(R.id.btnRate384k);
        btnRateReset = findViewById(R.id.btnRateReset);

        dockCenterAction = findViewById(R.id.dockCenterAction);

        mRateButtons = new Button[]{
                btnRate44k, btnRate48k, btnRate88k, btnRate96k,
                btnRate176k, btnRate192k, btnRate384k, btnRateReset
        };

        refreshAppChips();
        updateAutoSyncPill();
        updateTelemetry(false, 0, "", 0, "--", "Tidak Ada DAC USB", "DISCONNECTED", false, "Tidak Ada Pemutaran", false, "DAC Terputus");
    }

    private void setupListeners() {
        View.OnClickListener applyEngineListener = v -> {
            new Thread(() -> {
                boolean ok = AudioEngine.applyEngineTweaks(MainActivity.this);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, ok ? "⚡ Engine Bypass Mixer Aktif & Diterapkan!" : "Gagal menerapkan tweak (Periksa izin Root)", Toast.LENGTH_SHORT).show();
                });
            }).start();
        };

        btnEngineStatus.setOnClickListener(applyEngineListener);
        if (dockCenterAction != null) dockCenterAction.setOnClickListener(applyEngineListener);

        btnToggleAutoSync.setOnClickListener(v -> {
            mAutoRate = !mAutoRate;
            updateAutoSyncPill();
            restartMonitorService();
            Toast.makeText(MainActivity.this, "Auto-Sync Clock: " + (mAutoRate ? "Aktif (Otomatis Ikuti Track)" : "Nonaktif (Manual Clock)"), Toast.LENGTH_SHORT).show();
        });

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

    private void updateAutoSyncPill() {
        if (mAutoRate) {
            btnToggleAutoSync.setText("⚡ Auto-Sync Clock: AKTIF");
            btnToggleAutoSync.setTextColor(getColor(R.color.accent_emerald));
            btnToggleAutoSync.setBackgroundResource(R.drawable.chip_app_active);
        } else {
            btnToggleAutoSync.setText("⏸️ Auto-Sync Clock: NONAKTIF");
            btnToggleAutoSync.setTextColor(getColor(R.color.text_secondary));
            btnToggleAutoSync.setBackgroundResource(R.drawable.chip_app_inactive);
        }
    }

    private void toggleAppChip(String pkg, String appName) {
        boolean current = AudioEngine.isAppBypassed(this, pkg);
        AudioEngine.setAppBypassed(this, pkg, !current);
        refreshAppChips();
        Toast.makeText(this, appName + ": " + (!current ? "Bypass Mixer Aktif" : "Audio Standar (Mixer)"), Toast.LENGTH_SHORT).show();
    }

    private void refreshAppChips() {
        setChipState(chipApple, AudioEngine.PKG_APPLE_MUSIC, "Apple Music");
        setChipState(chipTidal, AudioEngine.PKG_TIDAL, "TIDAL");
        setChipState(chipBitchord, AudioEngine.PKG_BITCHORD, "Bitchord");
        setChipState(chipSpotify, AudioEngine.PKG_SPOTIFY, "Spotify");
    }

    private void setChipState(TextView chip, String pkg, String label) {
        boolean active = AudioEngine.isAppBypassed(this, pkg);
        if (active) {
            chip.setText("✓ " + label);
            chip.setTextColor(getColor(R.color.accent_emerald));
            chip.setBackgroundResource(R.drawable.chip_app_active);
        } else {
            chip.setText(label);
            chip.setTextColor(getColor(R.color.text_secondary));
            chip.setBackgroundResource(R.drawable.chip_app_inactive);
        }
    }

    private void selectRate(int rateHz, Button clickedBtn) {
        for (Button b : mRateButtons) {
            b.setBackgroundResource(R.drawable.btn_rate_glass);
            b.setTextColor(getColor(R.color.text_primary));
        }
        clickedBtn.setBackgroundResource(R.drawable.btn_rate_glass_active);
        clickedBtn.setTextColor(getColor(R.color.accent_emerald));

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
                tvMatchBadge.setTextColor(getColor(R.color.accent_emerald));
                tvDacRate.setTextColor(getColor(R.color.accent_emerald));
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
                b.setBackgroundResource(R.drawable.btn_rate_glass);
                b.setTextColor(getColor(R.color.text_primary));
            }
            target.setBackgroundResource(R.drawable.btn_rate_glass_active);
            target.setTextColor(getColor(R.color.accent_emerald));
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
