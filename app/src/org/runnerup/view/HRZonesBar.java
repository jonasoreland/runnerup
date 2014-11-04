/*
 * Copyright (C) 2014 git@fabieng.eu
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZonesBar extends View {

    enum BarOrientation {
        HORIZONTAL, VERTICAL
    };

    BarOrientation barOrientation = BarOrientation.HORIZONTAL;

    static final int colorLow = Color.WHITE; // Color for the zone 0
    static final int colorHigh = Color.parseColor("#ff0000"); // Color for the last zone

    // The two arrays will be used to make the gradient
    static final int[] dColorLow = new int[]{Color.red(colorLow), Color.green(colorLow), Color.blue(colorLow)};
    static final int[] dColorDiff = new int[]{Color.red(colorHigh) - dColorLow[0],
            Color.green(colorHigh) - dColorLow[1],
            Color.blue(colorHigh) - dColorLow[2]};

    static final float border = 5; // Border around the bar
    static final float barHeight = 40; // Height of the bar
    static final int fontSize = 20; // Font size used for writing the zone numbers
    static final float separator = 2; // Separator between two zones

    Paint paint = new Paint();
    Paint fontPaint = new Paint();

    double[] hrzData = null;

    public HRZonesBar (Context ctx) {
        super(ctx);
    }

    /**
     * Returns black or white, depending on which color would contrast best with the provided color.
     * Code taken from https://github.com/MatthewYork/Colours
     */
    public static int blackOrWhiteContrastingColor(int color) {
        int[] rgbArray = new int[]{Color.red(color), Color.green(color), Color.blue(color)};
        double a = 1 - ((0.00299 * (double) rgbArray[0]) + (0.00587 * (double) rgbArray[1]) + (0.00114 * (double) rgbArray[2]));
        return a < 0.5 ? Color.BLACK : Color.WHITE;
    }

    public void pushHrzData (double[] data) {
        this.hrzData = data;
    }

    public void onDraw(Canvas canvas) {
        if (hrzData == null) {
            return;
        }

        canvas.drawColor(Color.TRANSPARENT);

        fontPaint.setTextSize(fontSize);
        fontPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        fontPaint.setColor(Color.BLACK);
        fontPaint.setStyle(Paint.Style.FILL);

        paint.setStrokeWidth(0);
        paint.setStyle(Paint.Style.FILL);

        float left = 0;
        float totWidth = 0;
        if (barOrientation == BarOrientation.HORIZONTAL) {
            totWidth = getWidth() - border * 2;
            left = border;
        } else if (barOrientation == BarOrientation.VERTICAL) {
            totWidth = getHeight() - border * 2;
            left = getHeight() - border;
        }

        for (double aHrzData : hrzData) {
            if (aHrzData > 0) {
                totWidth -= separator;
            }
        }
        if (totWidth <= 0) {
            System.err.println("Not enough space to display the heart-rate zone bar");
            return;
        }

        for (int i = 0; i < hrzData.length; i++) {
            if (hrzData[i] <= 0) {
                continue;
            }

            int rectColor = Color.rgb(dColorLow[0] + (i * dColorDiff[0]) / hrzData.length,
                    dColorLow[1] + (i * dColorDiff[1]) / hrzData.length,
                    dColorLow[2] + (i * dColorDiff[2]) / hrzData.length);
            paint.setColor(rectColor);
            fontPaint.setColor(blackOrWhiteContrastingColor(rectColor));

            float len = (float) (totWidth * hrzData[i]);
            if (barOrientation == BarOrientation.HORIZONTAL) {
                canvas.drawRect(left, border, left + len, barHeight + border, paint);
            } else if (barOrientation == BarOrientation.VERTICAL) {
                canvas.drawRect(border, left - len, barHeight + border, left, paint);
            }

            float textLen = fontPaint.measureText("Z" + i);
            if ((barOrientation == BarOrientation.HORIZONTAL) && (textLen < len)) {
                canvas.drawText("Z" + i, left + (len - textLen) / 2, border + (barHeight + fontSize) / 2, fontPaint);
            } else if ((barOrientation == BarOrientation.VERTICAL) && (fontSize < len)) {
                canvas.drawText("Z" + i, border + (barHeight - textLen) / 2, left + (fontSize - len) / 2, fontPaint);
            }

            if (barOrientation == BarOrientation.HORIZONTAL) {
                left += len + separator;
            } else if (barOrientation == BarOrientation.VERTICAL) {
                left -= len + separator;
            }
        }
    }

    public int getTotalBarHeight() {
        return (int) Math.floor(border * 2 + barHeight);
    }
}
