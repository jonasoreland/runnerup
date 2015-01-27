package org.runnerup.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;

import org.json.JSONException;
import org.runnerup.R;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.workout.RepeatStep;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutSerializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CreateAdvancedWorkout extends Activity {

    Workout advancedWorkout = null;
    TitleSpinner advancedWorkoutSpinner = null;
    Button addStepButton = null;
    Button addRepeatButton = null;
    Button saveWorkoutButton = null;
    Button discardWorkoutButton = null;
    ListView advancedStepList = null;
    final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.create_advanced_workout);

        Intent intent = getIntent();
        String advWorkoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME);

        advancedWorkoutSpinner = (TitleSpinner) findViewById(R.id.new_workout_spinner);
        advancedWorkoutSpinner.setValue(advWorkoutName + ".json");
        advancedWorkoutSpinner.setEnabled(false);

        advancedStepList = (ListView) findViewById(R.id.new_advnced_workout_steps);
        advancedStepList.setDividerHeight(0);
        advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

        addStepButton = (Button) findViewById(R.id.add_step_button);
        addStepButton.setOnClickListener(addStepButtonClick);

        addRepeatButton = (Button) findViewById(R.id.add_repeat_button);
        addRepeatButton.setOnClickListener(addRepeatStepButtonClick);

        saveWorkoutButton = (Button) findViewById(R.id.workout_save_button);
        saveWorkoutButton.setOnClickListener(saveWorkoutButtonClick);

        discardWorkoutButton = (Button) findViewById(R.id.workout_discard_button);
        discardWorkoutButton.setOnClickListener(discardWorkoutButtonClick);

        try {
            createAdvancedWorkout(advWorkoutName);
        } catch (Exception e) {
            handleWorkoutFileException(e);
        }
    }

    private void createAdvancedWorkout(String name) throws JSONException, IOException {
        advancedWorkout = new Workout();
        WorkoutSerializer.writeFile(getApplicationContext(), name + ".json", advancedWorkout);
        advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
        advancedWorkoutStepsAdapter.notifyDataSetChanged();
    }

    final class WorkoutStepsAdapter extends BaseAdapter {

        List<Workout.StepListEntry> steps = new ArrayList<Workout.StepListEntry>();

        @Override
        public int getCount() {
            return steps.size();
        }

        @Override
        public Object getItem(int position) {
            return steps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Workout.StepListEntry entry = steps.get(position);
            StepButton button = null;
            if (convertView != null && convertView instanceof StepButton) {
                button = (StepButton) convertView;
            } else {
                button = new StepButton(CreateAdvancedWorkout.this, null);
            }
            button.setStep(entry.step);
            button.setPadding(entry.level * 7, 0, 0, 0);
            button.setOnChangedListener(onWorkoutChanged);

            if(entry.step instanceof RepeatStep) {
                button.setOnLongClickListener(onRepeatButtonLongClick);
            } else {
                button.setOnLongClickListener(onButtonLongClick);
            }
            button.setSelected(true);
            return button;
        }
    }

    final View.OnLongClickListener onRepeatButtonLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {

            final StepButton clicked = (StepButton) view.getParent();

            AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);
            builder.setTitle("Choose action");
            builder.setPositiveButton("Delete Step",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            deleteStep(clicked);
                            return;
                        }
                    });
            builder.setNegativeButton("Add step",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            addStep(clicked);
                            return;
                        }

                    });
            builder.show();

            return true;
        }

        private void deleteStep(StepButton button) {
            Step s = button.getStep();
            for(Step se : advancedWorkout.getSteps()) {
                if(se instanceof RepeatStep) {
                    for(Step subStep : ((RepeatStep) se).getSteps()) {
                        if(subStep.equals(s)) {
                            ((RepeatStep) se).getSteps().remove(s);
                            break;
                        }
                    }
                }
                if (se.equals(s)) {
                    advancedWorkout.getSteps().remove(se);
                    break;
                }
            }

            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }

        private void  addStep(StepButton button) {
            Step s = new Step();
            RepeatStep rs = (RepeatStep) button.getStep();
            rs.getSteps().add(s);
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }

    };

    final View.OnLongClickListener onButtonLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            StepButton clicked = (StepButton) view.getParent();
            Step s = clicked.getStep();

            for(Step se : advancedWorkout.getSteps()) {
                if(se instanceof RepeatStep) {
                    for(Step subStep : ((RepeatStep) se).getSteps()) {
                        if(subStep.equals(s)) {
                            ((RepeatStep) se).getSteps().remove(s);
                            break;
                        }
                    }
                }
                if (se.equals(s)) {
                    advancedWorkout.getSteps().remove(se);
                    break;
                }
            }
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
            return true;
        }
    };

    final Runnable onWorkoutChanged = new Runnable() {
        @Override
        public void run() {
            String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
            if (advancedWorkout != null) {
                Context ctx = getApplicationContext();
                try {
                    WorkoutSerializer.writeFile(ctx, advWorkoutName, advancedWorkout);
                } catch (Exception ex) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);
                    builder.setTitle(getString(R.string.failed_to_load_workout));
                    builder.setMessage("" + ex.toString());
                    builder.setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    builder.show();
                    return;
                }
            }
        }
    };

    final View.OnClickListener addStepButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            advancedWorkout.addStep(new Step());
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }
    };

    final View.OnClickListener addRepeatStepButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            advancedWorkout.addStep(new RepeatStep());
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }
    };

    final View.OnClickListener saveWorkoutButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
                WorkoutSerializer.writeFile(getApplicationContext(), advWorkoutName, advancedWorkout);
                finish();
            } catch (Exception e) {
                handleWorkoutFileException(e);
            }
        }
    };

    private void handleWorkoutFileException(Exception e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);
        builder.setTitle(getString(R.string.failed_to_create_workout));
        builder.setMessage("" + e.toString());
        builder.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.show();
        return;
    }

    final View.OnClickListener discardWorkoutButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);
            builder.setTitle("Delete workout?");
            builder.setMessage(getString(R.string.are_you_sure));
            builder.setPositiveButton(getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            File f = WorkoutSerializer.getFile(getApplicationContext(), advancedWorkoutSpinner.getValue().toString());
                            f.delete();
                            finish();
                            return;
                        }
                    });
            builder.setNegativeButton(getString(R.string.no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            return;
                        }
                    });
            builder.show();
            return;
        }
    };
}
