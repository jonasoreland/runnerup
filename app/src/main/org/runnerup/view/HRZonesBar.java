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
import android.view.View;

public class HRZonesBar extends View {

  private static final int colorLow = Color.WHITE; // Color for the zone 0
  private static final int colorHigh = Color.parseColor("#ff0000"); // Color for the last zone

  // The two arrays will be used to make the gradient
  private static final int[] dColorLow =
      new int[] {Color.red(colorLow), Color.green(colorLow), Color.blue(colorLow)};
  private static final int[] dColorDiff =
      new int[] {
        Color.red(colorHigh) - dColorLow[0],
        Color.green(colorHigh) - dColorLow[1],
        Color.blue(colorHigh) - dColorLow[2]
      };

  private static final float borderSize = 6; // Border around the chart
  private static final float separatorSize = 2; // Separator between two zones
  private static final int minBarHeight = 32;
  private static final int maxBarHeight = 64;
  private static final int preferredBarHeight = 48;
  private static final float minChartWidth = 120f;

  private final Paint paint = new Paint();
  private final Paint fontPaint = new Paint();

  private double[] hrzData = null;

  public HRZonesBar(Context ctx) {
    super(ctx);
  }

  public void pushHrzData(double[] data) {
    this.hrzData = data;
    requestLayout();
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int zoneCount = hrzData != null && hrzData.length > 0 ? hrzData.length : 6;
    int desiredHeight =
        (int)
            Math.ceil(
                2 * borderSize + zoneCount * preferredBarHeight + (zoneCount - 1) * separatorSize);
    int measuredWidth = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
    setMeasuredDimension(measuredWidth, measuredHeight);
  }

  public void onDraw(Canvas canvas) {
    if (hrzData == null) {
      return;
    }

    // calculate bar height and chart offset
    int actualHeight = getHeight();
    float calculatedBarHeight =
        (actualHeight - 2 * borderSize - (hrzData.length - 1) * separatorSize)
            / hrzData.length; // Height of the bar
    calculatedBarHeight = Math.min(calculatedBarHeight, maxBarHeight);
    calculatedBarHeight = Math.max(calculatedBarHeight, minBarHeight);
    int topOffset = 0;

    float totalWidth = getWidth();
    if (totalWidth <= 0 || calculatedBarHeight <= 0) {
      return;
    }

    // Font size and style
    int fontSize = Math.max(15, (int) (calculatedBarHeight * 0.675f));
    fontPaint.setTextSize(fontSize);
    fontPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
    fontPaint.setColor(Color.WHITE);
    fontPaint.setStyle(Paint.Style.FILL);
    fontPaint.setTextAlign(Paint.Align.LEFT);

    paint.setStrokeWidth(0);
    paint.setStyle(Paint.Style.FILL);

    canvas.drawColor(Color.TRANSPARENT);

    // calculate sum for percentage calculation
    double sum = 0;
    for (double aHrzData : hrzData) {
      sum += aHrzData;
    }

    float maxLabelWidth = 0;
    for (int i = 0; i < hrzData.length; i++) {
      String zoneName = getResources().getString(org.runnerup.common.R.string.Zone) + " " + i;
      maxLabelWidth = Math.max(maxLabelWidth, fontPaint.measureText(zoneName));
    }
    float percentTextWidth = fontPaint.measureText("100%");
    float chartWidth = Math.max(0f, totalWidth - maxLabelWidth - percentTextWidth - 5 * borderSize);
    float requiredWidth = maxLabelWidth + percentTextWidth + minChartWidth + 5 * borderSize;
    int requiredWidthPx = (int) Math.ceil(requiredWidth);
    if (getMinimumWidth() != requiredWidthPx) {
      setMinimumWidth(requiredWidthPx);
      requestLayout();
    }

    // do the drawing
    for (int i = 0; i < hrzData.length; i++) {
      int rectColor =
          Color.rgb(
              dColorLow[0] + (i * dColorDiff[0]) / hrzData.length,
              dColorLow[1] + (i * dColorDiff[1]) / hrzData.length,
              dColorLow[2] + (i * dColorDiff[2]) / hrzData.length);
      paint.setColor(rectColor);

      // calculate per cent value of Zone duration
      double hrzPart = sum > 0 ? hrzData[i] / sum : 0.0;
      float percent = Math.round((float) hrzPart * 100);

      // calculate text and bar length
      String zoneName = getResources().getString(org.runnerup.common.R.string.Zone) + " " + i;
      String percentText = percent + "%";
      float barLen = (float) (chartWidth * hrzPart);

      // elements x-offset
      float zoneOffset = borderSize;
      float barOffset = zoneOffset + maxLabelWidth + borderSize;
      float percentOffset = barOffset + chartWidth + borderSize;
      float percentWidth = fontPaint.measureText(percentText);
      float extraInsideInset =
          percent >= 100
              ? fontPaint.measureText("100.0%")
              : percent >= 10 ? fontPaint.measureText("20.0%") : fontPaint.measureText("2.0%");
      float percentInsideOffset =
          Math.max(
              barOffset + borderSize,
              barOffset + chartWidth - percentWidth - borderSize + extraInsideInset);
      //noinspection IntegerDivisionInFloatingPointContext
      float y = topOffset + (i + 1) * borderSize + calculatedBarHeight * (i + 1) - fontSize / 2;

      // draw actual values and bars
      canvas.drawText(zoneName, zoneOffset, y, fontPaint);
      if (percentOffset + percentWidth <= totalWidth - borderSize) {
        canvas.drawText(percentText, percentOffset, y, fontPaint);
      } else {
        canvas.drawText(percentText, percentInsideOffset, y, fontPaint);
      }

      if (hrzPart >= 0) {
        canvas.drawRect(
            barOffset,
            topOffset + i * calculatedBarHeight + (i + 1) * borderSize,
            barOffset + barLen,
            topOffset + (i + 1) * calculatedBarHeight + (i + 1) * borderSize,
            paint);
      }
    }
  }
}
