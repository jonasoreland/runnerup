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
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Step;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import android.os.Build;
import android.annotation.TargetApi;

@TargetApi(Build.VERSION_CODES.FROYO)
public class StepButton extends TableLayout {

    private Context mContext;
    private TableLayout mLayout;
    private TextView mIntensity;
    private TextView mDurationType;
    private TextView mDurationValue;
    private TextView mGoalType;
    private TextView mGoalValue;
    private TableRow mGoalRow;
    private Formatter formatter;
    private Step step;

    static final boolean editRepeatCount = true;

    public StepButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.step_button, this);
        formatter = new Formatter(context);
        mLayout = (TableLayout) findViewById(R.id.step_button);
        mIntensity = (TextView) findViewById(R.id.step_intensity);
        mDurationType = (TextView) findViewById(R.id.step_duration_type);
        mDurationValue = (TextView) findViewById(R.id.step_duration_value);
        mGoalType = (TextView) findViewById(R.id.step_goal_type);
        mGoalValue = (TextView) findViewById(R.id.step_goal_value);
        mGoalRow = (TableRow) findViewById(R.id.step_row1);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mLayout.setEnabled(enabled);
        for (int i = 0, j = mLayout.getChildCount(); i < j; i++) {
            mLayout.getChildAt(i).setEnabled(enabled);
        }
    }

    public void setStep(Step step) {
        this.step = step;
        Resources res = getResources();
        mIntensity.setText(res.getText(step.getIntensity().getTextId()));
        if (step.getIntensity() == Intensity.REPEAT) {
            mDurationType.setText("");
            mDurationValue.setText("" + step.getRepeatCount());
            mGoalRow.setVisibility(View.GONE);
            if (editRepeatCount)
                mLayout.setOnClickListener(onRepeatClickListener);
            return;
        }
        Dimension durationType = step.getDurationType();
        if (durationType == null) {
            mDurationType.setText("");
            mDurationValue.setText("Until press");
        } else {
            mDurationType.setText(res.getString(durationType.getTextId()));
            mDurationValue.setText(formatter.format(Formatter.TXT_LONG, durationType,
                    step.getDurationValue()));
        }

        Dimension goalType = step.getTargetType();
        if (goalType == null) {
            mGoalRow.setVisibility(View.GONE);
        } else {
            mGoalRow.setVisibility(View.VISIBLE);
            mGoalType.setText(res.getString(goalType.getTextId()));
            mGoalValue.setText(formatter.format(Formatter.TXT_SHORT, goalType,
                    step.getTargetValue().minValue)
                    + "-" +
                    formatter.format(Formatter.TXT_LONG, goalType, step.getTargetValue().maxValue));
        }
    }

    OnClickListener onRepeatClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final NumberPicker numberPicker = new NumberPicker(mContext, null);
            numberPicker.setOrientation(VERTICAL);
            numberPicker.setValue(step.getRepeatCount());

            AlertDialog.Builder alert = new AlertDialog.Builder(mContext);
            alert.setTitle("Repeat");

            final LinearLayout layout = new LinearLayout(mContext);
            layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            layout.addView(numberPicker);
            alert.setView(layout);
            alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    step.setRepeatCount(numberPicker.getValue());
                    dialog.dismiss();
                    setStep(step); // redraw
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
    };
}

