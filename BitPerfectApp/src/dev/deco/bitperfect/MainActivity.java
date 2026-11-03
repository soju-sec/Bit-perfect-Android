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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView tvEngineBadge;
    private TextView tvRouteBadge;
    private TextView tvActiveApp;
    private TextView tvSourceRate;
    private TextView tvDacName;
    private TextView tvDacRate;
    private TextView tvMatchStatus;

    private Switch switchAppApple;
    private Switch switchAppTidal;
    private Switch switchAppBitchord;
    private Switch switchAppSpotify;

    private Button btnApplyEngine;
    private Switch switchAutoRate;
    private Switch switchSafePause;

    private Button btnRate44k;
    private Button btnRate48k;
    private Button btnRate88k;
    private Button btnRate96k;
    private Button btnRate176k;
    private Button btnRate192k;
    private Button btnRate384k;
    private Button btnRateReset;

    private TextView tvModuleStatus;
    private Button btnUninstallModule;

    private Button[] mRateButtons;
    private boolean mAutoRate = true;
    private boolean mSafePause = true;

    private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
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

            updateTelemetry(sourceRate, sourceBitDepth, dacRate, dacFormat, dacName, routeType, isBitPerfect, activeApp, isAppBypassed, matchStatus);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        checkModuleStatus();
        startMonitorService();
    }

    private void initViews() {
        tvEngineBadge = findViewById(R.id.tvEngineBadge);
        tvRouteBadge = findViewById(R.id.tvRouteBadge);
        tvActiveApp = findViewById(R.id.tvActiveApp);
        tvSourceRate = findViewById(R.id.tvSourceRate);
        tvDacName = findViewById(R.id.tvDacName);
        tvDacRate = findViewById(R.id.tvDacRate);
        tvMatchStatus = findViewById(R.id.tvMatchStatus);

        switchAppApple = findViewById(R.id.switchAppApple);
        switchAppTidal = findViewById(R.id.switchAppTidal);
        switchAppBitchord = findViewById(R.id.switchAppBitchord);
        switchAppSpotify = findViewById(R.id.switchAppSpotify);

        btnApplyEngine = findViewById(R.id.btnApplyEngine);
        switchAutoRate = findViewById(R.id.switchAutoRate);
        switchSafePause = findViewById(R.id.switchSafePause);

        btnRate44k = findViewById(R.id.btnRate44k);
        btnRate48k = findViewById(R.id.btnRate48k);
        btnRate88k = findViewById(R.id.btnRate88k);
        btnRate96k = findViewById(R.id.btnRate96k);
        btnRate176k = findViewById(R.id.btnRate176k);
        btnRate192k = findViewById(R.id.btnRate192k);
        btnRate384k = findViewById(R.id.btnRate384k);
        btnRateReset = findViewById(R.id.btnRateReset);

        tvModuleStatus = findViewById(R.id.tvModuleStatus);
        btnUninstallModule = findViewById(R.id.btnUninstallModule);

        mRateButtons = new Button[]{
                btnRate44k, btnRate48k, btnRate88k, btnRate96k,
                btnRate176k, btnRate192k, btnRate384k, btnRateReset
        };

        // Load app bypass saved states
        switchAppApple.setChecked(AudioEngine.isAppBypassed(this, AudioEngine.PKG_APPLE_MUSIC));
        switchAppTidal.setChecked(AudioEngine.isAppBypassed(this, AudioEngine.PKG_TIDAL));
        switchAppBitchord.setChecked(AudioEngine.isAppBypassed(this, AudioEngine.PKG_BITCHORD));
        switchAppSpotify.setChecked(AudioEngine.isAppBypassed(this, AudioEngine.PKG_SPOTIFY));
    }

    private void setupListeners() {
        switchAppApple.setOnCheckedChangeListener((v, isChecked) -> {
            AudioEngine.setAppBypassed(this, AudioEngine.PKG_APPLE_MUSIC, isChecked);
            Toast.makeText(this, "Apple Music: " + (isChecked ? "Bypass Mixer Aktif" : "Audio Standar"), Toast.LENGTH_SHORT).show();
        });

        switchAppTidal.setOnCheckedChangeListener((v, isChecked) -> {
            AudioEngine.setAppBypassed(this, AudioEngine.PKG_TIDAL, isChecked);
            Toast.makeText(this, "TIDAL: " + (isChecked ? "Bypass Mixer Aktif" : "Audio Standar"), Toast.LENGTH_SHORT).show();
        });

        switchAppBitchord.setOnCheckedChangeListener((v, isChecked) -> {
            AudioEngine.setAppBypassed(this, AudioEngine.PKG_BITCHORD, isChecked);
            Toast.makeText(this, "Bitchord: " + (isChecked ? "Bypass Mixer Aktif" : "Audio Standar"), Toast.LENGTH_SHORT).show();
        });

        switchAppSpotify.setOnCheckedChangeListener((v, isChecked) -> {
            AudioEngine.setAppBypassed(this, AudioEngine.PKG_SPOTIFY, isChecked);
            Toast.makeText(this, "Spotify: " + (isChecked ? "Bypass Mixer Aktif" : "Audio Standar"), Toast.LENGTH_SHORT).show();
        });

        btnApplyEngine.setOnClickListener(v -> {
            new Thread(() -> {
                boolean ok = AudioEngine.applyEngineTweaks(MainActivity.this);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, ok ? "Konfigurasi Bypass Mixer Berhasil Diterapkan!" : "Gagal menerapkan tweak (Periksa Root)", Toast.LENGTH_LONG).show();
                });
            }).start();
        });

        switchAutoRate.setOnCheckedChangeListener((v, isChecked) -> {
            mAutoRate = isChecked;
            restartMonitorService();
        });

        switchSafePause.setOnCheckedChangeListener((v, isChecked) -> {
            mSafePause = isChecked;
            restartMonitorService();
        });

        btnRate44k.setOnClickListener(v -> selectRate(44100, btnRate44k));
        btnRate48k.setOnClickListener(v -> selectRate(48000, btnRate48k));
        btnRate88k.setOnClickListener(v -> selectRate(88200, btnRate88k));
        btnRate96k.setOnClickListener(v -> selectRate(96000, btnRate96k));
        btnRate176k.setOnClickListener(v -> selectRate(176400, btnRate176k));
        btnRate192k.setOnClickListener(v -> selectRate(192000, btnRate192k));
        btnRate384k.setOnClickListener(v -> selectRate(384000, btnRate384k));
        btnRateReset.setOnClickListener(v -> selectRate(0, btnRateReset));

        btnUninstallModule.setOnClickListener(v -> {
            new Thread(() -> {
                boolean uninstalled = AudioEngine.uninstallMagiskModule();
                runOnUiThread(() -> {
                    checkModuleStatus();
                    Toast.makeText(MainActivity.this, uninstalled ? "Modul Magisk berhasil dinonaktifkan/dihapus!" : "Modul sudah tidak terpasang", Toast.LENGTH_LONG).show();
                });
            }).start();
        });
    }

    private void checkModuleStatus() {
        boolean installed = AudioEngine.isModuleInstalled();
        if (installed) {
            tvModuleStatus.setText("Modul lama 'disable_resample_48k' terdeteksi. Seluruh fitur sudah diambil alih oleh aplikasi ini, Anda dapat mencopotnya sekarang.");
            btnUninstallModule.setVisibility(View.VISIBLE);
            btnUninstallModule.setText("Copot Modul Magisk Sekarang");
        } else {
            tvModuleStatus.setText("Bersih! Tidak ada modul Magisk yang bentrok. Semua bypass mixer dikendalikan secara fleksibel oleh aplikasi.");
            btnUninstallModule.setVisibility(View.GONE);
        }
    }

    private void selectRate(int rateHz, Button clickedBtn) {
        for (Button b : mRateButtons) {
            b.setBackgroundResource(R.drawable.btn_rate);
            b.setTextColor(getColor(R.color.text_primary));
        }
        clickedBtn.setBackgroundResource(R.drawable.btn_rate_active);
        clickedBtn.setTextColor(getColor(R.color.text_primary));

        new Thread(() -> {
            AudioEngine.setDacRate(MainActivity.this, rateHz, false);
            runOnUiThread(() -> {
                String text = rateHz == 0 ? "Rate DAC direset ke 48 kHz (Default)" : "Clock DAC diubah ke " + formatRate(rateHz);
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void updateTelemetry(int sourceRate, String sourceBitDepth, int dacRate, String dacFormat, String dacName, String routeType, boolean isBitPerfect, String activeApp, boolean isAppBypassed, String matchStatus) {
        if (activeApp != null && !activeApp.isEmpty()) tvActiveApp.setText(activeApp);
        if (dacName != null && !dacName.isEmpty()) tvDacName.setText(dacName);

        String trackText = formatRate(sourceRate);
        if (sourceBitDepth != null && !sourceBitDepth.isEmpty() && sourceRate > 0) {
            trackText += " • " + sourceBitDepth;
        }
        tvSourceRate.setText(trackText);

        String dacText = formatRate(dacRate);
        if (dacFormat != null && !dacFormat.isEmpty()) {
            dacText += " • " + dacFormat;
        }
        tvDacRate.setText(dacText);

        if (matchStatus != null && !matchStatus.isEmpty()) {
            tvMatchStatus.setText(matchStatus);
        }

        if (isBitPerfect) {
            tvRouteBadge.setText(R.string.status_direct_bitperfect);
            tvRouteBadge.setBackgroundResource(R.drawable.badge_route_direct);
            tvRouteBadge.setTextColor(getColor(R.color.accent_emerald));
            tvMatchStatus.setTextColor(getColor(R.color.text_emerald));
        } else if ("MIXER".equals(routeType)) {
            tvRouteBadge.setText(R.string.status_mixer_active);
            tvRouteBadge.setBackgroundResource(R.drawable.badge_route_mixer);
            tvRouteBadge.setTextColor(getColor(R.color.accent_amber));
            tvMatchStatus.setTextColor(getColor(R.color.text_amber));
        } else {
            tvRouteBadge.setText(R.string.status_standby);
            tvRouteBadge.setBackgroundResource(R.drawable.badge_route_standby);
            tvRouteBadge.setTextColor(getColor(R.color.text_muted));
            tvMatchStatus.setTextColor(getColor(R.color.text_muted));
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
        intent.putExtra("safePause", mSafePause);
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
