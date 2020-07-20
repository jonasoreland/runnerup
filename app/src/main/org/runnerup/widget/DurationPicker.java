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
import android.widget.LinearLayout;


public class DurationPicker extends LinearLayout {

    private final NumberPicker hours;
    private final NumberPicker minutes;
    private final NumberPicker seconds;

    public DurationPicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        hours = new NumberPicker(context, attrs);
        hours.setMinimumHeight(48);
        hours.setMinimumWidth(48);
        minutes = new NumberPicker(context, attrs);
        minutes.setMinimumHeight(48);
        minutes.setMinimumWidth(48);
        seconds = new NumberPicker(context, attrs);
        seconds.setMinimumHeight(48);
        seconds.setMinimumWidth(48);

        hours.setOrientation(VERTICAL);
        minutes.setOrientation(VERTICAL);
        seconds.setOrientation(VERTICAL);

        setOrientation(HORIZONTAL);
        setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        addView(hours);
        addView(minutes);
        addView(seconds);
    }

    public long getEpochTime() {
        long ret = 0;
        ret += seconds.getValue();
        ret += (long) minutes.getValue() * 60;
        ret += (long) hours.getValue() * 60 * 60;
        return ret;
    }

    public void setEpochTime(long s) {
        long h = s / 3600;
        s -= h * 3600;
        long m = s / 60;
        s -= m * 60;
        hours.setValue((int) h);
        minutes.setValue((int) m);
        seconds.setValue((int) s);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        hours.setEnabled(enabled);
        minutes.setEnabled(enabled);
        seconds.setEnabled(enabled);
    }
}
