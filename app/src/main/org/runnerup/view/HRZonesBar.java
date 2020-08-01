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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.runnerup.R;


public class HRZonesBar extends View {

    private static final int colorLow = Color.WHITE; // Color for the zone 0
    private static final int colorHigh = Color.parseColor("#ff0000"); // Color for the last zone

    // The two arrays will be used to make the gradient
    private static final int[] dColorLow = new int[]{Color.red(colorLow), Color.green(colorLow), Color.blue(colorLow)};
    private static final int[] dColorDiff = new int[]{Color.red(colorHigh) - dColorLow[0],
            Color.green(colorHigh) - dColorLow[1],
            Color.blue(colorHigh) - dColorLow[2]};

    private static final float borderSize = 10; // Border around the chart
    private static final float separatorSize = 2; // Separator between two zones
    private static final int minBarHeight = 15;
    private static final int maxBarHeight = 40;
    private static final double chartSize = 0.8;

    private final Paint paint = new Paint();
    private final Paint fontPaint = new Paint();

    private double[] hrzData = null;

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
        AppCompatActivity activity = (AppCompatActivity) getContext();
        LinearLayout buttons = activity.findViewById(R.id.buttons);

        int actualHeight = getHeight() - buttons.getHeight();
        float calculatedBarHeight = (actualHeight - 2*borderSize - (hrzData.length-1) * separatorSize)/hrzData.length; // Height of the bar
        calculatedBarHeight = calculatedBarHeight > maxBarHeight ? maxBarHeight : calculatedBarHeight;
        int topOffset = getTop();

        float totalWidth = getWidth();
        if (totalWidth <= 0 || calculatedBarHeight < 10 ) {
            Log.e(getClass().getName(), "Not enough space to display the heart-rate zone bar");
            activity.findViewById(R.id.hrzonesBarLayout).setVisibility(View.GONE);
            return;
        }

        // Font size and style
        int fontSize = (int)calculatedBarHeight / 2;
        fontPaint.setTextSize(fontSize);
        fontPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        fontPaint.setColor(Color.WHITE);
        fontPaint.setStyle(Paint.Style.FILL);
        fontPaint.setTextAlign(Paint.Align.LEFT);

        paint.setStrokeWidth(0);
        paint.setStyle(Paint.Style.FILL);

        canvas.drawColor(Color.TRANSPARENT);

        //calculate sum for percentage calculation
        double sum = 0;
        for (double aHrzData : hrzData) {
            sum += aHrzData;
        }

        //do the drawing
        for (int i = 0; i < hrzData.length; i++) {
            int rectColor = Color.rgb(dColorLow[0] + (i * dColorDiff[0]) / hrzData.length,
                    dColorLow[1] + (i * dColorDiff[1]) / hrzData.length,
                    dColorLow[2] + (i * dColorDiff[2]) / hrzData.length);
            paint.setColor(rectColor);

            //calculate per cent value of Zone duration
            double hrzPart = hrzData[i] / sum;
            float percent = Math.round((float)hrzPart * 100);

            //calculate text and bar length
            String zoneName = getResources().getString(R.string.Zone) + " " + i;
            float textLen = fontPaint.measureText(zoneName);
            float chartWidth = (float) ((totalWidth - textLen - 4 * borderSize) * chartSize);
            float barLen = (float) (chartWidth * hrzPart);


            //elements x-offset
            float zoneOffset = borderSize;
            float barOffset = zoneOffset + textLen + borderSize;
            float percentOffset = barOffset + chartWidth + borderSize;

            //draw actual values and bars
            if(calculatedBarHeight > minBarHeight) {
                //noinspection IntegerDivisionInFloatingPointContext
                canvas.drawText(zoneName, zoneOffset, topOffset + (i+1) * borderSize + calculatedBarHeight * (i + 1) - fontSize / 2, fontPaint);
                //noinspection IntegerDivisionInFloatingPointContext
                canvas.drawText(percent + "%", percentOffset, topOffset + (i+1) * borderSize + calculatedBarHeight * (i + 1) - fontSize / 2, fontPaint);
            }

            if (hrzPart >= 0) {
                canvas.drawRect(barOffset, topOffset + i * calculatedBarHeight + (i+1)* borderSize, barOffset + barLen, topOffset + (i+1)*calculatedBarHeight + (i+1)* borderSize, paint);
            }
        }
    }
}
