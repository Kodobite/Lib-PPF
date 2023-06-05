package io.kodebite.ppf;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@SuppressWarnings("unused")
public class PatternLockView extends View {

    private static final String UTF8 = "UTF-8";
    private static final String SHA1 = "SHA-1";
    private static final String MD5 = "MD5";
    private static final int DEFAULT_PATTERN_DOT_COUNT = 3;
    private static final boolean PROFILE_DRAWING = false;

    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;
    private static final int DEFAULT_DOT_ANIMATION_DURATION = 190;
    private static final int DEFAULT_PATH_END_ANIMATION_DURATION = 100;
    private static final float DEFAULT_DRAG_THRESHOLD = 0.0f;
    private static final Random RANDOM = new Random();
    private static int sDotCount;
    private final Path mCurrentPath = new Path();
    private final Rect mInvalidate = new Rect();
    private final Rect mTempInvalidateRect = new Rect();
    private final float mHitFactor = 0.6f;
    private final List<PatternLockViewListener> mPatternListeners;
    private DotState[][] mDotStates;
    private int mPatternSize;
    private boolean mDrawingProfilingStarted = false;
    private long mAnimatingPeriodStart;
    private int mAspectRatio;
    private int mNormalStateColor;
    private int mWrongStateColor;
    private int mCorrectStateColor;
    private int mPathWidth;
    private int mDotNormalSize;
    private int mDotSelectedSize;
    private int mDotAnimationDuration;
    private int mPathEndAnimationDuration;
    private Paint mDotPaint;
    private Paint mPathPaint;
    private ArrayList<Dot> mPattern;

    private boolean[][] mPatternDrawLookup;
    private float mInProgressX = -1;
    private float mInProgressY = -1;
    private int mPatternViewMode = PatternViewMode.CORRECT;
    private boolean mInputEnabled = true;
    private boolean mInStealthMode = false;
    private boolean mEnableHapticFeedback = false;
    private boolean mPatternInProgress = false;
    private float mViewWidth;
    private float mViewHeight;
    private Interpolator mFastOutSlowInInterpolator;
    private Interpolator mLinearOutSlowInInterpolator;

    public PatternLockView(Context context) {
        this(context, null);
    }

    public PatternLockView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.PatternLockView);
        try {
            sDotCount = typedArray.getInt(R.styleable.PatternLockView_ppf_patternLength,
                    DEFAULT_PATTERN_DOT_COUNT);

            mAspectRatio = typedArray.getInt(R.styleable.PatternLockView_ppf_aspectRatio,
                    3);
            mPathWidth = (int) typedArray.getDimension(R.styleable.PatternLockView_ppf_lineWidth,
                    getDimensionInPx(getContext(), R.dimen.pattern_lock_path_width));
            mNormalStateColor = typedArray.getColor(R.styleable.PatternLockView_ppf_stateNormalColor,
                    getColor(getContext(), R.color.white));
            mCorrectStateColor = typedArray.getColor(R.styleable.PatternLockView_ppf_stateCorrectColor,
                    getColor(getContext(), R.color.white));
            mWrongStateColor = typedArray.getColor(R.styleable.PatternLockView_ppf_stateWrongColor,
                    getColor(getContext(), R.color.pomegranate));
            mDotNormalSize = (int) typedArray.getDimension(R.styleable.PatternLockView_ppf_dotNormalSize,
                    getDimensionInPx(getContext(), R.dimen.pattern_lock_dot_size));
            mDotSelectedSize = (int) typedArray.getDimension(R.styleable
                            .PatternLockView_ppf_dotSelectedSize,
                    getDimensionInPx(getContext(), R.dimen.pattern_lock_dot_selected_size));
            mDotAnimationDuration = typedArray.getInt(R.styleable.PatternLockView_ppf_dotAnimationDuration,
                    DEFAULT_DOT_ANIMATION_DURATION);
            mPathEndAnimationDuration = typedArray.getInt(R.styleable.PatternLockView_ppf_lineEndAnimationDuration,
                    DEFAULT_PATH_END_ANIMATION_DURATION);
        } finally {
            typedArray.recycle();
        }

        // The pattern will always be symmetrical
        mPatternSize = sDotCount * sDotCount;
        mPattern = new ArrayList<>(mPatternSize);
        mPatternDrawLookup = new boolean[sDotCount][sDotCount];

        mDotStates = new DotState[sDotCount][sDotCount];
        for (int i = 0; i < sDotCount; i++) {
            for (int j = 0; j < sDotCount; j++) {
                mDotStates[i][j] = new DotState();
                mDotStates[i][j].mSize = mDotNormalSize;
            }
        }

        mPatternListeners = new ArrayList<>();

        initView();
    }

    public static String patternToString(PatternLockView patternLockView,
                                         List<Dot> pattern) {
        if (pattern == null) {
            return "";
        }
        int patternSize = pattern.size();
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < patternSize; i++) {
            Dot dot = pattern.get(i);
            stringBuilder.append((dot.getRow() * patternLockView.getDotCount() + dot.getColumn()));
        }
        return stringBuilder.toString();
    }

    public static List<Dot> stringToPattern(PatternLockView patternLockView,
                                            String string) {
        List<Dot> result = new ArrayList<>();

        for (int i = 0; i < string.length(); i++) {
            int number = Character.getNumericValue(string.charAt(i));
            result.add(Dot.of(number / patternLockView.getDotCount(),
                    number % patternLockView.getDotCount()));
        }
        return result;
    }

    public static String patternToSha1(PatternLockView patternLockView,
                                       List<Dot> pattern) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(SHA1);
            messageDigest.update(patternToString(patternLockView, pattern).getBytes(StandardCharsets.UTF_8));

            byte[] digest = messageDigest.digest();
            BigInteger bigInteger = new BigInteger(1, digest);
            return String.format((Locale) null,
                    "%0" + (digest.length * 2) + "x", bigInteger).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }


    public static ArrayList<Dot> generateRandomPattern(PatternLockView patternLockView,
                                                       int size)
            throws IndexOutOfBoundsException {
        if (patternLockView == null) {
            throw new IllegalArgumentException("PatternLockView can not be null.");
        }

        if (size <= 0 || size > patternLockView.getDotCount()) {
            throw new IndexOutOfBoundsException("Size must be in range [1, " +
                    patternLockView.getDotCount() + "]");
        }

        List<Integer> usedIds = new ArrayList<>();
        int lastId = randInt(patternLockView.getDotCount());
        usedIds.add(lastId);

        while (usedIds.size() < size) {
            // We start from an empty matrix, so there's always a break point to
            // exit this loop
            final int lastRow = lastId / patternLockView.getDotCount();
            final int lastCol = lastId % patternLockView.getDotCount();

            // This is the max available rows/ columns that we can reach from
            // the cell of `lastId` to the border of the matrix.
            final int maxDistance = Math.max(
                    Math.max(lastRow, patternLockView.getDotCount() - lastRow),
                    Math.max(lastCol, patternLockView.getDotCount() - lastCol));

            lastId = -1;

            // Starting from `distance` = 1, find the closest-available
            // neighbour value of the cell [lastRow, lastCol].
            for (int distance = 1; distance <= maxDistance; distance++) {

                // Now we have a square surrounding the current cell. We call it
                // ABCD, in which A is top-left, and C is bottom-right.
                final int rowA = lastRow - distance;
                final int colA = lastCol - distance;
                final int rowC = lastRow + distance;
                final int colC = lastCol + distance;

                int[] randomValues;

                // Process randomly AB, BC, CD, and DA. Break the loop as soon
                // as we find one value.
                final int[] lines = randIntArray(4);
                for (int line : lines) {
                    switch (line) {
                        case 0: {
                            if (rowA >= 0) {
                                randomValues = randIntArray(Math.max(0, colA),
                                        Math.min(patternLockView.getDotCount(),
                                                colC + 1));
                                for (int c : randomValues) {
                                    lastId = rowA * patternLockView.getDotCount()
                                            + c;
                                    if (usedIds.contains(lastId))
                                        lastId = -1;
                                    else
                                        break;
                                }
                            }

                            break;
                        }

                        case 1: {
                            if (colC < patternLockView.getDotCount()) {
                                randomValues = randIntArray(Math.max(0, rowA + 1),
                                        Math.min(patternLockView.getDotCount(),
                                                rowC + 1));
                                for (int r : randomValues) {
                                    lastId = r * patternLockView.getDotCount()
                                            + colC;
                                    if (usedIds.contains(lastId))
                                        lastId = -1;
                                    else
                                        break;
                                }
                            }

                            break;
                        }

                        case 2: {
                            if (rowC < patternLockView.getDotCount()) {
                                randomValues = randIntArray(Math.max(0, colA),
                                        Math.min(patternLockView.getDotCount(),
                                                colC));
                                for (int c : randomValues) {
                                    lastId = rowC * patternLockView.getDotCount()
                                            + c;
                                    if (usedIds.contains(lastId))
                                        lastId = -1;
                                    else
                                        break;
                                }
                            }

                            break;
                        }

                        case 3: {
                            if (colA >= 0) {
                                randomValues = randIntArray(Math.max(0, rowA + 1),
                                        Math.min(patternLockView.getDotCount(),
                                                rowC));
                                for (int r : randomValues) {
                                    lastId = r * patternLockView.getDotCount()
                                            + colA;
                                    if (usedIds.contains(lastId))
                                        lastId = -1;
                                    else
                                        break;
                                }
                            }

                            break;
                        }
                    }

                    if (lastId >= 0) break;
                }

                if (lastId >= 0) break;
            }

            usedIds.add(lastId);
        }

        ArrayList<Dot> result = new ArrayList<>();
        for (int id : usedIds) {
            result.add(Dot.of(id));
        }

        return result;
    }

    public static int randInt() {
        return RANDOM.nextInt((int) (System.nanoTime() % Integer.MAX_VALUE));
    }

    public static int randInt(int max) {
        return max > 0 ? randInt() % max : 0;
    }

    public static int[] randIntArray(int start, int end) {
        if (end <= start) {
            return new int[0];
        }

        final List<Integer> values = new ArrayList<>();
        for (int i = start; i < end; i++) {
            values.add(i);
        }

        final int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) {
            int k = randInt(values.size());
            result[i] = values.get(k);
            values.remove(k);
        }

        return result;
    }

    public static int[] randIntArray(int end) {
        return randIntArray(0, end);
    }

    public static int getColor(@NonNull Context context, @ColorRes int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    public static String getString(@NonNull Context context, @StringRes int stringRes) {
        return context.getString(stringRes);
    }

    public static float getDimensionInPx(@NonNull Context context, @DimenRes int dimenRes) {
        return context.getResources().getDimension(dimenRes);
    }

    private void initView() {
        setClickable(true);

        mPathPaint = new Paint();
        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);
        mPathPaint.setColor(mNormalStateColor);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);
        mPathPaint.setStrokeWidth(mPathWidth);

        mDotPaint = new Paint();
        mDotPaint.setAntiAlias(true);
        mDotPaint.setDither(true);

        if (!isInEditMode()) {
            mFastOutSlowInInterpolator = AnimationUtils.loadInterpolator(
                    getContext(), android.R.interpolator.fast_out_slow_in);
            mLinearOutSlowInInterpolator = AnimationUtils.loadInterpolator(
                    getContext(), android.R.interpolator.linear_out_slow_in);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mAspectRatio == 3) {
            return;
        }

        int oldWidth = resolveMeasured(widthMeasureSpec, getSuggestedMinimumWidth());
        int oldHeight = resolveMeasured(heightMeasureSpec, getSuggestedMinimumHeight());

        int newWidth;
        int newHeight;
        switch (mAspectRatio) {
            case AspectRatio.ASPECT_RATIO_SQUARE:
                newWidth = newHeight = Math.min(oldWidth, oldHeight);
                break;
            case AspectRatio.ASPECT_RATIO_MATCH_WIDTH:
                newWidth = oldWidth;
                newHeight = Math.min(oldWidth, oldHeight);
                break;

            case AspectRatio.ASPECT_RATIO_MATCH_HEIGHT:
                newWidth = Math.min(oldWidth, oldHeight);
                newHeight = oldHeight;
                break;

            default:
                throw new IllegalStateException("Unknown aspect ratio");
        }
        setMeasuredDimension(newWidth, newHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ArrayList<Dot> pattern = mPattern;
        int patternSize = pattern.size();
        boolean[][] drawLookupTable = mPatternDrawLookup;

        if (mPatternViewMode == PatternViewMode.AUTO_DRAW) {
            int oneCycle = (patternSize + 1) * MILLIS_PER_CIRCLE_ANIMATING;
            int spotInCycle = (int) (SystemClock.elapsedRealtime() - mAnimatingPeriodStart)
                    % oneCycle;
            int numCircles = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING;

            clearPatternDrawLookup();
            for (int i = 0; i < numCircles; i++) {
                Dot dot = pattern.get(i);
                drawLookupTable[dot.mRow][dot.mColumn] = true;
            }

            boolean needToUpdateInProgressPoint = numCircles > 0
                    && numCircles < patternSize;

            if (needToUpdateInProgressPoint) {
                float percentageOfNextCircle = ((float) (spotInCycle % MILLIS_PER_CIRCLE_ANIMATING))
                        / MILLIS_PER_CIRCLE_ANIMATING;

                Dot currentDot = pattern.get(numCircles - 1);
                float centerX = getCenterXForColumn(currentDot.mColumn);
                float centerY = getCenterYForRow(currentDot.mRow);

                Dot nextDot = pattern.get(numCircles);
                float dx = percentageOfNextCircle
                        * (getCenterXForColumn(nextDot.mColumn) - centerX);
                float dy = percentageOfNextCircle
                        * (getCenterYForRow(nextDot.mRow) - centerY);
                mInProgressX = centerX + dx;
                mInProgressY = centerY + dy;
            }
            invalidate();
        }

        Path currentPath = mCurrentPath;
        currentPath.rewind();

        // Draw the dots
        for (int i = 0; i < sDotCount; i++) {
            float centerY = getCenterYForRow(i);
            for (int j = 0; j < sDotCount; j++) {
                DotState dotState = mDotStates[i][j];
                float centerX = getCenterXForColumn(j);
                float size = dotState.mSize * dotState.mScale;
                float translationY = dotState.mTranslateY;
                drawCircle(canvas, (int) centerX, (int) centerY + translationY,
                        size, drawLookupTable[i][j], dotState.mAlpha);
            }
        }

        // Draw the path of the pattern (unless we are in stealth mode)
        boolean drawPath = !mInStealthMode;
        if (drawPath) {
            mPathPaint.setColor(getCurrentColor(true));

            boolean anyCircles = false;
            float lastX = 0f;
            float lastY = 0f;
            for (int i = 0; i < patternSize; i++) {
                Dot dot = pattern.get(i);

                // Only draw the part of the pattern stored in
                // the lookup table (this is only different in case
                // of animation)
                if (!drawLookupTable[dot.mRow][dot.mColumn]) {
                    break;
                }
                anyCircles = true;

                float centerX = getCenterXForColumn(dot.mColumn);
                float centerY = getCenterYForRow(dot.mRow);
                if (i != 0) {
                    DotState state = mDotStates[dot.mRow][dot.mColumn];
                    currentPath.rewind();
                    currentPath.moveTo(lastX, lastY);
                    if (state.mLineEndX != Float.MIN_VALUE
                            && state.mLineEndY != Float.MIN_VALUE) {
                        currentPath.lineTo(state.mLineEndX, state.mLineEndY);
                    } else {
                        currentPath.lineTo(centerX, centerY);
                    }
                    canvas.drawPath(currentPath, mPathPaint);
                }
                lastX = centerX;
                lastY = centerY;
            }

            // Draw last in progress section
            if ((mPatternInProgress || mPatternViewMode == PatternViewMode.AUTO_DRAW)
                    && anyCircles) {
                currentPath.rewind();
                currentPath.moveTo(lastX, lastY);
                currentPath.lineTo(mInProgressX, mInProgressY);

                mPathPaint.setAlpha((int) (calculateLastSegmentAlpha(
                        mInProgressX, mInProgressY, lastX, lastY) * 255f));
                canvas.drawPath(currentPath, mPathPaint);
            }
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        int adjustedWidth = width - getPaddingLeft() - getPaddingRight();
        mViewWidth = adjustedWidth / (float) sDotCount;

        int adjustedHeight = height - getPaddingTop() - getPaddingBottom();
        mViewHeight = adjustedHeight / (float) sDotCount;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                patternToString(this, mPattern),
                mPatternViewMode, mInputEnabled, mInStealthMode,
                mEnableHapticFeedback);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        setPattern(PatternViewMode.CORRECT,
                stringToPattern(this, savedState.getSerializedPattern()));
        mPatternViewMode = savedState.getDisplayMode();
        mInputEnabled = savedState.isInputEnabled();
        mInStealthMode = savedState.isInStealthMode();
        mEnableHapticFeedback = savedState.isTactileFeedbackEnabled();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (((AccessibilityManager) getContext().getSystemService(
                Context.ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled()) {
            final int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    event.setAction(MotionEvent.ACTION_DOWN);
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    event.setAction(MotionEvent.ACTION_MOVE);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    event.setAction(MotionEvent.ACTION_UP);
                    break;
            }
            onTouchEvent(event);
            event.setAction(action);
        }
        return super.onHoverEvent(event);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(event);
                return true;
            case MotionEvent.ACTION_UP:
                handleActionUp(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                mPatternInProgress = false;
                resetPattern();
                notifyPatternCleared();

                if (PROFILE_DRAWING) {
                    if (mDrawingProfilingStarted) {
                        Debug.stopMethodTracing();
                        mDrawingProfilingStarted = false;
                    }
                }
                return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<Dot> getPattern() {
        return (List<Dot>) mPattern.clone();
    }

    @PatternViewMode
    public int getPatternViewMode() {
        return mPatternViewMode;
    }

    public boolean isInStealthMode() {
        return mInStealthMode;
    }

    public void setInStealthMode(boolean inStealthMode) {
        mInStealthMode = inStealthMode;
    }

    public boolean isTactileFeedbackEnabled() {
        return mEnableHapticFeedback;
    }

    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mEnableHapticFeedback = tactileFeedbackEnabled;
    }

    public boolean isInputEnabled() {
        return mInputEnabled;
    }


    public void setInputEnabled(boolean inputEnabled) {
        mInputEnabled = inputEnabled;
    }

    public int getDotCount() {
        return sDotCount;
    }

    public void setDotCount(int dotCount) {
        sDotCount = dotCount;
        mPatternSize = sDotCount * sDotCount;
        mPattern = new ArrayList<>(mPatternSize);
        mPatternDrawLookup = new boolean[sDotCount][sDotCount];

        mDotStates = new DotState[sDotCount][sDotCount];
        for (int i = 0; i < sDotCount; i++) {
            for (int j = 0; j < sDotCount; j++) {
                mDotStates[i][j] = new DotState();
                mDotStates[i][j].mSize = mDotNormalSize;
            }
        }

        requestLayout();
        invalidate();
    }


    @AspectRatio
    public int getAspectRatio() {
        return mAspectRatio;
    }

    public void setAspectRatio(@AspectRatio int aspectRatio) {
        mAspectRatio = aspectRatio;
        requestLayout();
    }

    public int getNormalStateColor() {
        return mNormalStateColor;
    }

    public void setNormalStateColor(@ColorInt int normalStateColor) {
        mNormalStateColor = normalStateColor;
    }

    public int getWrongStateColor() {
        return mWrongStateColor;
    }

    public void setWrongStateColor(@ColorInt int wrongStateColor) {
        mWrongStateColor = wrongStateColor;
    }

    public int getCorrectStateColor() {
        return mCorrectStateColor;
    }

    public void setCorrectStateColor(@ColorInt int correctStateColor) {
        mCorrectStateColor = correctStateColor;
    }

    public int getPathWidth() {
        return mPathWidth;
    }

    public void setPathWidth(@Dimension int pathWidth) {
        mPathWidth = pathWidth;

        initView();
        invalidate();
    }

    public int getDotNormalSize() {
        return mDotNormalSize;
    }

    public void setDotNormalSize(@Dimension int dotNormalSize) {
        mDotNormalSize = dotNormalSize;

        for (int i = 0; i < sDotCount; i++) {
            for (int j = 0; j < sDotCount; j++) {
                mDotStates[i][j] = new DotState();
                mDotStates[i][j].mSize = mDotNormalSize;
            }
        }

        invalidate();
    }

    public int getDotSelectedSize() {
        return mDotSelectedSize;
    }

    public void setDotSelectedSize(@Dimension int dotSelectedSize) {
        mDotSelectedSize = dotSelectedSize;
    }

    public int getPatternSize() {
        return mPatternSize;
    }

    public int getDotAnimationDuration() {
        return mDotAnimationDuration;
    }

    public void setDotAnimationDuration(int dotAnimationDuration) {
        mDotAnimationDuration = dotAnimationDuration;
        invalidate();
    }

    public int getPathEndAnimationDuration() {
        return mPathEndAnimationDuration;
    }

    public void setPathEndAnimationDuration(int pathEndAnimationDuration) {
        mPathEndAnimationDuration = pathEndAnimationDuration;
    }

    public void setPattern(@PatternViewMode int patternViewMode, List<Dot> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        clearPatternDrawLookup();
        for (Dot dot : pattern) {
            mPatternDrawLookup[dot.mRow][dot.mColumn] = true;
        }
        setViewMode(patternViewMode);
    }


    public void setViewMode(@PatternViewMode int patternViewMode) {
        mPatternViewMode = patternViewMode;
        if (patternViewMode == PatternViewMode.AUTO_DRAW) {
            if (mPattern.size() == 0) {
                throw new IllegalStateException(
                        "you must have a pattern to "
                                + "animate if you want to set the display mode to animate");
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime();
            final Dot first = mPattern.get(0);
            mInProgressX = getCenterXForColumn(first.mColumn);
            mInProgressY = getCenterYForRow(first.mRow);
            clearPatternDrawLookup();
        }
        invalidate();
    }

    public void setEnableHapticFeedback(boolean enableHapticFeedback) {
        mEnableHapticFeedback = enableHapticFeedback;
    }

    public void addPatternLockListener(PatternLockViewListener patternListener) {
        mPatternListeners.add(patternListener);
    }

    public void removePatternLockListener(PatternLockViewListener patternListener) {
        mPatternListeners.remove(patternListener);
    }

    public void clearPattern() {
        resetPattern();
    }

    private int resolveMeasured(int measureSpec, int desired) {
        int result;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                result = desired;
                break;
            case MeasureSpec.AT_MOST:
                result = Math.max(specSize, desired);
                break;
            case MeasureSpec.EXACTLY:
            default:
                result = specSize;
        }
        return result;
    }

    private void notifyPatternProgress() {
        sendAccessEvent(R.string.message_pattern_dot_added);
        notifyListenersProgress(mPattern);
    }

    private void notifyPatternStarted() {
        sendAccessEvent(R.string.message_pattern_started);
        notifyListenersStarted();
    }

    private void notifyPatternDetected() {
        sendAccessEvent(R.string.message_pattern_detected);
        notifyListenersComplete(mPattern);
    }

    private void notifyPatternCleared() {
        sendAccessEvent(R.string.message_pattern_cleared);
        notifyListenersCleared();
    }

    private void resetPattern() {
        mPattern.clear();
        clearPatternDrawLookup();
        mPatternViewMode = PatternViewMode.CORRECT;
        invalidate();
    }

    private void notifyListenersStarted() {
        for (PatternLockViewListener patternListener : mPatternListeners) {
            if (patternListener != null) {
                patternListener.onStarted();
            }
        }
    }

    private void notifyListenersProgress(List<Dot> pattern) {
        for (PatternLockViewListener patternListener : mPatternListeners) {
            if (patternListener != null) {
                patternListener.onProgress(pattern);
            }
        }
    }

    private void notifyListenersComplete(List<Dot> pattern) {
        for (PatternLockViewListener patternListener : mPatternListeners) {
            if (patternListener != null) {
                patternListener.onComplete(pattern);
            }
        }
    }

    private void notifyListenersCleared() {
        for (PatternLockViewListener patternListener : mPatternListeners) {
            if (patternListener != null) {
                patternListener.onCleared();
            }
        }
    }

    private void clearPatternDrawLookup() {
        for (int i = 0; i < sDotCount; i++) {
            for (int j = 0; j < sDotCount; j++) {
                mPatternDrawLookup[i][j] = false;
            }
        }
    }


    private Dot detectAndAddHit(float x, float y) {
        final Dot dot = checkForNewHit(x, y);
        if (dot != null) {
            // Check for gaps in existing pattern
            Dot fillInGapDot = null;
            final ArrayList<Dot> pattern = mPattern;
            if (!pattern.isEmpty()) {
                Dot lastDot = pattern.get(pattern.size() - 1);
                int dRow = dot.mRow - lastDot.mRow;
                int dColumn = dot.mColumn - lastDot.mColumn;

                int fillInRow = lastDot.mRow;
                int fillInColumn = lastDot.mColumn;

                if (Math.abs(dRow) == 2 && Math.abs(dColumn) != 1) {
                    fillInRow = lastDot.mRow + ((dRow > 0) ? 1 : -1);
                }

                if (Math.abs(dColumn) == 2 && Math.abs(dRow) != 1) {
                    fillInColumn = lastDot.mColumn + ((dColumn > 0) ? 1 : -1);
                }

                fillInGapDot = Dot.of(fillInRow, fillInColumn);
            }

            if (fillInGapDot != null
                    && !mPatternDrawLookup[fillInGapDot.mRow][fillInGapDot.mColumn]) {
                addCellToPattern(fillInGapDot);
            }
            addCellToPattern(dot);
            if (mEnableHapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            return dot;
        }
        return null;
    }

    private void addCellToPattern(Dot newDot) {
        mPatternDrawLookup[newDot.mRow][newDot.mColumn] = true;
        mPattern.add(newDot);
        if (!mInStealthMode) {
            startDotSelectedAnimation(newDot);
        }
        notifyPatternProgress();
    }

    private void startDotSelectedAnimation(Dot dot) {
        final DotState dotState = mDotStates[dot.mRow][dot.mColumn];
        startSizeAnimation(mDotNormalSize, mDotSelectedSize, mDotAnimationDuration,
                mLinearOutSlowInInterpolator, dotState, () -> startSizeAnimation(mDotSelectedSize, mDotNormalSize, mDotAnimationDuration,
                        mFastOutSlowInInterpolator, dotState, null));
        startLineEndAnimation(dotState, mInProgressX, mInProgressY,
                getCenterXForColumn(dot.mColumn), getCenterYForRow(dot.mRow));
    }

    private void startLineEndAnimation(final DotState state,
                                       final float startX, final float startY, final float targetX,
                                       final float targetY) {
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator.addUpdateListener(animation -> {
            float t = (Float) animation.getAnimatedValue();
            state.mLineEndX = (1 - t) * startX + t * targetX;
            state.mLineEndY = (1 - t) * startY + t * targetY;
            invalidate();
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                state.mLineAnimator = null;
            }

        });
        valueAnimator.setInterpolator(mFastOutSlowInInterpolator);
        valueAnimator.setDuration(mPathEndAnimationDuration);
        valueAnimator.start();
        state.mLineAnimator = valueAnimator;
    }

    private void startSizeAnimation(float start, float end, long duration,
                                    Interpolator interpolator, final DotState state,
                                    final Runnable endRunnable) {
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(start, end);
        valueAnimator.addUpdateListener(animation -> {
            state.mSize = (Float) animation.getAnimatedValue();
            invalidate();
        });
        if (endRunnable != null) {
            valueAnimator.addListener(new AnimatorListenerAdapter() {

                @Override
                public void onAnimationEnd(Animator animation) {
                    endRunnable.run();
                }
            });
        }
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }


    private Dot checkForNewHit(float x, float y) {
        final int rowHit = getRowHit(y);
        if (rowHit < 0) {
            return null;
        }
        final int columnHit = getColumnHit(x);
        if (columnHit < 0) {
            return null;
        }

        if (mPatternDrawLookup[rowHit][columnHit]) {
            return null;
        }
        return Dot.of(rowHit, columnHit);
    }


    private int getRowHit(float y) {
        final float squareHeight = mViewHeight;
        float hitSize = squareHeight * mHitFactor;

        float offset = getPaddingTop() + (squareHeight - hitSize) / 2f;
        for (int i = 0; i < sDotCount; i++) {
            float hitTop = offset + squareHeight * i;
            if (y >= hitTop && y <= hitTop + hitSize) {
                return i;
            }
        }
        return -1;
    }


    private int getColumnHit(float x) {
        final float squareWidth = mViewWidth;
        float hitSize = squareWidth * mHitFactor;

        float offset = getPaddingLeft() + (squareWidth - hitSize) / 2f;
        for (int i = 0; i < sDotCount; i++) {

            final float hitLeft = offset + squareWidth * i;
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i;
            }
        }
        return -1;
    }

    private void handleActionMove(MotionEvent event) {
        float radius = mPathWidth;
        int historySize = event.getHistorySize();
        mTempInvalidateRect.setEmpty();
        boolean invalidateNow = false;
        for (int i = 0; i < historySize + 1; i++) {
            float x = i < historySize ? event.getHistoricalX(i) : event
                    .getX();
            float y = i < historySize ? event.getHistoricalY(i) : event
                    .getY();
            Dot hitDot = detectAndAddHit(x, y);
            int patternSize = mPattern.size();
            if (hitDot != null && patternSize == 1) {
                mPatternInProgress = true;
                notifyPatternStarted();
            }
            // Note current x and y for rubber banding of in progress patterns
            float dx = Math.abs(x - mInProgressX);
            float dy = Math.abs(y - mInProgressY);
            if (dx > DEFAULT_DRAG_THRESHOLD || dy > DEFAULT_DRAG_THRESHOLD) {
                invalidateNow = true;
            }

            if (mPatternInProgress && patternSize > 0) {
                final ArrayList<Dot> pattern = mPattern;
                final Dot lastDot = pattern.get(patternSize - 1);
                float lastCellCenterX = getCenterXForColumn(lastDot.mColumn);
                float lastCellCenterY = getCenterYForRow(lastDot.mRow);

                // Adjust for drawn segment from last cell to (x,y). Radius
                // accounts for line width.
                float left = Math.min(lastCellCenterX, x) - radius;
                float right = Math.max(lastCellCenterX, x) + radius;
                float top = Math.min(lastCellCenterY, y) - radius;
                float bottom = Math.max(lastCellCenterY, y) + radius;

                // Invalidate between the pattern's new cell and the pattern's
                // previous cell
                if (hitDot != null) {
                    float width = mViewWidth * 0.5f;
                    float height = mViewHeight * 0.5f;
                    float hitCellCenterX = getCenterXForColumn(hitDot.mColumn);
                    float hitCellCenterY = getCenterYForRow(hitDot.mRow);

                    left = Math.min(hitCellCenterX - width, left);
                    right = Math.max(hitCellCenterX + width, right);
                    top = Math.min(hitCellCenterY - height, top);
                    bottom = Math.max(hitCellCenterY + height, bottom);
                }

                // Invalidate between the pattern's last cell and the previous
                // location
                mTempInvalidateRect.union(Math.round(left), Math.round(top),
                        Math.round(right), Math.round(bottom));
            }
        }
        mInProgressX = event.getX();
        mInProgressY = event.getY();

        // To save updates, we only invalidate if the user moved beyond a
        // certain amount.
        if (invalidateNow) {
            mInvalidate.union(mTempInvalidateRect);
            invalidate(mInvalidate);
            mInvalidate.set(mTempInvalidateRect);
        }
    }

    private void sendAccessEvent(int resId) {
        announceForAccessibility(getContext().getString(resId));
    }

    private void handleActionUp(MotionEvent event) {
        // Report pattern detected
        if (!mPattern.isEmpty()) {
            mPatternInProgress = false;
            cancelLineAnimations();
            notifyPatternDetected();
            invalidate();
        }
        if (PROFILE_DRAWING) {
            if (mDrawingProfilingStarted) {
                Debug.stopMethodTracing();
                mDrawingProfilingStarted = false;
            }
        }
    }

    private void cancelLineAnimations() {
        for (int i = 0; i < sDotCount; i++) {
            for (int j = 0; j < sDotCount; j++) {
                DotState state = mDotStates[i][j];
                if (state.mLineAnimator != null) {
                    state.mLineAnimator.cancel();
                    state.mLineEndX = Float.MIN_VALUE;
                    state.mLineEndY = Float.MIN_VALUE;
                }
            }
        }
    }

    private void handleActionDown(MotionEvent event) {
        resetPattern();
        float x = event.getX();
        float y = event.getY();
        Dot hitDot = detectAndAddHit(x, y);
        if (hitDot != null) {
            mPatternInProgress = true;
            mPatternViewMode = PatternViewMode.CORRECT;
            notifyPatternStarted();
        } else {
            mPatternInProgress = false;
            notifyPatternCleared();
        }
        if (hitDot != null) {
            float startX = getCenterXForColumn(hitDot.mColumn);
            float startY = getCenterYForRow(hitDot.mRow);

            float widthOffset = mViewWidth / 2f;
            float heightOffset = mViewHeight / 2f;

            invalidate((int) (startX - widthOffset),
                    (int) (startY - heightOffset),
                    (int) (startX + widthOffset), (int) (startY + heightOffset));
        }
        mInProgressX = x;
        mInProgressY = y;
        if (PROFILE_DRAWING) {
            if (!mDrawingProfilingStarted) {
                Debug.startMethodTracing("PatternLockDrawing");
                mDrawingProfilingStarted = true;
            }
        }
    }

    private float getCenterXForColumn(int column) {
        return getPaddingLeft() + column * mViewWidth + mViewWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return getPaddingTop() + row * mViewHeight + mViewHeight / 2f;
    }

    private float calculateLastSegmentAlpha(float x, float y, float lastX,
                                            float lastY) {
        float diffX = x - lastX;
        float diffY = y - lastY;
        float dist = (float) Math.sqrt(diffX * diffX + diffY * diffY);
        float fraction = dist / mViewWidth;
        return Math.min(1f, Math.max(0f, (fraction - 0.3f) * 4f));
    }

    private int getCurrentColor(boolean partOfPattern) {
        if (!partOfPattern || mInStealthMode || mPatternInProgress) {
            return mNormalStateColor;
        } else if (mPatternViewMode == PatternViewMode.WRONG) {
            return mWrongStateColor;
        } else if (mPatternViewMode == PatternViewMode.CORRECT
                || mPatternViewMode == PatternViewMode.AUTO_DRAW) {
            return mCorrectStateColor;
        } else {
            throw new IllegalStateException("Unknown view mode " + mPatternViewMode);
        }
    }

    private void drawCircle(Canvas canvas, float centerX, float centerY,
                            float size, boolean partOfPattern, float alpha) {
        mDotPaint.setColor(getCurrentColor(partOfPattern));
        mDotPaint.setAlpha((int) (alpha * 255));
        canvas.drawCircle(centerX, centerY, size / 2, mDotPaint);
    }


    @IntDef({AspectRatio.ASPECT_RATIO_SQUARE, AspectRatio.ASPECT_RATIO_MATCH_WIDTH, AspectRatio.ASPECT_RATIO_MATCH_HEIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AspectRatio {
        int ASPECT_RATIO_SQUARE = 0;
        int ASPECT_RATIO_MATCH_WIDTH = 1;
        int ASPECT_RATIO_MATCH_HEIGHT = 2;
    }


    @IntDef({PatternViewMode.CORRECT, PatternViewMode.AUTO_DRAW, PatternViewMode.WRONG})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PatternViewMode {

        int CORRECT = 0;
        int AUTO_DRAW = 1;
        int WRONG = 2;
    }

    public interface PatternLockViewListener {


        void onStarted();


        void onProgress(List<Dot> progressPattern);


        void onComplete(List<Dot> pattern);


        void onCleared();
    }


    public static class Dot implements Parcelable {

        public static final Creator<Dot> CREATOR = new Creator<Dot>() {

            public Dot createFromParcel(Parcel in) {
                return new Dot(in);
            }

            public Dot[] newArray(int size) {
                return new Dot[size];
            }
        };
        private static final Dot[][] sDots;

        static {
            sDots = new Dot[sDotCount][sDotCount];

            // Initializing the dots
            for (int i = 0; i < sDotCount; i++) {
                for (int j = 0; j < sDotCount; j++) {
                    sDots[i][j] = new Dot(i, j);
                }
            }
        }

        private final int mRow;
        private final int mColumn;

        private Dot(int row, int column) {
            checkRange(row, column);
            this.mRow = row;
            this.mColumn = column;
        }

        private Dot(Parcel in) {
            mColumn = in.readInt();
            mRow = in.readInt();
        }


        public static synchronized Dot of(int row, int column) {
            checkRange(row, column);
            return sDots[row][column];
        }


        public static synchronized Dot of(int id) {
            return of(id / sDotCount, id % sDotCount);
        }

        private static void checkRange(int row, int column) {
            if (row < 0 || row > sDotCount - 1) {
                throw new IllegalArgumentException("mRow must be in range 0-"
                        + (sDotCount - 1));
            }
            if (column < 0 || column > sDotCount - 1) {
                throw new IllegalArgumentException("mColumn must be in range 0-"
                        + (sDotCount - 1));
            }
        }


        public int getId() {
            return mRow * sDotCount + mColumn;
        }

        public int getRow() {
            return mRow;
        }

        public int getColumn() {
            return mColumn;
        }

        @NonNull
        @Override
        public String toString() {
            return "(Row = " + mRow + ", Col = " + mColumn + ")";
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Dot)
                return mColumn == ((Dot) object).mColumn
                        && mRow == ((Dot) object).mRow;
            return super.equals(object);
        }

        @Override
        public int hashCode() {
            int result = mRow;
            result = 31 * result + mColumn;
            return result;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mColumn);
            dest.writeInt(mRow);
        }
    }


    private static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        private final String mSerializedPattern;
        private final int mDisplayMode;
        private final boolean mInputEnabled;
        private final boolean mInStealthMode;
        private final boolean mTactileFeedbackEnabled;


        private SavedState(Parcelable superState, String serializedPattern,
                           int displayMode, boolean inputEnabled, boolean inStealthMode,
                           boolean tactileFeedbackEnabled) {
            super(superState);

            mSerializedPattern = serializedPattern;
            mDisplayMode = displayMode;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mTactileFeedbackEnabled = tactileFeedbackEnabled;
        }


        private SavedState(Parcel in) {
            super(in);

            mSerializedPattern = in.readString();
            mDisplayMode = in.readInt();
            mInputEnabled = (Boolean) in.readValue(getClass().getClassLoader());
            mInStealthMode = (Boolean) in.readValue(getClass().getClassLoader());
            mTactileFeedbackEnabled = (Boolean) in.readValue(getClass().getClassLoader());
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isTactileFeedbackEnabled() {
            return mTactileFeedbackEnabled;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mDisplayMode);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mTactileFeedbackEnabled);
        }
    }

    public static class DotState {
        float mScale = 1.0f;
        float mTranslateY = 0.0f;
        float mAlpha = 1.0f;
        float mSize;
        float mLineEndX = Float.MIN_VALUE;
        float mLineEndY = Float.MIN_VALUE;
        ValueAnimator mLineAnimator;
    }

}
