package fr.neamar.kiss.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import fr.neamar.kiss.R;

/**
 * A frame that wraps a widget and provides resize handles for finger-based resizing.
 * Similar to how modern launchers like Nova, Lawnchair, etc. handle widget resizing.
 */
public class WidgetResizeFrame extends FrameLayout {

    public interface OnResizeListener {
        void onResizeStart(View widget);
        void onResize(View widget, int newWidth, int newHeight, int deltaX, int deltaY);
        void onResizeEnd(View widget, int finalWidth, int finalHeight, int finalX, int finalY);
        void onResizeComplete();
    }

    private static final int HANDLE_SIZE_DP = 24;
    private static final int BORDER_WIDTH_DP = 2;
    private static final int MIN_SIZE_DP = 40;
    private static final int TOUCH_SLOP_DP = 8;

    // Handle positions
    private static final int HANDLE_NONE = 0;
    private static final int HANDLE_LEFT = 1;
    private static final int HANDLE_TOP = 2;
    private static final int HANDLE_RIGHT = 4;
    private static final int HANDLE_BOTTOM = 8;
    private static final int HANDLE_CENTER = 16; // For dragging
    private static final int HANDLE_TOP_LEFT = HANDLE_TOP | HANDLE_LEFT;
    private static final int HANDLE_TOP_RIGHT = HANDLE_TOP | HANDLE_RIGHT;
    private static final int HANDLE_BOTTOM_LEFT = HANDLE_BOTTOM | HANDLE_LEFT;
    private static final int HANDLE_BOTTOM_RIGHT = HANDLE_BOTTOM | HANDLE_RIGHT;

    private final Paint borderPaint;
    private final Paint handlePaint;
    private final Paint handleFillPaint;
    private final RectF handleRect = new RectF();

    private final int handleSize;
    private final int borderWidth;
    private final int minSize;
    private final int touchSlop;

    private int activeHandle = HANDLE_NONE;
    private float touchStartX, touchStartY;
    private int startWidth, startHeight;
    private int startLeft, startTop;

    private View targetWidget;
    private OnResizeListener resizeListener;
    private boolean isResizing = false;

    public WidgetResizeFrame(@NonNull Context context) {
        this(context, null);
    }

    public WidgetResizeFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetResizeFrame(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = context.getResources().getDisplayMetrics().density;
        handleSize = (int) (HANDLE_SIZE_DP * density);
        borderWidth = (int) (BORDER_WIDTH_DP * density);
        minSize = (int) (MIN_SIZE_DP * density);
        touchSlop = (int) (TOUCH_SLOP_DP * density);

        // Border paint
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setColor(ContextCompat.getColor(context, R.color.zenlauncher));

        // Handle stroke paint
        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(borderWidth);
        handlePaint.setColor(ContextCompat.getColor(context, R.color.zenlauncher));

        // Handle fill paint
        handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleFillPaint.setStyle(Paint.Style.FILL);
        handleFillPaint.setColor(0xFFFFFFFF);

        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        // Add a "Done" button at the top
        addDoneButton(context, density);
    }

    private void addDoneButton(Context context, float density) {
        TextView doneButton = new TextView(context);
        doneButton.setText("✓");
        doneButton.setTextSize(18);
        doneButton.setTypeface(null, Typeface.BOLD);
        doneButton.setTextColor(0xFFFFFFFF);
        doneButton.setBackgroundColor(ContextCompat.getColor(context, R.color.zenlauncher));

        int padding = (int) (8 * density);
        doneButton.setPadding(padding * 2, padding, padding * 2, padding);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = (int) (-32 * density);
        lp.rightMargin = 0;
        doneButton.setLayoutParams(lp);

        doneButton.setOnClickListener(v -> {
            if (resizeListener != null) {
                resizeListener.onResizeComplete();
            }
        });

        addView(doneButton);
    }

    public void setTargetWidget(View widget) {
        this.targetWidget = widget;
    }

    public void setOnResizeListener(OnResizeListener listener) {
        this.resizeListener = listener;
    }

    public void setFrameColor(int color) {
        borderPaint.setColor(color);
        handlePaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw border
        float halfBorder = borderWidth / 2f;
        canvas.drawRect(halfBorder, halfBorder, width - halfBorder, height - halfBorder, borderPaint);

        // Draw corner handles
        drawHandle(canvas, 0, 0); // Top-left
        drawHandle(canvas, width - handleSize, 0); // Top-right
        drawHandle(canvas, 0, height - handleSize); // Bottom-left
        drawHandle(canvas, width - handleSize, height - handleSize); // Bottom-right

        // Draw edge handles
        drawHandle(canvas, (width - handleSize) / 2f, 0); // Top
        drawHandle(canvas, (width - handleSize) / 2f, height - handleSize); // Bottom
        drawHandle(canvas, 0, (height - handleSize) / 2f); // Left
        drawHandle(canvas, width - handleSize, (height - handleSize) / 2f); // Right
    }

    private void drawHandle(Canvas canvas, float x, float y) {
        float padding = handleSize * 0.2f;
        handleRect.set(x + padding, y + padding, x + handleSize - padding, y + handleSize - padding);
        canvas.drawOval(handleRect, handleFillPaint);
        canvas.drawOval(handleRect, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(event);
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(event);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return handleTouchUp(event);
        }
        return super.onTouchEvent(event);
    }

    private boolean handleTouchDown(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        activeHandle = getHandleAtPosition(x, y);
        if (activeHandle != HANDLE_NONE) {
            touchStartX = event.getRawX();
            touchStartY = event.getRawY();
            startWidth = getWidth();
            startHeight = getHeight();

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) getLayoutParams();
            startLeft = lp.leftMargin;
            startTop = lp.topMargin;

            isResizing = true;
            if (resizeListener != null && targetWidget != null) {
                resizeListener.onResizeStart(targetWidget);
            }
            return true;
        }
        return false;
    }

    private boolean handleTouchMove(MotionEvent event) {
        if (!isResizing || activeHandle == HANDLE_NONE) {
            return false;
        }

        float deltaX = event.getRawX() - touchStartX;
        float deltaY = event.getRawY() - touchStartY;

        int newWidth = startWidth;
        int newHeight = startHeight;
        int newLeft = startLeft;
        int newTop = startTop;

        // Handle horizontal resizing
        if ((activeHandle & HANDLE_LEFT) != 0) {
            int proposedWidth = (int) (startWidth - deltaX);
            if (proposedWidth >= minSize) {
                newWidth = proposedWidth;
                newLeft = (int) (startLeft + deltaX);
            }
        } else if ((activeHandle & HANDLE_RIGHT) != 0) {
            int proposedWidth = (int) (startWidth + deltaX);
            if (proposedWidth >= minSize) {
                newWidth = proposedWidth;
            }
        }

        // Handle vertical resizing
        if ((activeHandle & HANDLE_TOP) != 0) {
            int proposedHeight = (int) (startHeight - deltaY);
            if (proposedHeight >= minSize) {
                newHeight = proposedHeight;
                newTop = (int) (startTop + deltaY);
            }
        } else if ((activeHandle & HANDLE_BOTTOM) != 0) {
            int proposedHeight = (int) (startHeight + deltaY);
            if (proposedHeight >= minSize) {
                newHeight = proposedHeight;
            }
        }

        // Handle dragging (center)
        if (activeHandle == HANDLE_CENTER) {
            newLeft = (int) (startLeft + deltaX);
            newTop = (int) (startTop + deltaY);
        }

        // Apply changes
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) getLayoutParams();
        lp.width = newWidth;
        lp.height = newHeight;
        lp.leftMargin = newLeft;
        lp.topMargin = newTop;
        setLayoutParams(lp);

        if (resizeListener != null && targetWidget != null) {
            resizeListener.onResize(targetWidget, newWidth, newHeight, newLeft, newTop);
        }

        return true;
    }

    private boolean handleTouchUp(MotionEvent event) {
        if (isResizing && resizeListener != null && targetWidget != null) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) getLayoutParams();
            resizeListener.onResizeEnd(targetWidget, lp.width, lp.height, lp.leftMargin, lp.topMargin);
        }
        isResizing = false;
        activeHandle = HANDLE_NONE;
        return true;
    }

    private int getHandleAtPosition(float x, float y) {
        int width = getWidth();
        int height = getHeight();
        int extendedHandle = handleSize + touchSlop;

        // Check corners first (they have priority)
        // Top-left
        if (x < extendedHandle && y < extendedHandle) {
            return HANDLE_TOP_LEFT;
        }
        // Top-right
        if (x > width - extendedHandle && y < extendedHandle) {
            return HANDLE_TOP_RIGHT;
        }
        // Bottom-left
        if (x < extendedHandle && y > height - extendedHandle) {
            return HANDLE_BOTTOM_LEFT;
        }
        // Bottom-right
        if (x > width - extendedHandle && y > height - extendedHandle) {
            return HANDLE_BOTTOM_RIGHT;
        }

        // Check edges
        // Top edge
        if (y < extendedHandle && x > extendedHandle && x < width - extendedHandle) {
            return HANDLE_TOP;
        }
        // Bottom edge
        if (y > height - extendedHandle && x > extendedHandle && x < width - extendedHandle) {
            return HANDLE_BOTTOM;
        }
        // Left edge
        if (x < extendedHandle && y > extendedHandle && y < height - extendedHandle) {
            return HANDLE_LEFT;
        }
        // Right edge
        if (x > width - extendedHandle && y > extendedHandle && y < height - extendedHandle) {
            return HANDLE_RIGHT;
        }

        // Center area - for dragging
        if (x > extendedHandle && x < width - extendedHandle &&
            y > extendedHandle && y < height - extendedHandle) {
            return HANDLE_CENTER;
        }

        return HANDLE_NONE;
    }

    public boolean isResizing() {
        return isResizing;
    }
}
