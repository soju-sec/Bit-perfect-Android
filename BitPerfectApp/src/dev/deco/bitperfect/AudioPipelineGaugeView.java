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
    private int mSampleRate = 96000;
    private String mBitDepth = "24-bit";
    private boolean mIsBitPerfect = true;
    private String mRouteType = "DIRECT_PCM";
    private String mActiveApp = "Apple Music";
    private String mDacName = "Kiwi Ears AD1";

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

    public void updateStatus(int sampleRate, String bitDepth, boolean isBitPerfect, String routeType, String activeApp, String dacName) {
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
        int h = (int) (w * 0.74f); // Refined aspect ratio for the arch
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

        // Segment illumination based on sample rate
        int activeSegments;
        if (mSampleRate >= 352800) {
            activeSegments = 10;
        } else if (mSampleRate >= 176400) {
            activeSegments = 8;
        } else if (mSampleRate >= 88200) {
            activeSegments = 6;
        } else if (mSampleRate >= 44100) {
            activeSegments = 3;
        } else {
            activeSegments = 1;
        }

        int activeColor;
        if (mIsBitPerfect) {
            activeColor = Color.parseColor("#10B981"); // Vibrant Emerald
        } else if ("MIXER".equals(mRouteType)) {
            activeColor = Color.parseColor("#F59E0B"); // Warm Amber
        } else {
            activeColor = Color.parseColor("#475569"); // Slate
        }
        int inactiveColor = Color.parseColor("#1C2536"); // Distinct soft outline segment

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

        // --- Center Content (Like 1250 kcal in the mockup) ---

        // 1. Top Icon & Pipeline Live Subtitle
        float topIconY = cy - radius * 0.44f;
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(activeColor);
        mTextPaint.setTextSize(13f * density);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText("⚡", cx, topIconY, mTextPaint);

        mTextPaint.setColor(Color.parseColor("#8E9BB0"));
        mTextPaint.setTextSize(10.5f * density);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        mTextPaint.setLetterSpacing(0.06f);
        canvas.drawText("AUDIO PIPELINE LIVE", cx, topIconY + 14f * density, mTextPaint);

        // 2. GIGANTIC HERO LIVE RATE (Replacing kcal from mockup)
        String mainRate;
        if (mSampleRate <= 0) {
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

        // 3. Status Badge / Match Subtitle (Like Goal 2000 kcal)
        mTextPaint.setTextSize(10f * density);
        mTextPaint.setLetterSpacing(0.04f);
        mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        float statusY = rateY + 18f * density;
        if (mIsBitPerfect) {
            mTextPaint.setColor(Color.parseColor("#34D399")); // Emerald
            canvas.drawText("● 1:1 DIRECT BIT-PERFECT (ZERO SRC)", cx, statusY, mTextPaint);
        } else if ("MIXER".equals(mRouteType)) {
            mTextPaint.setColor(Color.parseColor("#FBBF24")); // Amber
            canvas.drawText("● MIXER ANDROID (RESAMPLED 48 kHz)", cx, statusY, mTextPaint);
        } else {
            mTextPaint.setColor(Color.parseColor("#64748B"));
            canvas.drawText("○ STANDBY • MENUNGGU AUDIO", cx, statusY, mTextPaint);
        }
    }
}
