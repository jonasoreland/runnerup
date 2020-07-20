/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.runnerup.R;


public class TextPreference extends android.preference.EditTextPreference {

    public TextPreference(Context context) {
        super(context);
    }

    public TextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue,
                                     Object defaultValue) {
        super.onSetInitialValue(restorePersistedValue, defaultValue);
        super.setSummary(super.getPersistedString(""));
        super.getEditText().setMinimumWidth(48);
        super.getEditText().setMinimumHeight(48);
    }

    @Override
    protected void onDialogClosed(boolean ok) {
        super.onDialogClosed(ok);
        if (ok) {
            String val = super.getPersistedString("");
            if (TextUtils.isEmpty(val)) {
                //If empty, use the default value
                //This could be a default setting and should not be hardcoded in a widget
                //However, getting the default value in the xml seems hard and a similar
                //onPreferenceChange() in SettingsActivity is not much better
                Resources res = getContext().getResources();
                if (this.getKey().equals(res.getString(R.string.pref_mapbox_default_style))) {
                    val = res.getString(R.string.mapboxDefaultStyle);
                    super.setText(val);
                } else if (this.getKey().equals(res.getString(R.string.pref_path_simplification_tolerance))) {
                    val = res.getString(R.string.path_simplification_default_tolerance);
                    super.setText(val);
                }
            }
            super.setSummary(val);
        }
    }
}
