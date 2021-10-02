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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.content.WorkoutFileProvider;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.SyncManager.Callback;
import org.runnerup.export.SyncManager.WorkoutRef;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Status;
import org.runnerup.workout.Workout;
import org.runnerup.workout.WorkoutSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public class ManageWorkoutsActivity extends AppCompatActivity implements Constants {

    private SQLiteDatabase mDB = null;

    private String PHONE_STRING = "My phone";
    public final static String WORKOUT_NAME = "";
    public final static String WORKOUT_EXISTS = "workout_exists";

    private final HashSet<SyncManager.WorkoutRef> pendingWorkouts = new HashSet<>();
    private final ArrayList<ContentValues> providers = new ArrayList<>();
    private final HashMap<String, ArrayList<SyncManager.WorkoutRef>> workouts = new HashMap<>();
    private WorkoutAccountListAdapter adapter = null;

    private final HashSet<String> loadedProviders = new HashSet<>();

    private boolean uploading = false;
    private CompoundButton currentlySelectedWorkout = null;
    private Button deleteButton = null;
    private Button shareButton = null;
    private Button editButton = null;
    private Button createButton = null;

    private SyncManager syncManager = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_workouts);

        PHONE_STRING = getResources().getString(R.string.my_phone);

        mDB = DBHelper.getReadableDatabase(this);
        syncManager = new SyncManager(this);
        adapter = new WorkoutAccountListAdapter(this);
        ExpandableListView list = findViewById(R.id.expandable_list_view);
        list.setAdapter(adapter);

        deleteButton = findViewById(R.id.delete_workout_button);
        deleteButton.setOnClickListener(deleteButtonClick);
        createButton = findViewById(R.id.create_workout_button);
        createButton.setOnClickListener(createButtonClick);

        shareButton = findViewById(R.id.share_workout_button);
        shareButton.setOnClickListener(shareButtonClick);

        editButton = findViewById(R.id.edit_workout_button);
        editButton.setOnClickListener(editButtonClick);

        handleButtons();

        requery();
        listLocal();
        list.expandGroup(0);

        Uri data = getIntent().getData();
        if (data != null) {
            getIntent().setData(null);
            String fileName = getFilename(data);
            if (fileName == null)
                fileName = "noname";

            try {
                importData(fileName, data);
            } catch (Exception e) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.Error)
                        .setMessage(getString(R.string.Failed_to_import) + ": " + fileName)
                        .setPositiveButton(R.string.OK,
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    ManageWorkoutsActivity.this.finish();
                                })
                        .show();
            }
        }
        // launch home Activity (with FLAG_ACTIVITY_CLEAR_TOP)
    }

    private String getFilename(Uri data) {
        Log.i(getClass().getName(), "scheme: " + data.toString());
        String name = null;
        if (ContentResolver.SCHEME_FILE.contentEquals(data.getScheme())) {
            name = data.getLastPathSegment();
        } else if (ContentResolver.SCHEME_CONTENT.contentEquals(data.getScheme())) {
            String[] projection = {
                    MediaStore.MediaColumns.DISPLAY_NAME
            };
            Cursor c = getContentResolver().query(data, projection, null, null, null);
            if (c != null) {
                c.moveToFirst();
                final int fileNameColumnId = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (fileNameColumnId >= 0)
                    name = c.getString(fileNameColumnId);
                c.close();
            }
        }
        return name;
    }

    private void importData(String fileName, final Uri data) throws Exception {
        final ContentResolver cr = getContentResolver();
        InputStream is = cr.openInputStream(data);
        if (is == null) {
            throw new Exception("Failed to get input stream");
        }
        Workout w = WorkoutSerializer.readJSON(new BufferedReader(new InputStreamReader(is)));
        is.close();
        if (w == null)
            throw new Exception("Failed to parse content");

        if(fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - ".json".length());
        }

        final String prefix = getString(R.string.RunnerUp_workout) + ": ";
        if (fileName.startsWith(prefix) && fileName.length() > prefix.length()) {
            fileName = fileName.substring(prefix.length());
        }

        final boolean exists = WorkoutSerializer.getFile(this, fileName).exists();
        final boolean[] selected = {
                false
        };

        final String workoutName = fileName;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Import_workout) + ": " + workoutName)
                .setPositiveButton(R.string.Yes,
                        (dialog, which) -> {
                            dialog.dismiss();
                            String saveName = workoutName;
                            try {
                                if (exists && !selected[0]) {
                                    for (int i = 1; i < 25; i++) {
                                        saveName = workoutName + "-" + i;
                                        if (!WorkoutSerializer.getFile(ManageWorkoutsActivity.this,
                                                saveName).exists())
                                            break;
                                    }
                                    Toast.makeText(ManageWorkoutsActivity.this,
                                            getString(R.string.Saving_as) + " " + saveName, Toast.LENGTH_SHORT).show();
                                }
                                saveImport(saveName, cr.openInputStream(data));
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            launchMain(saveName);
                        })
                .setNegativeButton(R.string.No,
                        (dialog, which) -> {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                            finish();
                        });

        if (exists) {
            String[] items = {
                    getString(R.string.Overwrite_existing)
            };
            builder.setMultiChoiceItems(items, selected,
                    (arg0, arg1, arg2) -> selected[arg1] = arg2);
        }

        builder.show();
    }

    private void saveImport(String file, InputStream is) throws IOException {
        File f = WorkoutSerializer.getFile(this, file);
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
        BufferedInputStream in = new BufferedInputStream(is);
        byte[] buf = new byte[1024];
        while (in.read(buf) > 0) {
            out.write(buf);
        }
        in.close();
        out.close();
    }

    private void launchMain(String fileName) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.edit().putString(getResources().getString(R.string.pref_advanced_workout), fileName).apply();

        Intent intent = new Intent(this, MainLayout.class)
                .putExtra("mode", StartActivity.TAB_ADVANCED)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        listLocal();
    }


    private void handleButtons() {
        if (currentlySelectedWorkout == null) {
            deleteButton.setEnabled(false);
            shareButton.setEnabled(false);
            editButton.setEnabled(false);
            createButton.setEnabled(true);
            return;
        }

        WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
        if (PHONE_STRING.contentEquals(selected.synchronizer)) {
            deleteButton.setEnabled(true);
            shareButton.setEnabled(true);
            editButton.setEnabled(true);
        } else {
            deleteButton.setEnabled(false);
            shareButton.setEnabled(false);
            editButton.setEnabled(false);
        }
    }

    private void listLocal() {
        ArrayList<SyncManager.WorkoutRef> newlist = new ArrayList<>();
        String[] list = org.runnerup.view.WorkoutListAdapter.load(this);
        if (list != null) {
            for (String s : list) {
                newlist.add(new SyncManager.WorkoutRef(PHONE_STRING, null,
                        s.substring(0, s.lastIndexOf('.')))
                );
            }
        }

        workouts.remove(PHONE_STRING);
        workouts.put(PHONE_STRING, newlist);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DBHelper.closeDB(mDB);
        syncManager.close();
    }

    private void requery() {
        ContentValues[] allSynchronizers;
        try {
            /*
             * Accounts/reports
             */
            String sql = "SELECT DISTINCT "
                    + "  acc._id, " // 0
                    + ("  acc." + DB.ACCOUNT.NAME + ", ")
                    + ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
                    + ("  acc." + DB.ACCOUNT.FLAGS + ", ")
                    + ("  acc." + DB.ACCOUNT.ENABLED + " ")
                    + (" FROM " + DB.ACCOUNT.TABLE + " acc ");

            Cursor c = mDB.rawQuery(sql, null);
            allSynchronizers = DBHelper.toArray(c);
            c.close();
        } catch (IllegalStateException e) {
            Log.e(getClass().getName(), "requery: " + e.getMessage());
            return;
        }

        providers.clear();

        ContentValues phone = new ContentValues();
        phone.put(DB.ACCOUNT.NAME, PHONE_STRING);
        providers.add(phone);

        // Only local phone provider is currently supported (Garmin was a remote provider a long time ago)
        // Comment out the handling to avoid accidental activations
//        for (ContentValues tmp : allSynchronizers) {
//            Synchronizer synchronizer = syncManager.add(tmp);
//            //There is no option to show disabled providers, so check for enable or configured
//            if (synchronizer != null && synchronizer.checkSupport(Synchronizer.Feature.WORKOUT_LIST) &&
//                    (synchronizer.isConfigured() || tmp.getAsInteger(DB.ACCOUNT.ENABLED) == 1)) {
//                providers.add(tmp);
//
//                workouts.remove(synchronizer.getName());
//                workouts.put(synchronizer.getName(), new ArrayList<>());
//            }
//        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (uploading) {
            /*
             * Ignore while uploading
             */
            return;
        }
        super.onBackPressed();
    }

    interface Filter<T> {
        boolean match(T t);
    }

    ArrayList<SyncManager.WorkoutRef> filter(List<SyncManager.WorkoutRef> list,
                                             Filter<SyncManager.WorkoutRef> f) {
        ArrayList<SyncManager.WorkoutRef> newlist = new ArrayList<>();
        return filter(list, newlist, f);
    }

    private ArrayList<SyncManager.WorkoutRef> filter(List<SyncManager.WorkoutRef> list,
                                                     ArrayList<WorkoutRef> newlist, Filter<SyncManager.WorkoutRef> f) {
        for (SyncManager.WorkoutRef w : list) {
            if (f.match(w))
                newlist.add(w);
        }
        return newlist;
    }


    private final OnClickListener createButtonClick = view -> {
        final Intent intent = new Intent(ManageWorkoutsActivity.this, CreateAdvancedWorkout.class);
        // Set an EditText view to get user input
        final EditText input = new EditText(ManageWorkoutsActivity.this);

        new AlertDialog.Builder(ManageWorkoutsActivity.this)
                .setTitle(R.string.Create_new_workout)
                .setMessage(R.string.Set_workout_name)
                .setView(input)
                .setPositiveButton(R.string.OK, (dialog, whichButton) -> {
                    String value = input.getText().toString();
                    intent.putExtra(WORKOUT_NAME, value);
                    intent.putExtra(WORKOUT_EXISTS, false);
                    startActivity(intent);

                })
                .setNegativeButton(R.string.Cancel, (dialog, whichButton) -> dialog.dismiss())
                .show();
    };

    private final OnClickListener deleteButtonClick = v -> {
        if (currentlySelectedWorkout == null)
            return;

        final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
        new AlertDialog.Builder(ManageWorkoutsActivity.this)
                .setTitle(getString(R.string.Delete_workout) + " " + selected.workoutName)
                .setMessage(R.string.Are_you_sure)
                .setPositiveButton(R.string.Yes,
                        (dialog, which) -> {
                            dialog.dismiss();
                            deleteWorkout(selected);
                        })
                .setNegativeButton(R.string.No,
                        // Do nothing but close the dialog
                        (dialog, which) -> dialog.dismiss())
                .show();
    };

    private void deleteWorkout(WorkoutRef selected) {
        File f = WorkoutSerializer.getFile(this, selected.workoutName);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (selected.workoutName.contentEquals(pref.getString(getResources().getString(R.string.pref_advanced_workout), ""))) {
            pref.edit().putString(getResources().getString(R.string.pref_advanced_workout), "").apply();
        }
        currentlySelectedWorkout = null;
        listLocal();
    }

    private final OnCheckedChangeListener onWorkoutChecked = (arg0, isChecked) -> {
        if (currentlySelectedWorkout != null) {
            currentlySelectedWorkout.setChecked(false);
        }
        if (isChecked) {
            currentlySelectedWorkout = arg0;
        } else {
            currentlySelectedWorkout = null;
        }
        handleButtons();
    };

    OnClickListener loadWorkoutButtonClick = v -> {
        uploading = true;
        syncManager.loadWorkouts(pendingWorkouts, (synchronizerName, status) -> {
            uploading = false;
            listLocal();
        });
    };

    private final OnClickListener shareButtonClick = v -> {
        if (currentlySelectedWorkout == null)
            return;

        final AppCompatActivity context = ManageWorkoutsActivity.this;
        final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
        final String name = selected.workoutName;
        final Intent intent = new Intent(Intent.ACTION_SEND);

        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.RunnerUp_workout) + ": " + name);
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.HinHere_is_a_workout_I_think_you_might_like));

        intent.setType(WorkoutFileProvider.MIME);
        Uri uri = Uri.parse("content://" + WorkoutFileProvider.AUTHORITY + "/" + name + ".json");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        context.startActivity(Intent.createChooser(intent, getString(R.string.Share_workout)));
    };

    private final OnClickListener editButtonClick = v -> {
        if (currentlySelectedWorkout == null)
            return;

        final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
        final Intent intent = new Intent(ManageWorkoutsActivity.this, CreateAdvancedWorkout.class);

        intent.putExtra(WORKOUT_NAME, selected.workoutName);
        intent.putExtra(WORKOUT_EXISTS, true);
        startActivity(intent);
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SyncManager.CONFIGURE_REQUEST) {
            syncManager.onActivityResult(requestCode, resultCode, data);
        }
        requery();
    }

    class WorkoutAccountListAdapter extends BaseExpandableListAdapter {

        final Context context;

        WorkoutAccountListAdapter(Context ctx) {
            context = ctx;
        }

        String getProvider(int index) {
            return providers.get(index).getAsString(DB.ACCOUNT.NAME);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return workouts.get(getProvider(groupPosition)).get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition,
                                 boolean isLastChild, View view, ViewGroup parent) {

            if (!(view instanceof LinearLayout)) {
                LayoutInflater infalInflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = infalInflater.inflate(R.layout.manage_workouts_list_row, parent, false);
            }

            WorkoutRef workout = workouts.get(getProvider(groupPosition)).get(childPosition);
            RadioButton cb = view.findViewById(R.id.download_workout_checkbox);

            cb.setTag(workout);
            cb.setChecked(currentlySelectedWorkout != null
                    && currentlySelectedWorkout.getTag() == workout);
            cb.setOnCheckedChangeListener(onWorkoutChecked);
            cb.setText(workout.workoutName);
            return view;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return workouts.get(getProvider(groupPosition)).size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return providers.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            return providers.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return 0;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.manage_workouts_list_category, parent, false);
            }

            TextView categoryText = convertView.findViewById(R.id.category_text);
            categoryText.setText(getProvider(groupPosition));

            if (isExpanded)
                categoryText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_expand_up_white_24dp, 0);
            else
                categoryText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_expand_down_white_24dp, 0);

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }

        int saveGroupPosition;

        @Override
        public void onGroupExpanded(int groupPosition) {
            String provider = getProvider(groupPosition);
            if (PHONE_STRING.contentEquals(provider)) {
                super.onGroupExpanded(groupPosition);
                return;
            }

            if (loadedProviders.contains(provider)) {
                super.onGroupExpanded(groupPosition);
                return;
            }

            uploading = true;
            saveGroupPosition = groupPosition;

            if (!syncManager.isConfigured(provider)) {
                syncManager.connect(onSynchronizerConfiguredCallback, provider);
            } else {
                onSynchronizerConfiguredCallback.run(provider, Synchronizer.Status.OK);
            }
        }

        final Callback onSynchronizerConfiguredCallback = new Callback() {
            @Override
            public void run(String synchronizerName, Status status) {
                Log.i(getClass().getName(), "status: " + status);
                if (status != Synchronizer.Status.OK) {
                    uploading = false;
                    return;
                }

                ArrayList<WorkoutRef> list = workouts.get(synchronizerName);
                list.clear();

                HashSet<String> tmp = new HashSet<>();
                tmp.add(synchronizerName);

                syncManager.loadWorkoutList(list, onLoadWorkoutListCallback, tmp);
            }
        };

        private void onGroupExpandedImpl() {
            super.onGroupExpanded(saveGroupPosition);
        }

        private final Callback onLoadWorkoutListCallback = new Callback() {

            @Override
            public void run(String synchronizerName, Status status) {
                uploading = false;
                if (status == Status.OK) {
                    loadedProviders.add(getProvider(saveGroupPosition));
                    adapter.notifyDataSetChanged();
                    onGroupExpandedImpl();
                }
            }
        };

        @Override
        public void onGroupCollapsed(int groupPosition) {
            super.onGroupCollapsed(groupPosition);
            String provider = getProvider(groupPosition);
            if (currentlySelectedWorkout != null) {
                WorkoutRef ref = (WorkoutRef) currentlySelectedWorkout.getTag();
                if (ref.synchronizer.contentEquals(provider)) {
                    currentlySelectedWorkout.setChecked(false);
                    currentlySelectedWorkout = null;
                }
            }
        }
    }
}
