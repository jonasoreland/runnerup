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

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

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

import java.util.Locale;

public class StepButton extends LinearLayout {


    private final Context mContext;
    private final ViewGroup mLayout;
    private final ImageView mIntensityIcon;
    private final TextView mDurationValue;
    private final TextView mGoalValue;
    private final Formatter formatter;

    public Step getStep() {
        return step;
    }

    private Step step;
    private Runnable mOnChangedListener = null;

    private static final boolean editRepeatCount = true;
    private static final boolean editStepButton = true;

    public StepButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.step_button, this);
        formatter = new Formatter(context);
        mLayout = findViewById(R.id.step_button_layout);
        mIntensityIcon = findViewById(R.id.step_icon);
        mDurationValue = findViewById(R.id.step_duration_value);
        mGoalValue = findViewById(R.id.step_goal_value);
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

        mDurationValue.setVisibility(VISIBLE);
        switch (step.getIntensity()) {
            case ACTIVE:
                mIntensityIcon.setImageResource(R.drawable.step_active);
                mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepActive)); //todo check if it works
                break;
            case RESTING:
                mIntensityIcon.setImageResource(R.drawable.step_resting);
                mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepResting));
                break;
            case REPEAT:
                mIntensityIcon.setImageResource(R.drawable.step_repeat);
                mDurationValue.setVisibility(GONE); //todo better wording in string
                mGoalValue.setText(String.format(Locale.getDefault(), getResources().getString(R.string.repeat_times), step.getRepeatCount()));
                mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepRepeat));
                if (editRepeatCount)
                    mLayout.setOnClickListener(onRepeatClickListener);
                return;
            case WARMUP:
                mIntensityIcon.setImageResource(R.drawable.step_warmup);
                mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepWarmup));
                break;
            case COOLDOWN:
                mIntensityIcon.setImageResource(R.drawable.step_cooldown);
                mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepCooldown));
                break;
            case RECOVERY:
                mIntensityIcon.setImageResource(R.drawable.step_recovery);
                mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepRecovery));
                break;
            default:
                mIntensityIcon.setImageResource(0);
        }

        Dimension durationType = step.getDurationType();
        if (durationType == null) {
            mDurationValue.setText(R.string.Until_press);
        } else {
            mDurationValue.setText(formatter.format(Formatter.Format.TXT_LONG, durationType,
                    step.getDurationValue()));
        }

        Dimension goalType = step.getTargetType();
        if (goalType == null) {
            mGoalValue.setText(step.getIntensity().getTextId());
        } else {
            String prefix;
            if (goalType == Dimension.HR || goalType == Dimension.HRZ)
                prefix = "HR "; //todo should use a string
            else
                prefix = "";

            mGoalValue.setText((String.format(Locale.getDefault(), "%s%s-%s",
                    prefix,
                    formatter.format(Formatter.Format.TXT_SHORT, goalType, step.getTargetValue().minValue),
                    formatter.format(Formatter.Format.TXT_LONG, goalType, step.getTargetValue().maxValue))));
        }
        if (editStepButton) {
            mLayout.setOnClickListener(onStepClickListener);
        }
    }

    private final OnClickListener onRepeatClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final NumberPicker numberPicker = new NumberPicker(mContext, null);
            numberPicker.setOrientation(VERTICAL);
            numberPicker.setDigits(4);
            numberPicker.setRange(0, 9999, true);
            numberPicker.setValue(step.getRepeatCount());

            final LinearLayout layout = new LinearLayout(mContext);
            layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            layout.addView(numberPicker);

            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.repeat)
                    .setView(layout)
                    .setPositiveButton(R.string.OK, (dialog, whichButton) -> {
                        step.setRepeatCount(numberPicker.getValue());
                        dialog.dismiss();
                        setStep(step); // redraw
                        if (mOnChangedListener != null) {
                            mOnChangedListener.run();
                        }
                    })
                    .setNegativeButton(R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
                    .show();
        }
    };

    private final OnClickListener onStepClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final LayoutInflater inflater = LayoutInflater.from(mContext);
            @SuppressLint("InflateParams") final View layout = inflater.inflate(
                    R.layout.step_dialog, null);

            final Runnable save = setupEditStep(inflater, layout);

            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.Edit_step)
                    .setView(layout)
                    .setPositiveButton(R.string.OK, (dialog, whichButton) -> {
                        save.run();
                        dialog.dismiss();
                        setStep(step); // redraw
                        if (mOnChangedListener != null) {
                            mOnChangedListener.run();
                        }
                    })
                    .setNegativeButton(R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
                    .show();
        }
    };

    private Runnable setupEditStep(LayoutInflater inflator, View layout) {
        final TitleSpinner stepType = layout.findViewById(R.id.step_dialog_intensity);
        stepType.setValue(step.getIntensity().getValue());

        final HRZonesListAdapter hrZonesAdapter = new HRZonesListAdapter(mContext, inflator);
        final TitleSpinner durationType = layout.findViewById(R.id.step_dialog_duration_type);
        final TitleSpinner durationTime = layout.findViewById(R.id.step_dialog_duration_time);
        final TitleSpinner durationDistance = layout.findViewById(R.id.step_dialog_duration_distance);
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

        final TitleSpinner targetType = layout.findViewById(R.id.step_dialog_target_type);
        final TitleSpinner targetPaceLo = layout.findViewById(R.id.step_dialog_target_pace_lo);
        final TitleSpinner targetPaceHi = layout.findViewById(R.id.step_dialog_target_pace_hi);
        final TitleSpinner targetHrz = layout.findViewById(R.id.step_dialog_target_hrz);

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
                            // the zones are off (i) due to 0-base in java arrays
                            // to 1 base in user HR zones numbering, and (b) there are 2 values
                            // per zone (min and max values)
                            int matchedZone = hrZonesAdapter.hrZones.match(target.minValue,
                                    target.maxValue) - 2;
                            if (matchedZone < 0) {
                                matchedZone = 0;
                            }
                            targetHrz.setValue(matchedZone);
                        } else {
                            targetHrz.setValue(0);
                        }
                        break;
                    default:
                        targetPaceLo.setEnabled(false);
                        targetPaceHi.setEnabled(false);
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

        return () -> {
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
        };
    }
}
