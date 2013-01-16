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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class TitleSpinner extends LinearLayout {

	TextView mTitle = null;
	TextView mValue = null;
	Spinner mSpinner = null;

	public TitleSpinner(Context context, AttributeSet attrs) {
		super(context, attrs);

		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.title_spinner, this);
			
		mTitle = (TextView) findViewById(R.id.title);
		mValue = (TextView) findViewById(R.id.value);
		mSpinner = (Spinner) findViewById(R.id.spinner);

		TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.TitleSpinner);
		CharSequence title = arr.getString(R.styleable.TitleSpinner_android_text);
		if (title != null) {
			mTitle.setText(title);
		}

		CharSequence prompt = arr.getText(R.styleable.TitleSpinner_android_prompt);
		if (prompt != null) {
			mSpinner.setPrompt(prompt);
		}

		CharSequence entries[] = arr.getTextArray(R.styleable.TitleSpinner_android_entries);
		if (entries != null) {
			ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_item, entries);
			mSpinner.setAdapter(adapter);
		}
		
		arr.recycle();  // Do this when done.
		
		LinearLayout layout = (LinearLayout) findViewById(R.id.titleSpinner);
		layout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
            	mSpinner.performClick();
			}
		});

		mSpinner.setOnItemSelectedListener(new OnItemSelectedListener(){
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				Object o = arg0.getItemAtPosition(arg2);
				mValue.setText(o.toString());
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
	}

	void setAdapter(SpinnerAdapter adapter) {
		mSpinner.setAdapter(adapter);
	}
}
