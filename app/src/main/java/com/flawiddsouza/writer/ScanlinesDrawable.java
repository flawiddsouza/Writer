package com.flawiddsouza.writer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class ScanlinesDrawable extends Drawable {
    private Paint paint;
    private int lineSpacing;

    public ScanlinesDrawable(int density) {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setAntiAlias(false); // Sharp pixels for CRT effect

        // Fixed moderate opacity for visibility
        paint.setColor(0x30000000); // ~18% opacity black

        // Density 0-100 controls line spacing (lower = more spacing, higher = more lines)
        // 0: Every 10 pixels (very sparse)
        // 50: Every 4 pixels (medium)
        // 100: Every 2 pixels (very dense)
        lineSpacing = 10 - (density * 8 / 100); // 10 to 2 pixels
        if (lineSpacing < 2) lineSpacing = 2; // Minimum 2 pixels spacing
    }

    @Override
    public void draw(Canvas canvas) {
        int height = getBounds().height();
        int width = getBounds().width();

        // Draw 1-pixel horizontal lines every 'lineSpacing' pixels
        for (int y = 0; y < height; y += lineSpacing) {
            canvas.drawLine(0, y, width, y, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
