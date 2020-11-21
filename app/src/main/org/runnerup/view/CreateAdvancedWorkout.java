package org.runnerup.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TableRow;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

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


public class CreateAdvancedWorkout extends AppCompatActivity {

    private Workout advancedWorkout = null;
    private TitleSpinner advancedWorkoutSpinner = null;
    private final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();
    private boolean dontAskAgain = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        setContentView(R.layout.create_advanced_workout);

        Intent intent = getIntent();
        String advWorkoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME);
        boolean workoutExists = intent.getBooleanExtra(ManageWorkoutsActivity.WORKOUT_EXISTS, false);

        advancedWorkoutSpinner = findViewById(R.id.new_workout_spinner);
        advancedWorkoutSpinner.setValue(advWorkoutName);
        advancedWorkoutSpinner.setEnabled(false);

        dontAskAgain = false;

        ListView advancedStepList = findViewById(R.id.new_advnced_workout_steps);
        advancedStepList.setDividerHeight(0);
        advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

        Button addStepButton = findViewById(R.id.add_step_button);
        addStepButton.setOnClickListener(addStepButtonClick);

        Button addRepeatButton = findViewById(R.id.add_repeat_button);
        addRepeatButton.setOnClickListener(addRepeatStepButtonClick);

        Button saveWorkoutButton = findViewById(R.id.workout_save_button);
        saveWorkoutButton.setOnClickListener(saveWorkoutButtonClick);

        Button discardWorkoutButton = findViewById(R.id.workout_discard_button);
        discardWorkoutButton.setOnClickListener(discardWorkoutButtonClick);

        if (workoutExists) {
            // Avoid users inadvertently deleting existing workouts while editing:
            // (discard button should only be available when creating a workout)
            discardWorkoutButton.setVisibility(View.GONE);
        }

        try {
            createAdvancedWorkout(advWorkoutName, workoutExists);
        } catch (Exception e) {
            handleWorkoutFileException(e);
        }
    }

    private void createAdvancedWorkout(String name, boolean workoutExists) throws JSONException, IOException {
        if (workoutExists) {
            advancedWorkout = WorkoutSerializer.readFile(getApplicationContext(), name);
        } else {
            advancedWorkout = new Workout();
            WorkoutSerializer.writeFile(getApplicationContext(), name, advancedWorkout);
        }
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

                viewHolder.button = view.findViewById(R.id.workout_step_button);
                viewHolder.button.setOnChangedListener(onWorkoutChanged);

                viewHolder.add = view.findViewById(R.id.add_button);
                viewHolder.add.setOnClickListener(onAddButtonClick);

                viewHolder.del = view.findViewById(R.id.del_button);
                viewHolder.del.setOnClickListener(onDeleteButtonClick);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolderWorkoutStepsAdapter) view.getTag();
            }

            Workout.StepListEntry entry = steps.get(position);
            viewHolder.button.setStep(entry.step);
            float pxToDp = getResources().getDisplayMetrics().density;
            viewHolder.button.setPadding((int)(entry.level * 8 * pxToDp + 0.5f), 0, 0, 0);

            return view;
        }
    }


    private final View.OnClickListener onAddButtonClick = view -> {

        TableRow row = (TableRow) view.getParent();
        final StepButton stepButton = row.findViewById(R.id.workout_step_button);

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
    };


    private final View.OnClickListener onDeleteButtonClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            TableRow row = (TableRow) view.getParent();
            final StepButton stepButton = row.findViewById(R.id.workout_step_button);

            if(!dontAskAgain) {
                new AlertDialog.Builder(CreateAdvancedWorkout.this)
                        .setMultiChoiceItems(new String[]{"Don't ask again"}, new boolean[]{dontAskAgain},
                                (dialog, indexSelected, isChecked) -> dontAskAgain = isChecked)

                        .setTitle(R.string.Are_you_sure)
                        .setPositiveButton(R.string.Yes,
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    deleteStep(stepButton);
                                })
                        .setNegativeButton(R.string.No,
                                (dialog, which) -> dialog.dismiss())
                        .show();
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

    private final Runnable onWorkoutChanged = () -> {
        String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
        if (advancedWorkout != null) {
            Context ctx = getApplicationContext();
            try {
                WorkoutSerializer.writeFile(ctx, advWorkoutName, advancedWorkout);
            } catch (Exception ex) {
                new AlertDialog.Builder(CreateAdvancedWorkout.this)
                        .setTitle(R.string.Failed_to_load_workout)
                        .setMessage("" + ex.toString())
                        .setPositiveButton(R.string.OK,
                                (dialog, which) -> dialog.dismiss())
                        .show();
            }
        }
    };

    private final View.OnClickListener addStepButtonClick = v -> {
        advancedWorkout.addStep(new Step());
        advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
        advancedWorkoutStepsAdapter.notifyDataSetChanged();
    };

    private final View.OnClickListener addRepeatStepButtonClick = view -> {
        advancedWorkout.addStep(new RepeatStep());
        advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
        advancedWorkoutStepsAdapter.notifyDataSetChanged();
    };

    private final View.OnClickListener saveWorkoutButtonClick = v -> {
        try {
            String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
            WorkoutSerializer.writeFile(getApplicationContext(), advWorkoutName, advancedWorkout);
            finish();
        } catch (Exception e) {
            handleWorkoutFileException(e);
        }
    };

    private void handleWorkoutFileException(Exception e) {
        new AlertDialog.Builder(CreateAdvancedWorkout.this)
        .setTitle(getString(R.string.Failed_to_create_workout))
                .setMessage("" + e.toString())
                .setPositiveButton(R.string.OK,
                        (dialog, which) -> dialog.dismiss())
                .show();
    }

    private final View.OnClickListener discardWorkoutButtonClick = view -> {
        new AlertDialog.Builder(CreateAdvancedWorkout.this)
                .setTitle(R.string.Delete_workout)
                .setMessage(R.string.Are_you_sure)
                .setPositiveButton(R.string.Yes,
                        (dialog, which) -> {
                            dialog.dismiss();
                            String name = advancedWorkoutSpinner.getValue().toString();
                            File f = WorkoutSerializer.getFile(getApplicationContext(), name);
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                            finish();
                        })
                .setNegativeButton(R.string.No,
                        (dialog, which) -> dialog.dismiss())
                .show();
    };
}
