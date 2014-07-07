package org.runnerup.widget;

import org.runnerup.R;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunnerUpWidgetProvider extends AppWidgetProvider {
	private static final String TAG = "RunnerUpWidgetProvider";
	public static final String UPDATE = "org.runnerup.widget.UPDATE";

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		int call = 1;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		AppWidgetManager mgr = AppWidgetManager.getInstance(context);
		if (intent.getAction().equals(UPDATE)) {
			int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
			for (int i = 0; i < appWidgetIds.length; i++) {
				int appWidgetId = appWidgetIds[i];

				String first = intent.getStringExtra(ConfigureWidgetActivity.loadSetting(context, appWidgetId, 1));
				String second = intent.getStringExtra(ConfigureWidgetActivity.loadSetting(context, appWidgetId, 2));
				String third = intent.getStringExtra(ConfigureWidgetActivity.loadSetting(context, appWidgetId, 3));
				//Toast.makeText(context, "Touched view " + viewIndex, Toast.LENGTH_SHORT).show();
				updateView(context, appWidgetId,
						getContentPair(context, intent, appWidgetId, 1),
						getContentPair(context, intent, appWidgetId, 2),
						getContentPair(context, intent, appWidgetId, 3));
			}
		}
		super.onReceive(context, intent);


	}

	private Pair<String, String> getContentPair(Context context, Intent intent, int appWidgetId, int i) {
		String key = ConfigureWidgetActivity.loadSetting(context, appWidgetId, i);
		String value = intent.getStringExtra(key);
		key = key.split(" ")[0].substring(0, 1) + key.split(" ")[1].substring(0, 1) + ":";
		return new Pair<String, String>(key, value);
	}

	public void updateView(Context context, int appWidgetId, Pair<String, String> first, Pair<String, String> second, Pair<String, String> third) {
		RemoteViews thisViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
		thisViews.setTextViewText(R.id.widget1label, first.first);
		thisViews.setTextViewText(R.id.widget1value, first.second);
		thisViews.setTextViewText(R.id.widget2label, second.first);
		thisViews.setTextViewText(R.id.widget2value, second.second);
		thisViews.setTextViewText(R.id.widget3label, third.first);
		thisViews.setTextViewText(R.id.widget3value, third.second);
		AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, thisViews);
	}

	static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
	                            int appWidgetId, String titlePrefix) {
		Log.d(TAG, "updateAppWidget appWidgetId=" + appWidgetId + " titlePrefix=" + titlePrefix);
		// Getting the string this way allows the string to be localized.  The format
		// string is filled in using java.util.Formatter-style format strings.
//	     CharSequence text = context.getString(R.string.appwidget_text_format,
//	               ExampleAppWidgetConfigure.loadTitlePref(context, appWidgetId),
//               "0x" + Long.toHexString(SystemClock.elapsedRealtime()));

		// Construct the RemoteViews object.  It takes the package name (in our case, it's our
		// package, but it needs this because on the other side it's the widget host inflating
		// the layout from our package).
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
		//views.setTextViewText(R.id.appwidget_text, text);

		// Tell the widget manager
		appWidgetManager.updateAppWidget(appWidgetId, views);
	}
}
