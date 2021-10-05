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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.runnerup.R;


public class NumberPicker extends LinearLayout {

    public interface OnChangedListener {
        void onChanged(NumberPicker picker, int oldVal, int newVal);
    }

    public interface Formatter {
        String toString(int value);
    }

    private int prevValue;
    private int currValue;
    private int minValue = MIN_VAL;
    private int maxValue = MAX_VAL;
    private boolean wrapValue = true;

    private final static int DIGITS = 2;
    private final static int MIN_VAL = 0;
    private final static int MAX_VAL = 59;

    private EditText valueText;
    private OnChangedListener listener;

    private Button decButton;
    private Button incButton;

    private boolean longInc = false;
    private boolean longDec = false;
    private final Handler longHandler = new Handler();
    private final int textSize = 25;
    private int digits = DIGITS;
    private String fmtString = "%0" + digits + "d";

    public NumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        createValueText(context);
        createButton(context, '+');
        createButton(context, '-');

        setPadding(5, 5, 5, 5);
        setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        addViews();
        updateView();

        if (attrs != null) {
            TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.NumberPicker);
            processAttributes(arr);
            arr.recycle();
        }
    }

    private void processAttributes(TypedArray arr) {
        if (arr == null)
            return;

        if (arr.hasValue(R.styleable.NumberPicker_digits)) {
            setDigits(arr.getInt(R.styleable.NumberPicker_digits, digits));
        }
        if (arr.hasValue(R.styleable.NumberPicker_min_val)) {
            minValue = arr.getInt(R.styleable.NumberPicker_min_val, minValue);
        }
        if (arr.hasValue(R.styleable.NumberPicker_max_val)) {
            maxValue = arr.getInt(R.styleable.NumberPicker_max_val, maxValue);
        }
    }

    private void addViews() {
        LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        if (this.getOrientation() == VERTICAL) {
            addView(incButton, lp);
            addView(valueText, lp);
            addView(decButton, lp);
        } else {
            addView(decButton, lp);
            addView(valueText, lp);
            addView(incButton, lp);
        }
    }

    private void createButton(Context context, char c) {
        Button b = new Button(context);
        b.setText(Character.toString(c));
        b.setTextSize(textSize);
        b.setOnClickListener(buttonClick);
        b.setOnLongClickListener(buttonLongClick);
        b.setOnTouchListener(buttonLongTouchListener);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        if (c == '+')
            incButton = b;
        else
            decButton = b;
    }

    private void createValueText(Context context) {
        valueText = new EditText(context);
        valueText.setTextSize(textSize);
        valueText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                valueText.selectAll();
            } else {
                validateInput(valueText);
            }
        });
        valueText.setInputType(InputType.TYPE_CLASS_NUMBER);
        valueText.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
    }

    private final Runnable longPressUpdater = new Runnable() {
        public void run() {
            if (longInc) {
                setValueImpl(currValue + 1);
            } else if (longDec) {
                setValueImpl(currValue - 1);
            } else {
                return;
            }
            long longSpeed = 50;
            longHandler.postDelayed(this, longSpeed);
        }
    };

    private void setValueImpl(int newValue) {
        if (newValue < minValue) {
            if (wrapValue)
                newValue = maxValue;
            else
                newValue = minValue;
        } else if (newValue > maxValue) {
            if (wrapValue)
                newValue = minValue;
            else
                newValue = maxValue;
        }
        int save = prevValue;
        prevValue = currValue;
        currValue = newValue;
        if (listener != null)
            listener.onChanged(this, save, newValue);
        updateView();
    }

    private void updateView() {
        valueText.setText(formatter.toString(currValue));
        valueText.selectAll();
    }

    private final OnClickListener buttonClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            validateInput(valueText);
            if (!valueText.hasFocus())
            {
                valueText.requestFocus();
            }
            int diff = v == incButton ? 1 : -1;
            setValueImpl(currValue + diff);
        }
    };

    private void buttonLongClick(int i) {
        valueText.clearFocus();
        if (i < 0) {
            longDec = true;
        } else if (i > 0) {
            longInc = true;
        } else {
            longInc = false;
            longDec = false;
            return;
        }

        longHandler.post(longPressUpdater);
    }

    private final OnLongClickListener buttonLongClick = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (v == incButton)
                buttonLongClick(+1);
            else
                buttonLongClick(-1);
            return true;
        }
    };

    private final OnTouchListener buttonLongTouchListener = new OnTouchListener() {
        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP &&
                    ((longInc && v == incButton) ||
                    (longDec && v == decButton))) {
                buttonLongClick(0);
                return true;
            }
            return false;
        }
    };

    private void validateInput(EditText tv) {
        String str = String.valueOf(tv.getText());
        if ("".equals(str)) {
            updateView();
        } else {
            try {
                int l = Integer.parseInt(str);
                setValueImpl(l);
            } catch (NumberFormatException ex) {
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        incButton.setEnabled(enabled);
        decButton.setEnabled(enabled);
        valueText.setEnabled(enabled);
        if (!enabled) {
            longInc = false;
            longDec = false;
        }
    }

    @Override
    public void setOrientation(int orientation) {
        if (getOrientation() != orientation) {
            super.setOrientation(orientation);
            readd();
        }
    }

    private final Formatter formatter = new Formatter() {
        final StringBuilder builder = new StringBuilder();
        final java.util.Formatter fmt = new java.util.Formatter(builder);
        final Object[] args = new Object[1];

        public String toString(int value) {
            args[0] = value;
            builder.delete(0, builder.length());
            fmt.format(fmtString, args);
            return fmt.toString();
        }
    };

    public void setRange(int min, int max, boolean wrap) {
        this.minValue = min;
        this.maxValue = max;
        this.wrapValue = wrap;
    }

    public void setDigits(int digits) {
        this.digits = digits;
        fmtString = "%0" + digits + "d";
        updateView();
        readd();
    }

    public void setValue(int newValue) {
        setValueImpl(newValue);
    }

    public int getValue() {
        validateInput(valueText);
        return currValue;
    }

    private void readd() {
        removeView(incButton);
        removeView(decButton);
        removeView(valueText);
        addViews();
    }
}
