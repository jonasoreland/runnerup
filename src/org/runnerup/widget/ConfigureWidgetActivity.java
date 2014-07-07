package org.runnerup.widget;

import android.annotation.TargetApi;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import org.runnerup.R;

/**
 * Created by niklas.weidemann on 2014-07-07.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class ConfigureWidgetActivity extends Activity {
	private static final String PREFS_NAME
			= "org.runnerup.widget.ConfigureWidgetActivity";
	private static final String PREF_PREFIX_KEY = "prefix_";

	private Spinner mSpinnerFirst;
	private Spinner mSpinnerSecond;
	private Spinner mSpinnerThird;
	private Button mButtonSave;
	private int mAppWidgetId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED);
		setContentView(R.layout.appwidget_configure);

		mSpinnerFirst = (Spinner) findViewById(R.id.spinner1);
		mSpinnerSecond = (Spinner) findViewById(R.id.spinner2);
		mSpinnerThird = (Spinner) findViewById(R.id.spinner3);

		mButtonSave = (Button) findViewById(R.id.buttonSave);

		mButtonSave.setOnClickListener(saveResult);
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		}

	}

	View.OnClickListener saveResult = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			final Context context = ConfigureWidgetActivity.this;

			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
			RunnerUpWidgetProvider.updateAppWidget(context, appWidgetManager, mAppWidgetId, "");
			saveSpinnerPref(context, mAppWidgetId, 1, mSpinnerFirst.getSelectedItem().toString());
			saveSpinnerPref(context, mAppWidgetId, 2, mSpinnerSecond.getSelectedItem().toString());
			saveSpinnerPref(context, mAppWidgetId, 3, mSpinnerThird.getSelectedItem().toString());
			Intent resultIntent = new Intent();
			resultIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
			setResult(RESULT_OK, resultIntent);
			finish();
		}
	};

	private static void saveSpinnerPref(Context context, int appWidgetId, int id, String text) {
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		String pref = PREF_PREFIX_KEY + id + appWidgetId;
		prefs.putString(pref, text);
		prefs.commit();
	}

	public static String loadSetting(Context context, int appWidgetId, int id) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		String pref = PREF_PREFIX_KEY + id + appWidgetId;
		String value = prefs.getString(pref, null);
		if (value != null) {
			return value;
		} else {
			return "Activity Pace";
		}
	}

}
