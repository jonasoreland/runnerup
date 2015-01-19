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

import org.runnerup.R;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRZonesBar extends View {

    static final int colorLow = Color.WHITE; // Color for the zone 0
    static final int colorHigh = Color.parseColor("#ff0000"); // Color for the last zone

    // The two arrays will be used to make the gradient
    static final int[] dColorLow = new int[]{Color.red(colorLow), Color.green(colorLow), Color.blue(colorLow)};
    static final int[] dColorDiff = new int[]{Color.red(colorHigh) - dColorLow[0],
            Color.green(colorHigh) - dColorLow[1],
            Color.blue(colorHigh) - dColorLow[2]};

    static final float borderSize = 10; // Border around the bar
    static final float separatorSize = 2; // Separator between two zones
    static final int maxBorderHeight = 60;
    static final double chartSize = 0.8;

    final Paint paint = new Paint();
    final Paint fontPaint = new Paint();

    double[] hrzData = null;

    public HRZonesBar (Context ctx) {
        super(ctx);
    }

    public void pushHrzData (double[] data) {
        this.hrzData = data;
    }

    public void onDraw(Canvas canvas) {
        if (hrzData == null) {
            return;
        }

        //calculate bar height and chart offset
        int actualHeight = getHeight()-getTop();
        float calculatedBarHeight = (actualHeight- borderSize *2-5* separatorSize)/hrzData.length; // Height of the bar
        calculatedBarHeight = calculatedBarHeight>maxBorderHeight ? maxBorderHeight : calculatedBarHeight;
        int offsetY = (int) ((actualHeight - 6*calculatedBarHeight - 5* separatorSize)/2);

        canvas.drawColor(Color.TRANSPARENT);

        // Font size and style
        int fontSize = (int)calculatedBarHeight/2;
        fontPaint.setTextSize(fontSize);
        fontPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        fontPaint.setColor(Color.BLACK);
        fontPaint.setStyle(Paint.Style.FILL);
        fontPaint.setTextAlign(Paint.Align.LEFT);

        paint.setStrokeWidth(0);
        paint.setStyle(Paint.Style.FILL);

        float totalWidth = 0;
        totalWidth = getWidth() - borderSize * 2;
        if (totalWidth <= 0) {
            System.err.println("Not enough space to display the heart-rate zone bar");
            return;
        }

        //calculate sum for percentage calculation
        double sum = 0;
        for (double aHrzData : hrzData) {
            sum += aHrzData;
        }

        for (int i = 0; i < hrzData.length; i++) {
            int rectColor = Color.rgb(dColorLow[0] + (i * dColorDiff[0]) / hrzData.length,
                    dColorLow[1] + (i * dColorDiff[1]) / hrzData.length,
                    dColorLow[2] + (i * dColorDiff[2]) / hrzData.length);
            paint.setColor(rectColor);
            fontPaint.setColor(Color.BLACK);

            //calculate per cent value of Zone duration
            double hrzPart = hrzData[i] / sum;
            float percent = Math.round((float)hrzPart * 100);

            //calculate text and bar length
            String zoneName = getResources().getString(R.string.zone) + " " + i;
            float textLen = fontPaint.measureText(zoneName);
            float barLen = (float) (totalWidth * chartSize * hrzPart);

            //draw actual values and bars
            canvas.drawText(zoneName, borderSize, offsetY + (i+1)* borderSize + calculatedBarHeight*(i+1)-fontSize/2, fontPaint);
            canvas.drawText(percent + "%", (float) (chartSize*totalWidth+ borderSize), offsetY + (i+1)* borderSize + calculatedBarHeight*(i+1)-fontSize/2, fontPaint);

            if (hrzPart >= 0) {
                canvas.drawRect(borderSize + textLen, offsetY + i*calculatedBarHeight + (i+1)* borderSize, borderSize + textLen + barLen, offsetY + (i+1)*calculatedBarHeight + (i+1)* borderSize, paint);
            }
        }
    }
}
