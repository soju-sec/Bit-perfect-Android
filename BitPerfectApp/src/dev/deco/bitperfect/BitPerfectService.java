package dev.deco.bitperfect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class BitPerfectService extends Service {
    private static final String TAG = "BitPerfectService";
    private static final String CHANNEL_ID = "bit_perfect_suite_channel";
    public static final String ACTION_STATUS_UPDATE = "dev.deco.bitperfect.STATUS_UPDATE";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning = false;
    private int mLastSourceRate = 0;
    private boolean mIsAutoSwitch = true;
    private boolean mSafePause = true;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            AudioStatus status = AudioEngine.pollStatus(BitPerfectService.this);

            // Broadcast status to UI
            Intent intent = new Intent(ACTION_STATUS_UPDATE);
            intent.setPackage(getPackageName());
            intent.putExtra("isDacConnected", status.isDacConnected);
            intent.putExtra("sourceRate", status.sourceRate);
            intent.putExtra("sourceBitDepth", status.sourceBitDepth);
            intent.putExtra("dacRate", status.dacRate);
            intent.putExtra("dacFormat", status.dacFormat);
            intent.putExtra("dacName", status.dacName);
            intent.putExtra("routeType", status.routeType);
            intent.putExtra("isBitPerfect", status.isBitPerfect);
            intent.putExtra("activeApp", status.activeApp);
            intent.putExtra("activePkg", status.activePkg);
            intent.putExtra("trackInfo", status.trackInfo);
            intent.putExtra("isAppBypassed", status.isAppBypassed);
            intent.putExtra("matchStatus", status.matchStatus);
            sendBroadcast(intent);

            // Per-App Auto Bypass Alignment
            if (mIsAutoSwitch && status.isAppBypassed) {
                if (status.sourceRate > 0 && status.sourceRate != mLastSourceRate && status.sourceRate != status.dacRate) {
                    mLastSourceRate = status.sourceRate;
                    Log.i(TAG, "Auto-aligning DAC Clock for " + status.activeApp + ": " + status.sourceRate + " Hz");
                    new Thread(() -> {
                        AudioEngine.setDacRate(BitPerfectService.this, status.sourceRate, false);
                    }).start();
                }
            }

            updateNotification(status);
            mHandler.postDelayed(this, 1500);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            mIsAutoSwitch = intent.getBooleanExtra("autoSwitch", true);
            mSafePause = intent.getBooleanExtra("safePause", true);
        }

        Notification notification = buildNotification("Pemantauan Audio Aktif...");
        startForeground(1001, notification);

        if (!mRunning) {
            mRunning = true;
            mHandler.post(mPollRunnable);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        mHandler.removeCallbacks(mPollRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Bit-Perfect Audio Suite Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Melacak resolusi stream audio dan status bit-perfect");
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Bit-Perfect Suite")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(AudioStatus status) {
        String msg;
        if (!status.isDacConnected) {
            msg = "DAC Terputus • Sambungkan USB DAC";
        } else if (status.isBitPerfect) {
            msg = "1:1 Bit-Perfect (" + (status.dacRate / 1000) + " kHz) • " + status.activeApp;
        } else if (status.dacRate > 0) {
            msg = "DAC: " + (status.dacRate / 1000) + " kHz • Track: " + (status.sourceRate / 1000) + " kHz (" + status.activeApp + ")";
        } else {
            msg = "Standby • " + status.dacName;
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(1001, buildNotification(msg));
        }
    }
}
