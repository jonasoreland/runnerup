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
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
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

	private enum Type {
		TS_SPINNER,
		TS_EDITTEXT
	};
	
	private String mKey = null;
	private TextView mTitle = null;
	private TextView mValue = null;
	private Spinner mSpinner = null;
	private CharSequence mPrompt = null;
	int mInputType = 0;
	private Context mContext;
	private OnSetValueListener mSetValueListener = null;
	private Type mType = null;
	
	public interface OnSetValueListener {
		/**
		 * 
		 * @param newValue
		 * @return
		 * @throws java.lang.IllegalArgumentException
		 */
		public String preSetValue(String newValue) throws java.lang.IllegalArgumentException;
		
		/**
		 * 
		 * @param newValue
		 * @return
		 * @throws java.lang.IllegalArgumentException
		 */
		public int preSetValue(int newValue) throws java.lang.IllegalArgumentException;
	};
	
	public TitleSpinner(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		
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
		CharSequence defaultValue = arr.getString(R.styleable.TitleSpinner_android_defaultValue);
		if (type == null || "spinner".contentEquals(type)) {
			mType = Type.TS_SPINNER;
			setupSpinner(context, arr, defaultValue);
		} else if ("edittext".contentEquals(type)) {
			mType = Type.TS_SPINNER;
			setupEditText(context, arr, defaultValue);
		} else {
			String s = null;
			s.charAt(8);
		}

		
		CharSequence key = arr.getString(R.styleable.TitleSpinner_android_key);
		if (key != null) {
			mKey = key.toString();
			loadValue(defaultValue != null ? defaultValue.toString() : null);
		}
		
		arr.recycle();  // Do this when done.
	}

	private void setupEditText(final Context context, TypedArray arr, CharSequence defaultValue) {
		mInputType = arr.getInt(R.styleable.TitleSpinner_android_inputType, EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_FLAG_DECIMAL);
		mValue.setText(defaultValue);
		
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
						setValue(edit.getText().toString());
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

	private void setupSpinner(Context context, TypedArray arr, CharSequence defaultValue) {
		if (mPrompt != null) {
			mSpinner.setPrompt(mPrompt);
		}

		CharSequence entries[] = arr.getTextArray(R.styleable.TitleSpinner_android_entries);
		if (entries != null) {
			ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_dropdown_item, entries);
			mSpinner.setAdapter(adapter);
			int value = 0;
			if (defaultValue != null) {
				try {
					value = Integer.parseInt(defaultValue.toString());
				} catch (java.lang.NumberFormatException ex) {
				}
			}
			if (value >= 0 && value < entries.length) {
				mValue.setText(entries[value]);
			}
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
			public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				setValue(arg2);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
	}
	
	void setAdapter(SpinnerAdapter adapter) {
		mSpinner.setAdapter(adapter);
	}

	void setOnSetValueListener(OnSetValueListener listener) {
		this.mSetValueListener = listener;
	}
	
	public void setTitle(String title) {
		mTitle.setText(title);
	}
	
	public void setPrompt(String prompt) {
		mPrompt = prompt;
	}
	
	private void loadValue(String defaultValue) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
		switch (mType) {
		case TS_SPINNER:
			int def = 0;
			if (defaultValue != null) {
				try {
					def = Integer.parseInt(defaultValue);
				} catch (NumberFormatException ex) {
				}
			}
			setValue(pref.getInt(mKey, def));
			break;
		case TS_EDITTEXT:
			String newValue = pref.getString(mKey, defaultValue == null ? "" : defaultValue);
			setValue(newValue);
		}
	}

	private void setValue(String value) {
		if (mSetValueListener != null) {
			try {
				value = mSetValueListener.preSetValue(value);
			} catch (java.lang.IllegalArgumentException ex) {
				return;
			}
		}
		mValue.setText(value);
		if (mKey == null)
			return;
		Editor pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
		pref.putString(mKey,  value);
		pref.commit();
	}

	private void setValue(int value) {
		if (mSetValueListener != null) {
			try {
				value = mSetValueListener.preSetValue(value);
			} catch (java.lang.IllegalArgumentException ex) {
				return;
			}
		}
		mSpinner.setSelection(value);
		mValue.setText(mSpinner.getAdapter().getItem(value).toString());
		if (mKey == null)
			return;
		Editor pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
		pref.putInt(mKey, value);
		pref.commit();
	}
}
