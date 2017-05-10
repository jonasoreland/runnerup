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

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.TimePicker;

import org.runnerup.R;
import org.runnerup.util.SafeParse;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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
    }

    private String mKey = null;
    private TextView mTitle = null;
    private TextView mValue = null;
    private Spinner mSpinner = null;
    int mInputType = 0;
    private final Context mContext;
    private OnSetValueListener mSetValueListener = null;
    private OnCloseDialogListener mCloseDialogListener = null;
    private Type mType = null;
    private boolean mFirstSetValue = true;
    private int values[] = null;
    private long mCurrValue = -1;

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
    }

    public interface OnCloseDialogListener {
        public void onClose(TitleSpinner spinner, boolean ok);
    }

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
        //Note: R.styleable.TitleSpinner_android_prompt is not used

        CharSequence type = arr.getString(R.styleable.TitleSpinner_type);
        CharSequence defaultValue = arr.getString(R.styleable.TitleSpinner_android_defaultValue);

        if (type == null || "spinner".contentEquals(type)) {
            mType = Type.TS_SPINNER;
            setupSpinner(context, attrs, arr, defaultValue);
        } else if ("spinner_txt".contentEquals(type)) {
            mType = Type.TS_SPINNER_TXT;
            setupSpinner(context, attrs, arr, defaultValue);
        } else if ("edittext".contentEquals(type)) {
            mType = Type.TS_EDITTEXT;
            setupEditText(context, attrs, arr, defaultValue);
        } else if ("datepicker".contentEquals(type)) {
            mType = Type.TS_DATEPICKER;
            setupDatePicker(context, attrs, arr, defaultValue);
        } else if ("timepicker".contentEquals(type)) {
            mType = Type.TS_TIMEPICKER;
            setupTimePicker(context, attrs, arr, defaultValue);
        } else if ("durationpicker".contentEquals(type)) {
            mType = Type.TS_DURATIONPICKER;
            setupDurationPicker(context, attrs, arr, defaultValue);
        } else if ("distancepicker".contentEquals(type)) {
            mType = Type.TS_DISTANCEPICKER;
            setupDistancePicker(context, attrs, arr, defaultValue);
        } else if ("numberpicker".contentEquals(type)) {
            mType = Type.TS_NUMBERPICKER;
            setupNumberPicker(context, attrs, arr, defaultValue);
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

    private void setupEditText(final Context context, final AttributeSet attrs, TypedArray arr, CharSequence defaultValue) {
        mInputType = arr.getInt(R.styleable.TitleSpinner_android_inputType,
                EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_FLAG_DECIMAL);
        setValue(defaultValue, false);

        final EditText edit = new EditText(context, attrs);
        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());

                edit.setText(mValue.getText());
                edit.setInputType(mInputType);
                final LinearLayout layout = createLayout(context);
                layout.addView(edit);
                alert.setView(layout);
                alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(edit.getText().toString());
                        dialog.dismiss();
                        layout.removeView(edit);
                        onClose(true);
                    }
                });
                alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(edit);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupSpinner(Context context, AttributeSet attrs, TypedArray arr, CharSequence defaultValue) {
        mSpinner.setPrompt(mTitle.getText());

        int entriesId = arr.getResourceId(R.styleable.TitleSpinner_android_entries, 0);
        int valuesId = arr.getResourceId(R.styleable.TitleSpinner_values, 0);
        if (valuesId != 0) {
            values = getResources().getIntArray(valuesId);
        }
        if (entriesId != 0) {
            DisabledEntriesAdapter adapter = new DisabledEntriesAdapter(mContext, entriesId);
            mSpinner.setAdapter(adapter);
            int value = 0;
            if (defaultValue != null) {
                value = SafeParse.parseInt(defaultValue.toString(), 0);
            }
            setValue(value);
//            if (value >= 0 && value < entries.length) {
//                mValueInt = value;
//                mValue.setText(entries[value]);
//            }
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
                    setValue(getRealValue(arg2));
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

    private static LinearLayout createLayout(Context context) {
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        return layout;
    }

    private void setupDatePicker(final Context context, AttributeSet attrs, TypedArray arr, CharSequence defaultValue) {
        if (defaultValue != null && "today".contentEquals(defaultValue)) {
            DateFormat df = android.text.format.DateFormat.getDateFormat(context);
            defaultValue = df.format(new Date());
        }
        setValue(defaultValue, false);

        final DatePicker datePicker = new DatePicker(context, attrs);

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());

                final LinearLayout layout = createLayout(context);
                layout.addView(datePicker);
                alert.setView(layout);
                alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(datePicker));
                        dialog.dismiss();
                        layout.removeView(datePicker);
                        onClose(true);
                    }

                    private String getValue(DatePicker dp) {
                        Calendar c = Calendar.getInstance();
                        c.set(dp.getYear(), dp.getMonth(), dp.getDayOfMonth());
                        DateFormat df = android.text.format.DateFormat.getDateFormat(context);
                        return df.format(c.getTime());
                    }
                });
                alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(datePicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupTimePicker(final Context context, AttributeSet attrs, TypedArray arr, CharSequence defaultValue) {
        if (defaultValue != null && "now".contentEquals(defaultValue)) {
            DateFormat df = android.text.format.DateFormat.getTimeFormat(context);
            defaultValue = df.format(new Date());
        }
        setValue(defaultValue, false);

        final TimePicker timePicker = new TimePicker(context, attrs);

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());

                timePicker.setIs24HourView(true);
                final LinearLayout layout = createLayout(context);
                layout.addView(timePicker);
                alert.setView(layout);
                alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(timePicker));
                        dialog.dismiss();
                        layout.removeView(timePicker);
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
                alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(timePicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupDurationPicker(final Context context, final AttributeSet attrs, TypedArray arr,
                                     CharSequence defaultValue) {
        setValue(defaultValue, false);

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());

                final DurationPicker picker = new DurationPicker(context, attrs);
                picker.setEpochTime(mCurrValue);
                final LinearLayout layout = createLayout(context);
                layout.addView(picker);
                alert.setView(layout);
                alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(picker));
                        dialog.dismiss();
                        layout.removeView(picker);
                        onClose(true);
                    }

                    private String getValue(DurationPicker dp) {
                        return DateUtils.formatElapsedTime(picker.getEpochTime());
                    }
                });
                alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(picker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupDistancePicker(final Context context, AttributeSet attrs, TypedArray arr,
                                     CharSequence defaultValue) {
        setValue(defaultValue, false);

        final DistancePicker distancePicker = new DistancePicker(context, attrs);

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());

                distancePicker.setDistance(mCurrValue);

                final LinearLayout layout = createLayout(context);
                layout.addView(distancePicker);
                alert.setView(layout);
                alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(distancePicker));
                        dialog.dismiss();
                        layout.removeView(distancePicker);
                        onClose(true);
                    }

                    private String getValue(DistancePicker dp) {
                        return Long.toString(dp.getDistance());
                    }
                });
                alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(distancePicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupNumberPicker(final Context context, AttributeSet attrs, final TypedArray arr, CharSequence defaultValue) {
        setValue(defaultValue, false);

        final NumberPicker numberPicker = new NumberPicker(context, attrs);
        numberPicker.setOrientation(VERTICAL);

        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mTitle.getText());

                numberPicker.setValue((int)mCurrValue);

                final LinearLayout layout = createLayout(context);
                layout.addView(numberPicker);
                alert.setView(layout);
                alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
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
                alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
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
        setValue (value, true);
    }

    public void setValue(CharSequence value, Boolean savePreferences) {
        String str = value == null ? "" : value.toString();
        setValue(str, savePreferences);
    }

    public void setValue(String value, Boolean savePreferences) {
        if (mSetValueListener != null) {
            try {
                value = mSetValueListener.preSetValue(value);
            } catch (java.lang.IllegalArgumentException ex) {
                return;
            }
        }

        //Store the value - could be separate for distance vs time
        if (value == null) {
            mCurrValue = 0;
        } else if (mType == Type.TS_DURATIONPICKER) {
            mCurrValue = SafeParse.parseSeconds(value, 0);
        } else {
            mCurrValue = (long) SafeParse.parseDouble(value.toString(), 0);
        }
        if (mType == Type.TS_DISTANCEPICKER && !TextUtils.isEmpty(value)) {
            mValue.setText(String.format("%s %s", value, getResources().getString(R.string.metrics_distance_m)));
        } else {
            mValue.setText(value);
        }
        if (mType == Type.TS_SPINNER_TXT) {
            if (mSpinner.getAdapter() != null) {
                int intVal = find(mSpinner.getAdapter(), value);
                mSpinner.setSelection(intVal);
            }
        }

        if (mKey == null || !savePreferences)
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

    int getSelectionValue(int value) {
        if (values == null)
            return value;
        int p = 0;
        for (int v : values) {
            if (v == value)
                return p;
            p++;
        }

        /* not found, hmm...what to do... */
        return 0;
    }

    int getRealValue(int value) {
        if (values == null)
            return value;
        if (value >= 0 && value < values.length)
            return values[value];

        /* invalid value, hmm...what to do... */
        return values[0];
    }

    public void setValue(int value) {
        if (mSetValueListener != null) {
            try {
                value = mSetValueListener.preSetValue(value);
            } catch (java.lang.IllegalArgumentException ex) {
                if ((int)mCurrValue != -1) {
                    mSpinner.setSelection((int)mCurrValue);
                }
                return;
            }
        }
        mCurrValue = value;
        int selectionValue = getSelectionValue(value);
        mSpinner.setSelection(selectionValue);
        if (mSpinner.getAdapter() != null) {
            Object val = mSpinner.getAdapter().getItem(selectionValue);
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

    public void addDisabledValue(int value) {
        int selection = getSelectionValue(value);
        ((DisabledEntriesAdapter)mSpinner.getAdapter()).addDisabled(selection);
    }

    public void clearDisabled() {
        ((DisabledEntriesAdapter)mSpinner.getAdapter()).clearDisabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        LinearLayout layout = (LinearLayout) findViewById(R.id.title_spinner);
        layout.setEnabled(enabled);
        mSpinner.setEnabled(enabled);
    }

    public CharSequence getValue() {
        switch(mType) {
            case TS_SPINNER_TXT:
            case TS_EDITTEXT:
                return mValue.getText();
            case TS_DATEPICKER:
            case TS_TIMEPICKER:
            case TS_DURATIONPICKER:
            case TS_DISTANCEPICKER:
            case TS_NUMBERPICKER:
            case TS_SPINNER:
                break;
        }
        return String.format(Locale.getDefault(), "%d", mCurrValue);
    }

    public int getValueInt() {
        return (int)mCurrValue;
    }

    public void clear() {
        if (mKey != null) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().remove(mKey).commit();
        }
    }
}
