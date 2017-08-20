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

    private double baseUnitMeters;

    private final NumberPicker unitMeters; // e.g km or mi
    private final TextView unitString;

    public DistancePicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        Formatter f = new Formatter(context);
        unitMeters = new NumberPicker(context, attrs, true, f.getUnitDecimals());
        LinearLayout unitStringLayout = new LinearLayout(context, attrs);
        unitStringLayout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        unitStringLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        unitString = new TextView(context, attrs);
        unitString.setTextSize(25);
        unitStringLayout.addView(unitString);
        unitMeters.setOrientation(VERTICAL);

        setOrientation(HORIZONTAL);
        setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        addView(unitMeters);
        addView(unitStringLayout);

        setBaseUnit(f.getUnitMeters(), f.getUnitString());
    }

    private void setBaseUnit(double baseUnit, String baseString) {
        baseUnitMeters = baseUnit;
        unitString.setText(baseString);
    }

    public double getDistance() {
        return unitMeters.getValue() * baseUnitMeters;
    }

    public void setDistance(double s) {
        double h = s / baseUnitMeters;
        unitMeters.setValue(h);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        unitMeters.setEnabled(enabled);
    }
}
