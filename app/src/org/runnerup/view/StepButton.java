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

package org.runnerup.view;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB.DIMENSION;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.widget.NumberPicker;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Range;
import org.runnerup.workout.Step;

@TargetApi(Build.VERSION_CODES.FROYO)
public class StepButton extends TableLayout {

    private final Context mContext;
    private final TableLayout mLayout;
    private final TextView mIntensity;
    private final TextView mDurationType;
    private final TextView mDurationValue;
    private final TextView mGoalType;
    private final TextView mGoalValue;
    private final TableRow mGoalRow;
    private final Formatter formatter;

    public Step getStep() {
        return step;
    }

    private Step step;
    private Runnable mOnChangedListener = null;

    static final boolean editRepeatCount = true;
    static final boolean editStepButton = true;

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

    public void setOnChangedListener(Runnable runnable) {
        mOnChangedListener = runnable;
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
            mDurationValue.setText(res.getString(R.string.Until_press));
        } else {
            mDurationType.setText(res.getString(durationType.getTextId()));
            mDurationValue.setText(formatter.format(Formatter.Format.TXT_LONG, durationType,
                    step.getDurationValue()));
        }

        Dimension goalType = step.getTargetType();
        if (goalType == null) {
            mGoalRow.setVisibility(View.GONE);
        } else {
            mGoalRow.setVisibility(View.VISIBLE);
            mGoalType.setText(res.getString(goalType.getTextId()));
            mGoalValue.setText(formatter.format(Formatter.Format.TXT_SHORT, goalType,
                    step.getTargetValue().minValue)
                    + "-" +
                    formatter.format(Formatter.Format.TXT_LONG, goalType, step.getTargetValue().maxValue));
        }
        if (editStepButton) {
            mLayout.setOnClickListener(onStepClickListener);
        }
    }

    final OnClickListener onRepeatClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final NumberPicker numberPicker = new NumberPicker(mContext, null);
            numberPicker.setOrientation(VERTICAL);
            numberPicker.setValue(step.getRepeatCount());

            AlertDialog.Builder alert = new AlertDialog.Builder(mContext);
            alert.setTitle(getResources().getString(R.string.repeat));

            final LinearLayout layout = new LinearLayout(mContext);
            layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            layout.addView(numberPicker);
            alert.setView(layout);
            alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    step.setRepeatCount(numberPicker.getValue());
                    dialog.dismiss();
                    setStep(step); // redraw
                    if (mOnChangedListener != null) {
                        mOnChangedListener.run();
                    }
                }
            });

            alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            AlertDialog dialog = alert.create();
            dialog.show();
        }
    };

    final OnClickListener onStepClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            AlertDialog.Builder alert = new AlertDialog.Builder(mContext);
            alert.setTitle(getResources().getString(R.string.Edit_step));

            final LayoutInflater inflater = LayoutInflater.from(mContext);
            final View layout = inflater.inflate(
                    R.layout.step_dialog, null);

            final Runnable save = setupEditStep(inflater, layout);
            alert.setView(layout);
            alert.setPositiveButton(getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    save.run();
                    dialog.dismiss();
                    setStep(step); // redraw
                    if (mOnChangedListener != null) {
                        mOnChangedListener.run();
                    }
                }
            });
            alert.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            AlertDialog dialog = alert.create();
            dialog.show();
        }
    };

    Runnable setupEditStep(LayoutInflater inflator, View layout) {
        final TitleSpinner stepType = (TitleSpinner) layout.findViewById(R.id.step_intensity);
        stepType.setValue(step.getIntensity().getValue());

        final HRZonesListAdapter hrZonesAdapter = new HRZonesListAdapter(mContext, inflator);
        final TitleSpinner durationType = (TitleSpinner) layout.findViewById(R.id.step_duration_type);
        final TitleSpinner durationTime = (TitleSpinner) layout.findViewById(R.id.step_duration_time);
        final TitleSpinner durationDistance = (TitleSpinner) layout.findViewById(R.id.step_duration_distance);
        durationType.setOnSetValueListener(new TitleSpinner.OnSetValueListener() {
            @Override
            public String preSetValue(String newValue) throws IllegalArgumentException {
                return null;
            }

            @Override
            public int preSetValue(int newValue) throws IllegalArgumentException {
                switch (newValue) {
                    case DIMENSION.TIME:
                        durationTime.setEnabled(true);
                        durationTime.setVisibility(View.VISIBLE);
                        durationTime.setValue(formatter.formatElapsedTime(Formatter.Format.TXT,
                                (long) step.getDurationValue()));
                        durationDistance.setVisibility(View.GONE);
                        break;
                    case DIMENSION.DISTANCE:
                        durationTime.setVisibility(View.GONE);
                        durationDistance.setEnabled(true);
                        durationDistance.setVisibility(View.VISIBLE);
                        durationDistance.setValue(Long.toString((long) step.getDurationValue()));
                        break;
                    default:
                        durationTime.setEnabled(false);
                        durationDistance.setEnabled(false);
                        break;
                }
                return newValue;
            }
        });
        if (step.getDurationType() == null) {
            durationType.setValue(-1);
        } else {
            durationType.setValue(step.getDurationType().getValue());
        }

        final TitleSpinner targetType = (TitleSpinner) layout.findViewById(R.id.step_target_type);
        final TitleSpinner targetPaceLo = (TitleSpinner) layout.findViewById(R.id.step_target_pace_lo);
        final TitleSpinner targetPaceHi = (TitleSpinner) layout.findViewById(R.id.step_target_pace_hi);
        final TitleSpinner targetHrz = (TitleSpinner) layout.findViewById(R.id.step_target_hrz);

        if (!hrZonesAdapter.hrZones.isConfigured()) {
            targetType.addDisabledValue(DIMENSION.HRZ);
        } else {
            targetHrz.setAdapter(hrZonesAdapter);
        }

        targetType.setOnSetValueListener(new TitleSpinner.OnSetValueListener() {
            @Override
            public String preSetValue(String newValue) throws IllegalArgumentException {
                return null;
            }

            @Override
            public int preSetValue(int newValue) throws IllegalArgumentException {
                Range target = step.getTargetValue();
                switch (newValue) {
                    case DIMENSION.PACE:
                        targetPaceLo.setEnabled(true);
                        targetPaceHi.setEnabled(true);
                        targetPaceLo.setVisibility(View.VISIBLE);
                        targetPaceHi.setVisibility(View.VISIBLE);
                        if (target != null) {
                            targetPaceLo.setValue(formatter.formatPace(Formatter.Format.TXT_SHORT,
                                    target.minValue));
                            targetPaceHi.setValue(formatter.formatPace(Formatter.Format.TXT_SHORT,
                                    target.maxValue));
                        }
                        targetHrz.setVisibility(View.GONE);
                        break;
                    case DIMENSION.HR:
                    case DIMENSION.HRZ:
                        targetPaceLo.setVisibility(View.GONE);
                        targetPaceHi.setVisibility(View.GONE);
                        targetHrz.setEnabled(true);
                        targetHrz.setVisibility(View.VISIBLE);
                        if (target != null) {
                            targetHrz.setValue(hrZonesAdapter.hrZones.match(target.minValue,
                                    target.maxValue));
                        } else {
                            targetHrz.setValue(0);
                        }
                        break;
                    default:
                        targetPaceLo.setEnabled(false);
                        targetPaceHi
                                .setEnabled(false);
                        targetHrz.setEnabled(false);
                        break;
                }
                return newValue;
            }
        });
        if (step.getTargetType() == null) {
            targetType.setValue(-1);
        } else if (step.getTargetType().getValue() == DIMENSION.HR) {
            targetType.setValue(DIMENSION.HRZ);
        } else {
            targetType.setValue(step.getTargetType().getValue());
        }

        return new Runnable() {

            @Override
            public void run() {
                step.setIntensity(Intensity.valueOf(stepType.getValueInt()));
                step.setDurationType(Dimension.valueOf(durationType.getValueInt()));
                switch (durationType.getValueInt()) {
                    case DIMENSION.DISTANCE:
                        step.setDurationValue(SafeParse.parseDouble(
                                durationDistance.getValue().toString(), 1000));
                        break;
                    case DIMENSION.TIME:
                        step.setDurationValue(SafeParse.parseSeconds(
                                durationTime.getValue().toString(), 60));
                        break;
                }
                step.setTargetType(Dimension.valueOf(targetType.getValueInt()));
                switch (targetType.getValueInt()) {
                    case DIMENSION.PACE: {
                        double unitMeters = Formatter.getUnitMeters(mContext);
                        double paceLo = (double) SafeParse.parseSeconds(
                                targetPaceLo.getValue().toString(), 5 * 60);
                        double paceHi = (double) SafeParse.parseSeconds(
                                targetPaceHi.getValue().toString(), 5 * 60);
                        step.setTargetValue(paceLo / unitMeters, paceHi / unitMeters);
                        break;
                    }
                    case DIMENSION.HRZ:
                        step.setTargetType(Dimension.HR);
                        Pair<Integer, Integer> range = hrZonesAdapter.hrZones.getHRValues(
                                targetHrz.getValueInt() + 1);
                        step.setTargetValue(range.first, range.second);
                }
            }
        };
    }
}
