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
    private TextView tvBadgeBitPerfect;
    private TextView tvSourceRate;
    private TextView tvDacRate;
    private TextView tvFormat;
    private TextView tvDacName;
    private Switch switchAutoRate;

    private Button btnModeBypass;
    private Button btnModeOffload;

    private Button btnRate8k;
    private Button btnRate16k;
    private Button btnRate32k;
    private Button btnRate44k;
    private Button btnRate48k;
    private Button btnRate96k;
    private Button btnRate192k;
    private Button btnRate384k;
    private Button btnRateReset;

    private Button[] mRateButtons;
    private int mSelectedRate = 0;
    private boolean mIsOffload = false;
    private boolean mIsAutoSwitch = true;

    private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int sourceRate = intent.getIntExtra("sourceRate", 0);
            int dacRate = intent.getIntExtra("dacRate", 0);
            String format = intent.getStringExtra("format");
            String dacName = intent.getStringExtra("dacName");
            boolean isBitPerfect = intent.getBooleanExtra("isBitPerfect", false);

            updateUI(sourceRate, dacRate, format, dacName, isBitPerfect);
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
        tvBadgeBitPerfect = findViewById(R.id.tvBadgeBitPerfect);
        tvSourceRate = findViewById(R.id.tvSourceRate);
        tvDacRate = findViewById(R.id.tvDacRate);
        tvFormat = findViewById(R.id.tvFormat);
        tvDacName = findViewById(R.id.tvDacName);
        switchAutoRate = findViewById(R.id.switchAutoRate);

        btnModeBypass = findViewById(R.id.btnModeBypass);
        btnModeOffload = findViewById(R.id.btnModeOffload);

        btnRate8k = findViewById(R.id.btnRate8k);
        btnRate16k = findViewById(R.id.btnRate16k);
        btnRate32k = findViewById(R.id.btnRate32k);
        btnRate44k = findViewById(R.id.btnRate44k);
        btnRate48k = findViewById(R.id.btnRate48k);
        btnRate96k = findViewById(R.id.btnRate96k);
        btnRate192k = findViewById(R.id.btnRate192k);
        btnRate384k = findViewById(R.id.btnRate384k);
        btnRateReset = findViewById(R.id.btnRateReset);

        mRateButtons = new Button[]{
                btnRate8k, btnRate16k, btnRate32k, btnRate44k,
                btnRate48k, btnRate96k, btnRate192k, btnRate384k, btnRateReset
        };
    }

    private void setupListeners() {
        switchAutoRate.setOnCheckedChangeListener((btn, isChecked) -> {
            mIsAutoSwitch = isChecked;
            restartMonitorService();
            Toast.makeText(this, isChecked ? "Auto-switch aktif" : "Auto-switch nonaktif", Toast.LENGTH_SHORT).show();
        });

        btnModeBypass.setOnClickListener(v -> setMode(false));
        btnModeOffload.setOnClickListener(v -> setMode(true));

        btnRate8k.setOnClickListener(v -> selectRate(8000, btnRate8k));
        btnRate16k.setOnClickListener(v -> selectRate(16000, btnRate16k));
        btnRate32k.setOnClickListener(v -> selectRate(32000, btnRate32k));
        btnRate44k.setOnClickListener(v -> selectRate(44100, btnRate44k));
        btnRate48k.setOnClickListener(v -> selectRate(48000, btnRate48k));
        btnRate96k.setOnClickListener(v -> selectRate(96000, btnRate96k));
        btnRate192k.setOnClickListener(v -> selectRate(192000, btnRate192k));
        btnRate384k.setOnClickListener(v -> selectRate(384000, btnRate384k));
        btnRateReset.setOnClickListener(v -> selectRate(0, btnRateReset));
    }

    private void setMode(boolean isOffload) {
        mIsOffload = isOffload;
        if (isOffload) {
            btnModeOffload.setBackgroundResource(R.drawable.btn_pill_active);
            btnModeOffload.setTextColor(getColor(R.color.text_primary));
            btnModeBypass.setBackgroundResource(R.drawable.btn_pill);
            btnModeBypass.setTextColor(getColor(R.color.text_secondary));
        } else {
            btnModeBypass.setBackgroundResource(R.drawable.btn_pill_active);
            btnModeBypass.setTextColor(getColor(R.color.text_primary));
            btnModeOffload.setBackgroundResource(R.drawable.btn_pill);
            btnModeOffload.setTextColor(getColor(R.color.text_secondary));
        }
        restartMonitorService();
    }

    private void selectRate(int rateHz, Button clickedBtn) {
        mSelectedRate = rateHz;

        // Update button visual states
        for (Button b : mRateButtons) {
            b.setBackgroundResource(R.drawable.btn_pill);
            b.setTextColor(getColor(R.color.text_secondary));
        }
        clickedBtn.setBackgroundResource(R.drawable.btn_pill_active);
        clickedBtn.setTextColor(getColor(R.color.text_primary));

        new Thread(() -> {
            boolean ok = AudioEngine.setDacRate(MainActivity.this, rateHz, mIsOffload, false);
            runOnUiThread(() -> {
                String text = rateHz == 0 ? "Rate direset ke default" : "Rate DAC diubah ke " + formatRate(rateHz);
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void updateUI(int sourceRate, int dacRate, String format, String dacName, boolean isBitPerfect) {
        tvSourceRate.setText(formatRate(sourceRate));
        tvDacRate.setText(formatRate(dacRate));
        if (format != null && !format.isEmpty()) tvFormat.setText(format);
        if (dacName != null && !dacName.isEmpty()) tvDacName.setText(dacName);

        if (isBitPerfect) {
            tvBadgeBitPerfect.setText(R.string.status_bitperfect);
            tvBadgeBitPerfect.setBackgroundResource(R.drawable.badge_bitperfect);
        } else {
            tvBadgeBitPerfect.setText(R.string.status_standby);
            tvBadgeBitPerfect.setBackgroundResource(R.drawable.badge_standby);
        }
    }

    private String formatRate(int rateHz) {
        if (rateHz <= 0) return "-- kHz";
        if (rateHz % 1000 == 0) {
            return (rateHz / 1000) + " kHz";
        } else {
            return String.format("%.1f kHz", rateHz / 1000.0);
        }
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, BitPerfectService.class);
        intent.putExtra("autoSwitch", mIsAutoSwitch);
        intent.putExtra("isOffload", mIsOffload);
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
