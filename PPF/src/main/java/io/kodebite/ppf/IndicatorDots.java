package io.kodebite.ppf;

import static io.kodebite.ppf.PinLockView.getDimensionInPx;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.IntDef;
import androidx.core.view.ViewCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public class IndicatorDots extends LinearLayout {

    private static final int DEFAULT_PIN_LENGTH = 4;
    private final int mDiameter;
    private final int mPadding;
    private final int mFillDrawable;
    private final int mEmptyDrawable;
    private final int mDotColorNormal;
    private final int mDotColorFilled;
    private int mCount;
    private int mType;
    private int mPreviousCount;

    public IndicatorDots(Context context) {
        this(context, null);
    }

    public IndicatorDots(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IndicatorDots(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.IndicatorDots);

        try {
            mDiameter = (int) typedArray.getDimension(R.styleable.IndicatorDots_ppf_diameter, getDimensionInPx(getContext(), R.dimen.default_dot_diameter));
            mPadding = (int) typedArray.getDimension(R.styleable.IndicatorDots_ppf_padding, getDimensionInPx(getContext(), R.dimen.default_dot_spacing));
            mFillDrawable = typedArray.getResourceId(R.styleable.IndicatorDots_ppf_dotDrawableFilled,
                    R.drawable.dot_filled);
            mEmptyDrawable = typedArray.getResourceId(R.styleable.IndicatorDots_ppf_dotDrawableNormal,
                    R.drawable.dot_empty);
            mCount = typedArray.getInt(R.styleable.IndicatorDots_ppf_count, DEFAULT_PIN_LENGTH);
            mType = typedArray.getInt(R.styleable.IndicatorDots_ppf_type,
                    IndicatorType.FIXED);

            mDotColorNormal = typedArray.getColor(R.styleable.IndicatorDots_ppf_dotColorNormal, 0);
            mDotColorFilled = typedArray.getColor(R.styleable.IndicatorDots_ppf_dotColorFilled, 0);
        } finally {
            typedArray.recycle();
        }

        initView(context);
    }

    private void initView(Context context) {
        ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_LTR);
        if (mType == 0) {
            for (int i = 0; i < mCount; i++) {
                View dot = new View(context);
                emptyDot(dot);

                LayoutParams params = new LayoutParams(mDiameter,
                        mDiameter);
                params.setMargins(mPadding, 0, mPadding, 0);
                dot.setLayoutParams(params);

                addView(dot);
            }
        } else if (mType == 2) {
            setLayoutTransition(new LayoutTransition());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // If the indicator type is not fixed
        if (mType != 0) {
            ViewGroup.LayoutParams params = this.getLayoutParams();
            params.height = mDiameter;
            requestLayout();
        }
    }

    void updateDot(int length) {
        if (mType == 0) {
            if (length > 0) {
                if (length > mPreviousCount) {
                    fillDot(getChildAt(length - 1));
                } else {
                    emptyDot(getChildAt(length));
                }
                mPreviousCount = length;
            } else {
                // When {@code mPinLength} is 0, we need to reset all the views back to empty
                for (int i = 0; i < getChildCount(); i++) {
                    View v = getChildAt(i);
                    emptyDot(v);
                }
                mPreviousCount = 0;
            }
        } else {
            if (length > 0) {
                if (length > mPreviousCount) {
                    View dot = new View(getContext());
                    fillDot(dot);

                    LayoutParams params = new LayoutParams(mDiameter,
                            mDiameter);
                    params.setMargins(mPadding, 0, mPadding, 0);
                    dot.setLayoutParams(params);

                    addView(dot, length - 1);
                } else {
                    removeViewAt(length);
                }
                mPreviousCount = length;
            } else {
                removeAllViews();
                mPreviousCount = 0;
            }
        }
    }

    private void emptyDot(View dot) {
        dot.setBackgroundResource(mEmptyDrawable);
        // set color filter for view

        if (mDotColorNormal != 0) {
            dot.getBackground().setColorFilter(mDotColorNormal, android.graphics.PorterDuff.Mode.SRC_IN);
        }


    }

    private void fillDot(View dot) {
        dot.setBackgroundResource(mFillDrawable);
        // set color filter for view

        if (mDotColorFilled != 0) {
            dot.getBackground().setColorFilter(mDotColorFilled, android.graphics.PorterDuff.Mode.SRC_IN);
        }

    }


    public int getCount() {
        return mCount;
    }

    public void setCount(int pinLength) {
        this.mCount = pinLength;
        removeAllViews();
        initView(getContext());
    }

    public
    @IndicatorType
    int getType() {
        return mType;
    }

    public void setType(@IndicatorType int type) {
        this.mType = type;
        removeAllViews();
        initView(getContext());
    }

    @IntDef({IndicatorType.FIXED, IndicatorType.FILL, IndicatorType.FILL_WITH_ANIMATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface IndicatorType {
        int FIXED = 0;
        int FILL = 1;
        int FILL_WITH_ANIMATION = 2;
    }
}
