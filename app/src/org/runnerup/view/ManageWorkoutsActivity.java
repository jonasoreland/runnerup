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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
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

@TargetApi(Build.VERSION_CODES.FROYO)
public class ManageWorkoutsActivity extends Activity implements Constants {

    DBHelper mDBHelper = null;
    SQLiteDatabase mDB = null;

    private String PHONE_STRING = "my phone";
    public final static String WORKOUT_NAME = "";

    final HashSet<SyncManager.WorkoutRef> pendingWorkouts = new HashSet<SyncManager.WorkoutRef>();
    final ArrayList<ContentValues> providers = new ArrayList<ContentValues>();
    final HashMap<String, ArrayList<SyncManager.WorkoutRef>> workouts = new HashMap<String, ArrayList<SyncManager.WorkoutRef>>();
    WorkoutAccountListAdapter adapter = null;

    final HashSet<String> loadedProviders = new HashSet<String>();

    boolean uploading = false;
    ExpandableListView list = null;
    CompoundButton currentlySelectedWorkout = null;
    Button downloadButton = null;
    Button deleteButton = null;
    Button shareButton = null;
    Button createButton = null;

    SyncManager syncManager = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_workouts);

        PHONE_STRING = getResources().getString(R.string.my_phone);

        mDBHelper = new DBHelper(this);
        mDB = mDBHelper.getReadableDatabase();
        syncManager = new SyncManager(this);
        adapter = new WorkoutAccountListAdapter(this);
        list = (ExpandableListView) findViewById(R.id.expandable_list_view);
        list.setAdapter(adapter);
        downloadButton = (Button) findViewById(R.id.download_workout_button);
        downloadButton.setOnClickListener(downloadButtonClick);
        deleteButton = (Button) findViewById(R.id.delete_workout_button);
        deleteButton.setOnClickListener(deleteButtonClick);
        createButton = (Button) findViewById(R.id.create_workout_button);
        createButton.setOnClickListener(createButtonClick);

        shareButton = (Button) findViewById(R.id.share_workout_button);
        shareButton.setOnClickListener(shareButtonClick);

        handleButtons();

        requery();
        listLocal();

        Uri data = getIntent().getData();
        if (data != null) {
            getIntent().setData(null);
            String fileName = getFilename(data);
            if (fileName == null)
                fileName = "noname";

            try {
                importData(fileName, data);
            } catch (Exception e) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.Problem));
                builder.setMessage(getString(R.string.Failed_to_import) + fileName);
                builder.setPositiveButton(getString(R.string.OK_darn),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                ManageWorkoutsActivity.this.finish();
                                return;
                            }
                        });
                builder.show();
                return;
            }
            return;
        }
        // launch home Activity (with FLAG_ACTIVITY_CLEAR_TOP)
    }

    private String getFilename(Uri data) {
        Log.i(getClass().getName(), "scheme: " + data.toString());
        String name = null;
        if (ContentResolver.SCHEME_FILE.contentEquals(data.getScheme())) {
            name = data.getLastPathSegment();
        } else if (ContentResolver.SCHEME_CONTENT.contentEquals(data.getScheme())) {
            String projection[] = {
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

    private void importData(final String fileName, final Uri data) throws Exception {
        final ContentResolver cr = getContentResolver();
        InputStream is = cr.openInputStream(data);
        if (is == null) {
            throw new Exception("Failed to get input stream");
        }
        boolean convertRestToRecovery = true; // we just test to import, value of this doesnt matter
        Workout w = WorkoutSerializer.readJSON(new BufferedReader(new InputStreamReader(is)),
                convertRestToRecovery);
        is.close();
        if (w == null)
            throw new Exception("Failed to parse content");

        final boolean exists = WorkoutSerializer.getFile(this, fileName).exists();
        final boolean selected[] = {
            false
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Import_workout) + fileName);
        builder.setPositiveButton(getString(R.string.Yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        String saveName = fileName;
                        try {
                            if (exists && selected[0] == false) {
                                String name = "";
                                String tmp[] = fileName.split("\\.");
                                if (tmp.length > 0) {
                                    for (int i = 0; i < tmp.length - 1; i++)
                                        name = name.concat(tmp[i]);
                                } else {
                                    name = fileName;
                                }
                                String ending = tmp.length > 0 ? ("." + tmp[tmp.length - 1]) : "";
                                String newName = fileName;
                                for (int i = 1; i < 25; i++) {
                                    newName = name + "-" + i + ending;
                                    if (!WorkoutSerializer.getFile(ManageWorkoutsActivity.this,
                                            newName).exists())
                                        break;
                                }
                                saveName = newName;
                                Toast.makeText(ManageWorkoutsActivity.this,
                                        getString(R.string.Saving_as) + saveName, Toast.LENGTH_SHORT).show();
                            }
                            saveImport(saveName, cr.openInputStream(data));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        launchMain(saveName);
                        return;
                    }
                });
        builder.setNegativeButton(getString(R.string.No_way),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing but close the dialog
                        dialog.dismiss();
                        finish();
                        return;
                    }
                });

        if (exists) {
            String items[] = {
                getString(R.string.Overwrite_existing)
            };
            builder.setMultiChoiceItems(items, selected,
                    new OnMultiChoiceClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int arg1,
                                boolean arg2) {
                            selected[arg1] = arg2;
                        }
                    });
        }

        builder.show();
        return;
    }

    private void saveImport(String file, InputStream is) throws IOException {
        File f = WorkoutSerializer.getFile(this, file);
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
        BufferedInputStream in = new BufferedInputStream(is);
        byte buf[] = new byte[1024];
        while (in.read(buf) > 0) {
            out.write(buf);
        }
        in.close();
        out.close();
    }

    protected void launchMain(String fileName) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.edit().putString(getResources().getString(R.string.pref_advanced_workout), fileName).commit();

        Intent intent = new Intent(this, MainLayout.class);
        intent.putExtra("mode", StartFragment.TAB_ADVANCED);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
        return;
    }

    @Override
    protected void onResume() {
        super.onResume();

        listLocal();
    }


    private void handleButtons() {
        if (currentlySelectedWorkout == null) {
            downloadButton.setEnabled(false);
            deleteButton.setEnabled(false);
            shareButton.setEnabled(false);
            createButton.setEnabled(true);
            return;
        }

        WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
        if (PHONE_STRING.contentEquals(selected.synchronizer)) {
            downloadButton.setEnabled(false);
            deleteButton.setEnabled(true);
            shareButton.setEnabled(true);
        } else {
            downloadButton.setEnabled(true);
            deleteButton.setEnabled(false);
            shareButton.setEnabled(false);
        }
    }

    void listLocal() {
        ArrayList<SyncManager.WorkoutRef> newlist = new ArrayList<SyncManager.WorkoutRef>();
        String[] list = org.runnerup.view.WorkoutListAdapter.load(this);
        if (list != null) {
            for (String s : list) {
                newlist.add(new SyncManager.WorkoutRef(PHONE_STRING, null, s));
            }
        }

        workouts.remove(PHONE_STRING);
        workouts.put(PHONE_STRING, newlist);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDB.close();
        mDBHelper.close();
        syncManager.close();
    }

    void requery() {
        ContentValues allSynchronizers[] = null;
        {
            /**
             * Accounts/reports
             */
            String sql = new String(
                    "SELECT DISTINCT "
                            + "  acc._id, " // 0
                            + ("  acc." + DB.ACCOUNT.NAME + ", ")
                            + ("  acc." + DB.ACCOUNT.DESCRIPTION + ", ")
                            + ("  acc." + DB.ACCOUNT.AUTH_METHOD + ", ")
                            + ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
                            + ("  acc." + DB.ACCOUNT.ENABLED + ", ")
                            + ("  acc." + DB.ACCOUNT.FLAGS + " ")
                            + (" FROM " + DB.ACCOUNT.TABLE + " acc "));

            Cursor c = mDB.rawQuery(sql, null);
            allSynchronizers = DBHelper.toArray(c);
            c.close();
        }

        providers.clear();

        ContentValues phone = new ContentValues();
        phone.put(DB.ACCOUNT.NAME, PHONE_STRING);
        providers.add(phone);

        for (ContentValues tmp : allSynchronizers) {
            Synchronizer synchronizer = syncManager.add(tmp);
            if (synchronizer != null && synchronizer.checkSupport(Synchronizer.Feature.WORKOUT_LIST)) {
                providers.add(tmp);

                workouts.remove(synchronizer.getName());
                workouts.put(synchronizer.getName(), new ArrayList<WorkoutRef>());
            }
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (uploading == true) {
            /**
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
        ArrayList<SyncManager.WorkoutRef> newlist = new ArrayList<SyncManager.WorkoutRef>();
        return filter(list, newlist, f);
    }

    ArrayList<SyncManager.WorkoutRef> filter(List<SyncManager.WorkoutRef> list,
            ArrayList<WorkoutRef> newlist, Filter<SyncManager.WorkoutRef> f) {
        for (SyncManager.WorkoutRef w : list) {
            if (f.match(w))
                newlist.add(w);
        }
        return newlist;
    }


    final OnClickListener createButtonClick = new OnClickListener() {
        @Override
        public void onClick(View view) {
            final Intent intent = new Intent(ManageWorkoutsActivity.this, CreateAdvancedWorkout.class);

            AlertDialog.Builder builder = new AlertDialog.Builder(ManageWorkoutsActivity.this);

            builder.setTitle(getString(R.string.Create_new_workout));
            builder.setMessage(getString(R.string.Set_workout_name));

            // Set an EditText view to get user input
            final EditText input = new EditText(ManageWorkoutsActivity.this);
            builder.setView(input);

            builder.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String value = input.getText().toString();
                    intent.putExtra(WORKOUT_NAME, value);
                    startActivity(intent);

                }
            });

            builder.setNegativeButton(getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });

            builder.show();
        }
    };

    final OnClickListener downloadButtonClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (currentlySelectedWorkout == null)
                return;

            final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
            ArrayList<WorkoutRef> local = workouts.get(PHONE_STRING);
            if (contains(local, selected)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ManageWorkoutsActivity.this);
                builder.setTitle(getString(R.string.Downloading_1s_will_overwrite_2_workout_with_same_name, selected.workoutName, PHONE_STRING));
                builder.setMessage(getString(R.string.Are_you_sure));
                builder.setPositiveButton(getString(R.string.Yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                downloadWorkout(selected);
                                return;
                            }
                        });
                builder.setNegativeButton(getString(R.string.No),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing but close the dialog
                                dialog.dismiss();
                            }

                        });
                builder.show();
                return;
            }

            downloadWorkout(selected);
        }

        private void downloadWorkout(WorkoutRef selected) {
            uploading = true;
            HashSet<WorkoutRef> list = new HashSet<WorkoutRef>();
            list.add((WorkoutRef) currentlySelectedWorkout.getTag());
            syncManager.loadWorkouts(list, new SyncManager.Callback() {
                @Override
                public void run(String synchronizerName, Synchronizer.Status status) {
                    uploading = false;
                    currentlySelectedWorkout = null;
                    listLocal();
                    handleButtons();
                }
            });
        }

        private boolean contains(ArrayList<WorkoutRef> local,
                WorkoutRef selected) {
            for (WorkoutRef w : local) {
                if (selected.workoutName.contentEquals(w.workoutName)) {
                    return true;
                }
            }
            return false;
        }
    };

    private final OnClickListener deleteButtonClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (currentlySelectedWorkout == null)
                return;

            final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
            AlertDialog.Builder builder = new AlertDialog.Builder(ManageWorkoutsActivity.this);
            builder.setTitle(getString(R.string.Delete_workout) + selected.workoutName);
            builder.setMessage(getString(R.string.Are_you_sure));
            builder.setPositiveButton(getString(R.string.Yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            deleteWorkout(selected);
                            return;
                        }
                    });
            builder.setNegativeButton(getString(R.string.No),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
            return;
        }
    };

    private void deleteWorkout(WorkoutRef selected) {
        File f = WorkoutSerializer.getFile(this, selected.workoutName);
        f.delete();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (selected.workoutName.contentEquals(pref.getString(getResources().getString(R.string.pref_advanced_workout), ""))) {
            pref.edit().putString(getResources().getString(R.string.pref_advanced_workout), "").commit();
        }
        currentlySelectedWorkout = null;
        listLocal();
    }

    public final OnCheckedChangeListener onWorkoutChecked = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton arg0, boolean isChecked) {
            if (currentlySelectedWorkout != null) {
                currentlySelectedWorkout.setChecked(false);
            }
            if (isChecked) {
                currentlySelectedWorkout = arg0;
            } else {
                currentlySelectedWorkout = null;
            }
            handleButtons();
        }
    };

    OnClickListener loadWorkoutButtonClick = new OnClickListener() {
        public void onClick(View v) {
            uploading = true;
            syncManager.loadWorkouts(pendingWorkouts, new SyncManager.Callback() {
                @Override
                public void run(String synchronizerName, Status status) {
                    uploading = false;
                    listLocal();
                }
            });
        }
    };

    private final OnClickListener shareButtonClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (currentlySelectedWorkout == null)
                return;

            final Activity context = ManageWorkoutsActivity.this;
            final WorkoutRef selected = (WorkoutRef) currentlySelectedWorkout.getTag();
            final String name = selected.workoutName;
            final Intent intent = new Intent(Intent.ACTION_SEND);

            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.RunnerUp_workout) + name);
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.HinHere_is_a_workout_I_think_you_might_like));

            intent.setType(WorkoutFileProvider.MIME);
            Uri uri = Uri.parse("content://" + WorkoutFileProvider.AUTHORITY + "/" + name);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            context.startActivity(Intent.createChooser(intent, getString(R.string.Share_workout)));
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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

            if (view == null || !(view instanceof LinearLayout)) {
                LayoutInflater infalInflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = infalInflater.inflate(R.layout.manage_workouts_list_row, parent, false);
            }

            WorkoutRef workout = workouts.get(getProvider(groupPosition)).get(childPosition);
            RadioButton cb = (RadioButton) view.findViewById(R.id.download_workout_checkbox);
            TextView tv = (TextView) view.findViewById(R.id.download_workout_name);

            cb.setTag(workout);
            cb.setChecked(currentlySelectedWorkout != null
                    && currentlySelectedWorkout.getTag() == workout);
            cb.setOnCheckedChangeListener(onWorkoutChecked);
            tv.setText(workout.workoutName);
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
            TextView view = null;
            if (convertView != null && convertView instanceof TextView)
                view = (TextView) convertView;
            else
                view = new TextView(context);

            view.setGravity(Gravity.CENTER_HORIZONTAL);
            view.setText(getProvider(groupPosition));
            view.setBackgroundResource(android.R.drawable.btn_default_small);
            view.setTextAppearance(context, R.style.ButtonTextGrey);
            return view;
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
                syncManager.connect(onSynchronizerConfiguredCallback, provider, false);
            }
            else {
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

                HashSet<String> tmp = new HashSet<String>();
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
