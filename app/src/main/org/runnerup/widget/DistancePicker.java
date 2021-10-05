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

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.runnerup.util.Formatter;


public class DistancePicker extends LinearLayout {

    private long baseUnitMeters;

    private final NumberPicker unitMeters; // e.g km or mi
    private final TextView unitString;
    private final NumberPicker meters;

    public DistancePicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        unitMeters = new NumberPicker(context, attrs);
        LinearLayout unitStringLayout = new LinearLayout(context);
        unitStringLayout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        unitStringLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        unitString = new TextView(context);
        unitString.setTextSize(25);
        unitString.setMinimumHeight(48);
        unitString.setMinimumWidth(48);
        unitStringLayout.addView(unitString);
        meters = new NumberPicker(context, attrs);

        unitMeters.setDigits(3);
        unitMeters.setRange(0,999,true);
        unitMeters.setOrientation(VERTICAL);
        meters.setDigits(4);
        meters.setOrientation(VERTICAL);

        setOrientation(HORIZONTAL);
        setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        addView(unitMeters);
        addView(unitStringLayout);
        addView(meters);

        {
            Formatter f = new Formatter(context);
            setBaseUint((long) f.getUnitMeters(), f.getUnitString());
        }
    }

    private void setBaseUint(long baseUnit, String baseString) {
        baseUnitMeters = baseUnit;
        unitString.setText(baseString);
        meters.setRange(0, (int) baseUnitMeters - 1, true);
    }

    public long getDistance() {
        long ret = 0;
        ret += meters.getValue();
        ret += (long) unitMeters.getValue() * baseUnitMeters;
        return ret;
    }

    public void setDistance(long s) {
        long h = s / baseUnitMeters;
        s -= h * baseUnitMeters;
        unitMeters.setValue((int) h);
        meters.setValue((int) s);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        unitMeters.setEnabled(enabled);
        meters.setEnabled(enabled);
    }
}
