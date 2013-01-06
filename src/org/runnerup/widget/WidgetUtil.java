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

import android.widget.EditText;

public class WidgetUtil {

	public static void setEditable(EditText editText, boolean onoff) {
		if (onoff == true) {
			editText.setClickable(onoff);
			editText.setFocusable(onoff);
			editText.setFocusableInTouchMode(onoff);
		} else {
			editText.setClickable(onoff);
			editText.setFocusable(onoff);
		}
	}
}
