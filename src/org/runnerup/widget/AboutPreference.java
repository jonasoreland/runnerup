/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

import org.runnerup.R;

import com.google.android.gms.common.GooglePlayServicesUtil;

import android.content.Context;
import android.content.res.Resources;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class AboutPreference extends DialogPreference {

	public AboutPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public AboutPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	void init() {
		setNegativeButtonText(null);
		Resources res = getContext().getResources();
		String str = res.getString(R.string.Google_Play_Services_Legal_Notices);
		if (str.contentEquals(this.getTitle())) {
			CharSequence msg = GooglePlayServicesUtil.getOpenSourceSoftwareLicenseInfo(this.getContext());
			if (msg != null) {
				this.setDialogMessage(msg);
			}
		}
	}
}
