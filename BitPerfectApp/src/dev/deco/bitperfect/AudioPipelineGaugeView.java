package dev.deco.bitperfect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class AudioPipelineGaugeView extends View {
    private boolean mIsDacConnected = false;
    private int mSampleRate = 0;
    private String mBitDepth = "24-bit";
    private boolean mIsBitPerfect = false;
    private String mRouteType = "DISCONNECTED";
    private String mActiveApp = "None";
    private String mDacName = "Tidak Ada DAC USB";

    private Paint mArcPaint;
    private Paint mTextPaint;
    private RectF mArcRect;

    private static final int TOTAL_SEGMENTS = 10;
    private static final float START_ANGLE = 145f;
    private static final float TOTAL_SWEEP = 250f;
    private static final float GAP_ANGLE = 6.5f;

    public AudioPipelineGaugeView(Context context) {
        super(context);
        init();
    }

    public AudioPipelineGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioPipelineGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mArcPaint.setStyle(Paint.Style.STROKE);
        mArcPaint.setStrokeCap(Paint.Cap.ROUND);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mArcRect = new RectF();
    }

    public void updateStatus(boolean isDacConnected, int sampleRate, String bitDepth, boolean isBitPerfect, String routeType, String activeApp, String dacName) {
        mIsDacConnected = isDacConnected;
        mSampleRate = sampleRate;
        if (bitDepth != null && !bitDepth.isEmpty()) mBitDepth = bitDepth;
        mIsBitPerfect = isBitPerfect;
        if (routeType != null) mRouteType = routeType;
        if (activeApp != null && !activeApp.isEmpty()) mActiveApp = activeApp;
        if (dacName != null && !dacName.isEmpty()) mDacName = dacName;
        postInvalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) (w * 0.74f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float cx = w / 2f;
        float cy = h * 0.52f;
        float radius = Math.min(w, h) * 0.43f;
        float strokeWidth = 17f * density;

        mArcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        mArcPaint.setStrokeWidth(strokeWidth);

        // Determine active segments and theme colors
        int activeSegments = 0;
        int activeColor;
        String topIcon;
        String subtitleText;
        String statusText;

        if (!mIsDacConnected || "DISCONNECTED".equals(mRouteType)) {
            // DAC UNPLUGGED
            activeSegments = 0;
            activeColor = Color.parseColor("#475569");
            topIcon = "🔌";
            subtitleText = "DAC TIDAK TERDETEKSI";
            statusText = "○ SAMBUNGKAN USB DAC";
        } else if ("STANDBY".equals(mRouteType) || mSampleRate <= 0) {
            // DAC CONNECTED BUT IDLE
            activeSegments = 1;
            activeColor = Color.parseColor("#64748B");
            topIcon = "⏸️";
            subtitleText = "AUDIO STANDBY (IDLE)";
            statusText = "○ MENUNGGU PEMUTARAN AUDIO";
        } else if (mIsBitPerfect || "DIRECT_PCM".equals(mRouteType)) {
            // DIRECT BIT-PERFECT
            if (mSampleRate >= 352800) activeSegments = 10;
            else if (mSampleRate >= 176400) activeSegments = 8;
            else if (mSampleRate >= 88200) activeSegments = 6;
            else if (mSampleRate >= 44100) activeSegments = 3;
            else activeSegments = 2;

            activeColor = Color.parseColor("#10B981");
            topIcon = "⚡";
            subtitleText = "AUDIO PIPELINE LIVE";
            statusText = "● 1:1 DIRECT BIT-PERFECT (ZERO SRC)";
        } else if ("MIXER".equals(mRouteType)) {
            // ANDROID MIXER 48k
            activeSegments = 3;
            activeColor = Color.parseColor("#F59E0B");
            topIcon = "🔊";
            subtitleText = "MIXER ANDROID AKTIF";
            statusText = "● 48 kHz RESAMPLE (MIXER)";
        } else {
            // SPEAKER OR OTHER
            activeSegments = 2;
            activeColor = Color.parseColor("#64748B");
            topIcon = "📱";
            subtitleText = "SPEAKER INTERNAL HP";
            statusText = "● AUDIO KE SPEAKER INTERNAL";
        }

        int inactiveColor = Color.parseColor("#1C2536");
        float segmentSweep = (TOTAL_SWEEP - (TOTAL_SEGMENTS - 1) * GAP_ANGLE) / TOTAL_SEGMENTS;

        // Draw segmented arch blocks
        for (int i = 0; i < TOTAL_SEGMENTS; i++) {
            float start = START_ANGLE + i * (segmentSweep + GAP_ANGLE);
            if (i < activeSegments) {
                mArcPaint.setColor(activeColor);
            } else {
                mArcPaint.setColor(inactiveColor);
            }
            canvas.drawArc(mArcRect, start, segmentSweep, false, mArcPaint);
        }

        // --- Center Content ---

        // 1. Top Icon & Pipeline Live Subtitle
        float topIconY = cy - radius * 0.44f;
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(activeColor);
        mTextPaint.setTextSize(13f * density);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText(topIcon, cx, topIconY, mTextPaint);

        mTextPaint.setColor(Color.parseColor("#8E9BB0"));
        mTextPaint.setTextSize(10.5f * density);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        mTextPaint.setLetterSpacing(0.06f);
        canvas.drawText(subtitleText, cx, topIconY + 14f * density, mTextPaint);

        // 2. GIGANTIC HERO LIVE RATE (Replacing kcal from mockup)
        String mainRate;
        if (!mIsDacConnected || mSampleRate <= 0) {
            mainRate = "--";
        } else if (mSampleRate % 1000 == 0) {
            mainRate = String.valueOf(mSampleRate / 1000) + ".0";
        } else {
            mainRate = String.format("%.1f", mSampleRate / 1000.0);
        }

        mTextPaint.setColor(Color.parseColor("#FFFFFF"));
        mTextPaint.setTextSize(38f * density);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        mTextPaint.setLetterSpacing(0.01f);

        float rateY = cy + 8f * density;
        canvas.drawText(mainRate + " kHz", cx, rateY, mTextPaint);

        // 3. Status Badge / Match Subtitle
        mTextPaint.setTextSize(10f * density);
        mTextPaint.setLetterSpacing(0.04f);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        mTextPaint.setColor(activeColor);

        float statusY = rateY + 18f * density;
        canvas.drawText(statusText, cx, statusY, mTextPaint);
    }
}
