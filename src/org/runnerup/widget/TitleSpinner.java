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

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.content.DialogInterface;

public class TitleSpinner extends LinearLayout {

	private TextView mTitle = null;
	private TextView mValue = null;
	private Spinner mSpinner = null;
	private CharSequence mPrompt = null;
	int mInputType = 0;
	
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
		mPrompt = arr.getText(R.styleable.TitleSpinner_android_prompt);

		CharSequence type = arr.getString(R.styleable.TitleSpinner_type);
		if (type == null || "spinner".contentEquals(type)) {
			setupSpinner(context, arr);
		} else if ("edittext".contentEquals(type)) {
			setupEditText(context, arr);
		} else {
			String s = null;
			s.charAt(8);
		}
		arr.recycle();  // Do this when done.
	}

	private void setupEditText(final Context context, TypedArray arr) {
		CharSequence defaultValue = arr.getString(R.styleable.TitleSpinner_android_defaultValue);
		if (defaultValue != null) {
			mValue.setText(defaultValue);
		}

		mInputType = arr.getInt(R.styleable.TitleSpinner_android_inputType, EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_FLAG_DECIMAL);
		
		LinearLayout layout = (LinearLayout) findViewById(R.id.titleSpinner);
		layout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder dialog = new AlertDialog.Builder(context);

				dialog.setTitle(mTitle.getText());
				if (mPrompt != null) {
					dialog.setMessage(mPrompt);
				}

				final EditText edit = new EditText(context);
				edit.setText(mValue.getText());
				edit.setInputType(mInputType);
				dialog.setView(edit);
				dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						mValue.setText(edit.getText());
						dialog.dismiss();
					}
				});
				dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.dismiss();
					}
				});
				dialog.show();
			}
		});

	}

	private void setupSpinner(Context context, TypedArray arr) {
		if (mPrompt != null) {
			mSpinner.setPrompt(mPrompt);
		}

		CharSequence entries[] = arr.getTextArray(R.styleable.TitleSpinner_android_entries);
		if (entries != null) {
			ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_item, entries);
			mSpinner.setAdapter(adapter);
		}

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
