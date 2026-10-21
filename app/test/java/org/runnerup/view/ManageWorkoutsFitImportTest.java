package org.runnerup.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.BaseCursor;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowDialog;
import org.runnerup.R;
import org.runnerup.workout.WorkoutSerializer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ManageWorkoutsFitImportTest {
  @Test
  @SuppressWarnings("deprecation")
  public void selectedFitDocumentPreviewsSavesAndOpensRunnerUpWorkoutSetup() throws Exception {
    File savedFile =
        WorkoutSerializer.getFile(RuntimeEnvironment.getApplication(), "running-intervals");
    try {
      byte[] selectedBytes;
      try (InputStream fixture = getClass().getResourceAsStream("/fit/running-intervals.fit")) {
        assertNotNull(fixture);
        selectedBytes = fixture.readAllBytes();
      }
      savedFile.delete();
      Uri selectedUri = Uri.parse("content://org.runnerup.test/workouts/42");
      ShadowContentResolver contentResolver =
          Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver());
      contentResolver.registerInputStream(selectedUri, new ByteArrayInputStream(selectedBytes));
      BaseCursor metadata =
          new BaseCursor() {
            @Override
            public boolean moveToFirst() {
              return true;
            }

            @Override
            public int getCount() {
              return 1;
            }

            @Override
            public String[] getColumnNames() {
              return new String[] {MediaStore.MediaColumns.DISPLAY_NAME};
            }

            @Override
            public int getColumnIndex(String columnName) {
              return MediaStore.MediaColumns.DISPLAY_NAME.equals(columnName) ? 0 : -1;
            }

            @Override
            public void close() {}

            @Override
            public String getString(int columnIndex) {
              return "running-intervals.fit";
            }
          };
      contentResolver.setCursor(selectedUri, metadata);
      Intent viewDocument =
          new Intent(Intent.ACTION_VIEW)
              .setClassName("org.runnerup", ManageWorkoutsActivity.class.getName())
              .setData(selectedUri);

      ManageWorkoutsActivity activity =
          Robolectric.buildActivity(ManageWorkoutsActivity.class, viewDocument).setup().get();
      savedFile = WorkoutSerializer.getFile(activity, "running-intervals");
      savedFile.delete();
      AlertDialog preview = (AlertDialog) ShadowDialog.getLatestDialog();
      assertNotNull(preview);
      assertTrue(preview.isShowing());
      TextView previewText = preview.findViewById(android.R.id.message);
      assertNotNull(previewText);
      String previewContent = previewText.getText().toString();
      assertTrue(previewContent, previewContent.contains("Warm up"));
      assertTrue(previewContent, previewContent.contains("10"));
      assertTrue(previewContent, previewContent.contains("400 m"));

      preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();

      Intent launch = Shadows.shadowOf(activity).getNextStartedActivity();
      assertNotNull(launch);
      assertEquals(MainLayout.class.getName(), launch.getComponent().getClassName());
      assertEquals(StartFragment.TAB_ADVANCED, launch.getStringExtra("mode"));
      String workoutName =
          PreferenceManager.getDefaultSharedPreferences(activity)
              .getString(activity.getString(R.string.pref_advanced_workout), "");
      assertEquals("running-intervals", workoutName);
      savedFile = WorkoutSerializer.getFile(activity, workoutName);
      assertTrue(savedFile.isFile());
      assertEquals(2, WorkoutSerializer.readFile(activity, workoutName).getSteps().size());
    } finally {
      savedFile.delete();
    }
  }
}
