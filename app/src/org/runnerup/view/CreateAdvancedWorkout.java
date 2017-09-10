package org.runnerup.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TableRow;

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

    private Workout advancedWorkout = null;
    private TitleSpinner advancedWorkoutSpinner = null;
    private final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();
    private boolean dontAskAgain = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.create_advanced_workout);

        Intent intent = getIntent();
        String advWorkoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME);

        advancedWorkoutSpinner = (TitleSpinner) findViewById(R.id.new_workout_spinner);
        advancedWorkoutSpinner.setValue(advWorkoutName);
        advancedWorkoutSpinner.setEnabled(false);

        dontAskAgain = false;

        ListView advancedStepList = (ListView) findViewById(R.id.new_advnced_workout_steps);
        advancedStepList.setDividerHeight(0);
        advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

        Button addStepButton = (Button) findViewById(R.id.add_step_button);
        addStepButton.setOnClickListener(addStepButtonClick);

        Button addRepeatButton = (Button) findViewById(R.id.add_repeat_button);
        addRepeatButton.setOnClickListener(addRepeatStepButtonClick);

        Button saveWorkoutButton = (Button) findViewById(R.id.workout_save_button);
        saveWorkoutButton.setOnClickListener(saveWorkoutButtonClick);

        Button discardWorkoutButton = (Button) findViewById(R.id.workout_discard_button);
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

        List<Workout.StepListEntry> steps = new ArrayList<>();

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

        private class ViewHolderWorkoutStepsAdapter {
            private StepButton button;
            private Button add;
            private Button del;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            ViewHolderWorkoutStepsAdapter viewHolder;

            if (view == null) {
                viewHolder = new ViewHolderWorkoutStepsAdapter();

                LayoutInflater inflater = getLayoutInflater();
                view = inflater.inflate(R.layout.advanced_workout_row, parent, false);

                viewHolder.button = (StepButton) view.findViewById(R.id.workout_step_button);
                viewHolder.button.setOnChangedListener(onWorkoutChanged);

                viewHolder.add = (Button) view.findViewById(R.id.add_button);
                viewHolder.add.setOnClickListener(onAddButtonClick);

                viewHolder.del = (Button) view.findViewById(R.id.del_button);
                viewHolder.del.setOnClickListener(onDeleteButtonClick);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolderWorkoutStepsAdapter) view.getTag();
            }

            Workout.StepListEntry entry = steps.get(position);
            viewHolder.button.setStep(entry.step);
            viewHolder.button.setPadding(entry.level * 12, 0, 0, 0);

            return view;
        }
    }


    private final View.OnClickListener onAddButtonClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {

            TableRow row = (TableRow) view.getParent();
            final StepButton stepButton = (StepButton) row.findViewById(R.id.workout_step_button);

            Step currentStep = stepButton.getStep();
            if (currentStep instanceof RepeatStep) {
                RepeatStep rs = (RepeatStep) currentStep;
                rs.getSteps().add(new Step());
            } else {

                int index = advancedWorkout.getSteps().indexOf(currentStep);
                if (index < 0) {
                    for (Step se : advancedWorkout.getSteps()) {
                        if (se instanceof RepeatStep) {
                            index = ((RepeatStep) se).getSteps().indexOf(currentStep);
                            ((RepeatStep) se).getSteps().add(index + 1, new Step());
                        }
                    }
                } else {
                    advancedWorkout.getSteps().add(index + 1, new Step());
                }
            }
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }
    };


    private final View.OnClickListener onDeleteButtonClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            TableRow row = (TableRow) view.getParent();
            final StepButton stepButton = (StepButton) row.findViewById(R.id.workout_step_button);

            if(!dontAskAgain) {

                AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);

                builder.setMultiChoiceItems(new String[] {"Don't ask again"}, new boolean[] {dontAskAgain},
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                                dontAskAgain = isChecked;
                            }
                        });

                builder.setTitle(getString(R.string.Are_you_sure));
                builder.setPositiveButton(getString(R.string.Yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                deleteStep(stepButton);
                            }
                        });
                builder.setNegativeButton(getString(R.string.No),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                builder.show();
            } else {
                deleteStep(stepButton);
            }
        }

        private void deleteStep(StepButton button) {
            Step s = button.getStep();
            for (Step se : advancedWorkout.getSteps()) {
                if (se instanceof RepeatStep) {
                    for (Step subStep : ((RepeatStep) se).getSteps()) {
                        if (subStep.equals(s)) {
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
    };

    private final Runnable onWorkoutChanged = new Runnable() {
        @Override
        public void run() {
            String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
            if (advancedWorkout != null) {
                Context ctx = getApplicationContext();
                try {
                    WorkoutSerializer.writeFile(ctx, advWorkoutName + ".json", advancedWorkout);
                } catch (Exception ex) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);
                    builder.setTitle(getString(R.string.Failed_to_load_workout));
                    builder.setMessage("" + ex.toString());
                    builder.setPositiveButton(getString(R.string.OK),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    builder.show();
                }
            }
        }
    };

    private final View.OnClickListener addStepButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            advancedWorkout.addStep(new Step());
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }
    };

    private final View.OnClickListener addRepeatStepButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            advancedWorkout.addStep(new RepeatStep());
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }
    };

    private final View.OnClickListener saveWorkoutButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
                WorkoutSerializer.writeFile(getApplicationContext(), advWorkoutName + ".json", advancedWorkout);
                finish();
            } catch (Exception e) {
                handleWorkoutFileException(e);
            }
        }
    };

    private void handleWorkoutFileException(Exception e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);
        builder.setTitle(getString(R.string.Failed_to_create_workout));
        builder.setMessage("" + e.toString());
        builder.setPositiveButton(getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.show();
    }

    private final View.OnClickListener discardWorkoutButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(CreateAdvancedWorkout.this);
            builder.setTitle("Delete workout?");
            builder.setMessage(getString(R.string.Are_you_sure));
            builder.setPositiveButton(getString(R.string.Yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            String name = advancedWorkoutSpinner.getValue().toString() + ".json";
                            File f = WorkoutSerializer.getFile(getApplicationContext(), name);
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                            finish();
                        }
                    });
            builder.setNegativeButton(getString(R.string.No),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.show();
        }
    };
}
