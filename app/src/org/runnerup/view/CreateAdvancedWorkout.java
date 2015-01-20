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
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CreateAdvancedWorkout extends Activity {

    Workout advancedWorkout = null;
    TitleSpinner advancedWorkoutSpinner = null;
    Button addStepButton = null;
    Button saveWorkoutButton = null;
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

        saveWorkoutButton = (Button) findViewById(R.id.workout_save_button);
        saveWorkoutButton.setOnClickListener(saveWorkoutButtonClick);




        try {
            createAdvancedWorkout(advWorkoutName);
        } catch (IOException e) {
            System.out.println("DUPA");
        } catch (JSONException e) {
            System.out.println("DUPA");
        }
    }

    private void createAdvancedWorkout(String name) throws JSONException, IOException {
        advancedWorkout = new Workout();

        WorkoutSerializer.writeFile(getApplicationContext(), name + ".json", advancedWorkout);

        advancedWorkoutStepsAdapter.steps = advancedWorkout.getSteps();
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
            button.setSelected(true);
            return button;
        }
    }

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

    final View.OnClickListener saveWorkoutButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
                WorkoutSerializer.writeFile(getApplicationContext(), advWorkoutName, advancedWorkout);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    final View.OnClickListener addStepButtonClick = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            advancedWorkout.addStep(new Step());
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getSteps();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
        }
    };


}
