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

import org.runnerup.R;

import android.app.Dialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;

public class StepButton extends TableLayout {

	private Context mContext;
	private TableLayout mLayout;
	
	public StepButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.step_button, this);
		mLayout = (TableLayout) findViewById(R.id.stepButton);

		mLayout.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				final Dialog dialog = new Dialog(mContext);
				dialog.setContentView(R.layout.step_dialog);
				dialog.setTitle("Title...");
				dialog.show();
			}
		});
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		mLayout.setEnabled(enabled);
		for (int i = 0, j = mLayout.getChildCount(); i < j; i++) {
			mLayout.getChildAt(i).setEnabled(enabled);
		}
	}

	
}
