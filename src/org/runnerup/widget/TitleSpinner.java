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
import org.runnerup.util.SafeParse;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.Gravity;
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

@TargetApi(Build.VERSION_CODES.FROYO)
public class TitleSpinner extends LinearLayout {

    private enum Type {
        TS_SPINNER,
        TS_SPINNER_TXT,
        TS_EDITTEXT,
        TS_DATEPICKER,
        TS_TIMEPICKER,
        TS_DURATIONPICKER,
        TS_DISTANCEPICKER,
        TS_NUMBERPICKER
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
    private OnCloseDialogListener mCloseDialogListener = null;
    private Type mType = null;
    private boolean mFirstSetValue = true;

    public interface OnSetValueListener {
        /**
         * @param newValue
         * @return
         * @throws java.lang.IllegalArgumentException
         */
        public String preSetValue(String newValue) throws java.lang.IllegalArgumentException;

        /**
         * @param newValue
         * @return
         * @throws java.lang.IllegalArgumentException
         */
        public int preSetValue(int newValue) throws java.lang.IllegalArgumentException;
    };

    public interface OnCloseDialogListener {
        public void onClose(TitleSpinner spinner, boolean ok);
    };

    public TitleSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
        } else if ("spinner_txt".contentEquals(type)) {
            mType = Type.TS_SPINNER_TXT;
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
        } else if ("durationpicker".contentEquals(type)) {
            mType = Type.TS_DURATIONPICKER;
            setupDurationPicker(context, arr, defaultValue);
        } else if ("distancepicker".contentEquals(type)) {
            mType = Type.TS_DISTANCEPICKER;
            setupDistancePicker(context, arr, defaultValue);
        } else if ("numberpicker".contentEquals(type)) {
            mType = Type.TS_NUMBERPICKER;
            setupNumberPicker(context, arr, defaultValue);
        } else {
            arr = null; // force null pointer exception
        }

        CharSequence key = arr.getString(R.styleable.TitleSpinner_android_key);
        if (key != null) {
            mKey = key.toString();
            loadValue(defaultValue != null ? defaultValue.toString() : null);
        }

        arr.recycle(); // Do this when done.
    }

    private void setupEditText(final Context context, TypedArray arr, CharSequence defaultValue) {
        mInputType = arr.getInt(R.styleable.TitleSpinner_android_inputType,
                EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_FLAG_DECIMAL);
        mValue.setText(defaultValue);

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
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
                        onClose(true);
                    }
                });
                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        onClose(false);
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
            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context,
                    android.R.layout.simple_spinner_dropdown_item, entries);
            mSpinner.setAdapter(adapter);
            int value = 0;
            if (defaultValue != null) {
                value = SafeParse.parseInt(defaultValue.toString(), 0);
            }
            if (value >= 0 && value < entries.length) {
                mValueInt = value;
                mValue.setText(entries[value]);
            }
        }

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpinner.performClick();
            }
        });

        mSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                if (mType == Type.TS_SPINNER_TXT) {
                    if (mSpinner.getAdapter() != null) {
                        setValue(mSpinner.getAdapter().getItem(arg2).toString());
                    }
                } else {
                    setValue(arg2);
                }
                if (!mFirstSetValue) {
                    onClose(true);
                }
                mFirstSetValue = false;
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

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
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
                        onClose(true);
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
                        onClose(false);
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

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());
                if (mPrompt != null) {
                    alert.setMessage(mPrompt);
                }

                final TimePicker timePicker = new TimePicker(context);
                timePicker.setIs24HourView(true);
                alert.setView(timePicker);
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(timePicker));
                        dialog.dismiss();
                        onClose(true);
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
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupDurationPicker(final Context context, TypedArray arr,
            CharSequence defaultValue) {
        if (defaultValue != null) {
            mValue.setText(defaultValue);
        } else {
            mValue.setText("");
        }

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());
                if (mPrompt != null) {
                    alert.setMessage(mPrompt);
                }

                final DurationPicker timePicker = new DurationPicker(context, null);
                timePicker.setEpochTime(SafeParse.parseSeconds(mValue.getText().toString(), 0));

                final LinearLayout layout = new LinearLayout(context);
                layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
                layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
                layout.addView(timePicker);
                alert.setView(layout);
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(timePicker));
                        dialog.dismiss();
                        onClose(true);
                    }

                    private String getValue(DurationPicker dp) {
                        return DateUtils.formatElapsedTime(timePicker.getEpochTime());
                    }
                });
                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupDistancePicker(final Context context, TypedArray arr,
            CharSequence defaultValue) {
        if (defaultValue != null) {
            mValue.setText(defaultValue);
        } else {
            mValue.setText("");
        }

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());
                if (mPrompt != null) {
                    alert.setMessage(mPrompt);
                }

                final DistancePicker distancePicker = new DistancePicker(context, null);
                distancePicker.setDistance((long) SafeParse.parseDouble(
                        mValue.getText().toString(), 0));

                final LinearLayout layout = new LinearLayout(context);
                layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
                layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
                layout.addView(distancePicker);
                alert.setView(layout);
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(distancePicker));
                        dialog.dismiss();
                        onClose(true);
                    }

                    private String getValue(DistancePicker dp) {
                        return Long.toString(dp.getDistance());
                    }
                });
                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupNumberPicker(final Context context, TypedArray arr, CharSequence defaultValue) {
        if (defaultValue != null) {
            mValue.setText(defaultValue);
        } else {
            mValue.setText("");
        }

        final NumberPicker numberPicker = new NumberPicker(context, null);
        numberPicker.processAttributes(arr);
        numberPicker.setOrientation(VERTICAL);

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());
                if (mPrompt != null) {
                    alert.setMessage(mPrompt);
                }

                numberPicker.setValue(SafeParse.parseInt(mValue.getText().toString(), 0));

                final LinearLayout layout = new LinearLayout(context);
                layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
                layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
                layout.addView(numberPicker);
                alert.setView(layout);
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(numberPicker));
                        dialog.dismiss();
                        layout.removeView(numberPicker);
                        onClose(true);
                    }

                    private String getValue(NumberPicker dp) {
                        return Integer.toString(dp.getValue());
                    }
                });
                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(numberPicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    public void setAdapter(SpinnerAdapter adapter) {
        mSpinner.setAdapter(adapter);
        loadValue(null);
    }

    public void setOnSetValueListener(OnSetValueListener listener) {
        this.mSetValueListener = listener;
    }

    public void setOnCloseDialogListener(OnCloseDialogListener listener) {
        this.mCloseDialogListener = listener;
    }

    private void onClose(boolean b) {
        if (mCloseDialogListener != null)
            mCloseDialogListener.onClose(this, b);
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
                    def = SafeParse.parseInt(defaultValue, 0);
                }
                setValue(pref.getInt(mKey, def));
                break;
            case TS_SPINNER_TXT:
            case TS_EDITTEXT:
            case TS_DURATIONPICKER:
            case TS_DISTANCEPICKER:
            case TS_NUMBERPICKER:
            case TS_DATEPICKER:
            case TS_TIMEPICKER:
                final String val = pref.getString(mKey, defaultValue == null ? "" : defaultValue);
                setValue(val);
                break;
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
        if (mType == Type.TS_SPINNER_TXT) {
            if (mSpinner.getAdapter() != null) {
                int intVal = find(mSpinner.getAdapter(), value);
                mSpinner.setSelection(intVal);
            }
        }

        if (mKey == null)
            return;
        Editor pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        pref.putString(mKey, value);
        pref.commit();
    }

    private int find(SpinnerAdapter adapter, String value) {
        for (int i = 0; i < adapter.getCount(); i++) {
            if (value.contentEquals(adapter.getItem(i).toString())) {
                return i;
            }
        }
        return 0;
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
        if (mSpinner.getAdapter() != null) {
            Object val = mSpinner.getAdapter().getItem(value);
            if (val != null)
                mValue.setText(val.toString());
            else
                mValue.setText("");
        }
        if (mKey == null)
            return;
        Editor pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        pref.putInt(mKey, value);
        pref.commit();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setEnabled(enabled);
        mSpinner.setEnabled(enabled);
    }

    public CharSequence getValue() {
        return mValue.getText();
    }

    public int getValueInt() {
        return mValueInt;
    }

    public void clear() {
        if (mKey != null) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().remove(mKey).commit();
        }
    }
}
