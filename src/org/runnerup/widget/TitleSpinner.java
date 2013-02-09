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

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import org.runnerup.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.TimePicker;

public class TitleSpinner extends LinearLayout {

	private enum Type {
		TS_SPINNER,
		TS_EDITTEXT,
		TS_DATEPICKER,
		TS_TIMEPICKER
	};
	
	private int mValueInt = -1;
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
			mType = Type.TS_EDITTEXT;
			setupEditText(context, arr, defaultValue);
		} else if ("datepicker".contentEquals(type)) {
			mType = Type.TS_DATEPICKER;
			setupDatePicker(context, arr, defaultValue);
		} else if ("timepicker".contentEquals(type)) {
			mType = Type.TS_TIMEPICKER;
			setupTimePicker(context, arr, defaultValue);
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
				AlertDialog.Builder alert = new AlertDialog.Builder(context);

				alert.setTitle(mTitle.getText());
				if (mPrompt != null) {
					alert.setMessage(mPrompt);
				}

				final EditText edit = new EditText(context);
				edit.setText(mValue.getText());
				edit.setInputType(mInputType);
				alert.setView(edit);
				alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						setValue(edit.getText().toString());
						dialog.dismiss();
					}
				});
				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.dismiss();
					}
				});
				AlertDialog dialog = alert.create();
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
				mValueInt = value;
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
	
	private void setupDatePicker(final Context context, TypedArray arr, CharSequence defaultValue) {
		if (defaultValue != null && "today".contentEquals(defaultValue)) {
			DateFormat df = android.text.format.DateFormat.getDateFormat(context);
			defaultValue = df.format(new Date());
		}
		if (defaultValue != null) {
			mValue.setText(defaultValue);
		} else {
			mValue.setText("");
		}
		
		LinearLayout layout = (LinearLayout) findViewById(R.id.titleSpinner);
		layout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder alert = new AlertDialog.Builder(context);

				alert.setTitle(mTitle.getText());
				if (mPrompt != null) {
					alert.setMessage(mPrompt);
				}

				final DatePicker datePicker = new DatePicker(context);
				alert.setView(datePicker);
				alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						setValue(getValue(datePicker));
						dialog.dismiss();
					}

					private String getValue(DatePicker dp) {
						Calendar c = Calendar.getInstance();
						c.set(dp.getYear(), dp.getMonth(), dp.getDayOfMonth());
						DateFormat df = android.text.format.DateFormat.getDateFormat(context);
						return df.format(c.getTime());
					}
				});
				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.dismiss();
					}
				});
				AlertDialog dialog = alert.create();
				dialog.show();
			}
		});
	}

	private void setupTimePicker(final Context context, TypedArray arr, CharSequence defaultValue) {
		if (defaultValue != null && "now".contentEquals(defaultValue)) {
			DateFormat df = android.text.format.DateFormat.getTimeFormat(context);
			defaultValue = df.format(new Date());
		}
		if (defaultValue != null) {
			mValue.setText(defaultValue);
		} else {
			mValue.setText("");
		}
		
		LinearLayout layout = (LinearLayout) findViewById(R.id.titleSpinner);
		layout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder alert = new AlertDialog.Builder(context);

				alert.setTitle(mTitle.getText());
				if (mPrompt != null) {
					alert.setMessage(mPrompt);
				}

				final TimePicker timePicker = new TimePicker(context);
				alert.setView(timePicker);
				alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						setValue(getValue(timePicker));
						dialog.dismiss();
					}

					private String getValue(TimePicker dp) {
						Calendar c = Calendar.getInstance();
						c.set(Calendar.HOUR, dp.getCurrentHour());
						c.set(Calendar.MINUTE, dp.getCurrentMinute());
						DateFormat df = android.text.format.DateFormat.getTimeFormat(context);
						return df.format(c.getTime());
					}
				});
				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.dismiss();
					}
				});
				AlertDialog dialog = alert.create();
				dialog.show();
			}
		});
	}

	public void setAdapter(SpinnerAdapter adapter) {
		mSpinner.setAdapter(adapter);
	}

	public void setOnSetValueListener(OnSetValueListener listener) {
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
		if (pref == null)
			return;
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

	public void setValue(String value) {
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

	public void setValue(int value) {
		if (mSetValueListener != null) {
			try {
				value = mSetValueListener.preSetValue(value);
			} catch (java.lang.IllegalArgumentException ex) {
				if (mValueInt != -1) {
					mSpinner.setSelection(mValueInt);
				}
				return;
			}
		}
		mValueInt = value;
		mSpinner.setSelection(mValueInt);
		mValue.setText(mSpinner.getAdapter().getItem(value).toString());
		if (mKey == null)
			return;
		Editor pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
		pref.putInt(mKey, value);
		pref.commit();
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		LinearLayout layout = (LinearLayout) findViewById(R.id.titleSpinner);
		layout.setEnabled(enabled);
		mSpinner.setEnabled(enabled);
	}

	public CharSequence getValue() {
		return mValue.getText();
	}
}
