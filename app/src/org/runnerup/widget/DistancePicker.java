/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.runnerup.util.Formatter;

@TargetApi(Build.VERSION_CODES.FROYO)
public class DistancePicker extends LinearLayout {

    private final long baseUnitMeters;

    private final NumberPicker majorNo; // e.g km or mi
    private final NumberPicker minorNo; //fraction, i.e. meters or miles/1000

    public DistancePicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        majorNo = new NumberPicker(context, attrs);
        majorNo.setOrientation(VERTICAL);
        addView(majorNo);

        LinearLayout decimalSeparatorLayout = new LinearLayout(context, attrs);
        decimalSeparatorLayout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        decimalSeparatorLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        TextView decimalSeparator = new TextView(context, attrs);
        decimalSeparator.setTextSize(25);
        decimalSeparatorLayout.addView(decimalSeparator);
        addView(decimalSeparatorLayout);

        minorNo = new NumberPicker(context, attrs);
        minorNo.setOrientation(VERTICAL);
        addView(minorNo);

        LinearLayout unitStringLayout = new LinearLayout(context, attrs);
        unitStringLayout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        unitStringLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        TextView unitString = new TextView(context, attrs);
        unitString.setTextSize(25);
        unitStringLayout.addView(unitString);
        addView(unitStringLayout);

        setOrientation(HORIZONTAL);
        setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        Formatter f = new Formatter(context);
        baseUnitMeters = (long) f.getUnitMeters();
        unitString.setText(f.getUnitString());
        minorNo.setRange(0, 999, true);
        minorNo.setDigits(3);
        unitString.setText(f.getUnitString());
        decimalSeparator.setText(String.valueOf(f.getDecimalSeparator()));
    }

    public long getDistance() {
        long ret = 0;
        ret += minorNo.getValue() * (baseUnitMeters / 1000.0d);
        ret += (long) majorNo.getValue() * baseUnitMeters;
        return ret;
    }

    public void setDistance(long s) {
        long h = s / baseUnitMeters;
        s -= h * baseUnitMeters;
        s*= 1000.0d / baseUnitMeters;
        majorNo.setValue((int) h);
        minorNo.setValue((int) s);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        majorNo.setEnabled(enabled);
        minorNo.setEnabled(enabled);
    }
}
