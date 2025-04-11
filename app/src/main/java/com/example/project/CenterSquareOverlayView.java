package com.example.project;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class CenterSquareOverlayView extends View {
    private static final float SQUARE_SIZE_RATIO = 0.1f; // 20% of the smaller dimension
    private Paint squarePaint;

    public CenterSquareOverlayView(Context context) {
        super(context);
        init();
    }

    public CenterSquareOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CenterSquareOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        squarePaint = new Paint();
        squarePaint.setColor(Color.RED);
        squarePaint.setStyle(Paint.Style.STROKE);
        squarePaint.setStrokeWidth(5);
        squarePaint.setAlpha(180); // Add some transparency (255 is fully opaque)
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Calculate square size (same as in CenterSquareDetectionActivity)
        int squareSize = (int) (Math.min(width, height) * SQUARE_SIZE_RATIO);

        // Calculate square position
        int left = (width - squareSize) / 2;
        int top = (height - squareSize) / 2;

        // Draw the square
        Rect centerSquare = new Rect(left, top, left + squareSize, top + squareSize);
        canvas.drawRect(centerSquare, squarePaint);
    }
}
