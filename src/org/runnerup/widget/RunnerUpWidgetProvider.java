package org.runnerup.widget;

import org.runnerup.R;
import org.runnerup.gpstracker.GpsTracker;
import org.runnerup.view.MainLayout;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
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
	public static final String STOPPED = "org.runnerup.widget.STOPPED";

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		setViewStartState(context, appWidgetManager, appWidgetIds);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
		if (intent.getAction().equals(UPDATE)) {
			for (int i = 0; i < appWidgetIds.length; i++) {
				int appWidgetId = appWidgetIds[i];

				updateViewValues(context, appWidgetManager, appWidgetId,
						getContentPair(context, intent, appWidgetId, 1),
						getContentPair(context, intent, appWidgetId, 2),
						getContentPair(context, intent, appWidgetId, 3),
						intent.getBooleanExtra("isPaused", false));
			}
		} else if (intent.getAction().equals(STOPPED)) {
			setViewStartState(context, appWidgetManager, appWidgetIds);
		}

		super.onReceive(context, intent);
	}

	private void setViewStartState(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		for (int i = 0; i < appWidgetIds.length; i++) {
			int appWidgetId = appWidgetIds[i];

			Intent intent = new Intent(context, MainLayout.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
			views.setImageViewResource(R.id.togglePause, android.R.drawable.ic_media_play);
			views.setOnClickPendingIntent(R.id.togglePause, pendingIntent);

			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}

	private Pair<String, String> getContentPair(Context context, Intent intent, int appWidgetId, int i) {
		String key = ConfigureWidgetActivity.loadSetting(context, appWidgetId, i);
		String value = intent.getStringExtra(key);
		key = key.split(" ")[0].substring(0, 1) + key.split(" ")[1].substring(0, 1) + ":";
		return new Pair<String, String>(key, value);
	}

	public void updateViewValues(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Pair<String, String> first, Pair<String, String> second, Pair<String, String> third, boolean isPaused) {
		RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
		view.setTextViewText(R.id.widget1label, first.first);
		view.setTextViewText(R.id.widget1value, first.second);
		view.setTextViewText(R.id.widget2label, second.first);
		view.setTextViewText(R.id.widget2value, second.second);
		view.setTextViewText(R.id.widget3label, third.first);
		view.setTextViewText(R.id.widget3value, third.second);
		if (isPaused) {
			view.setImageViewResource(R.id.togglePause, android.R.drawable.ic_media_play);
		} else {
			view.setImageViewResource(R.id.togglePause, android.R.drawable.ic_media_pause);
		}

		final Intent pauseIntent = new Intent(GpsTracker.BROADCAST_PAUSE);
		final PendingIntent pendingPauseIntent = PendingIntent.getBroadcast(context, 0,
				pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		view.setOnClickPendingIntent(R.id.togglePause, pendingPauseIntent);
		appWidgetManager.updateAppWidget(appWidgetId, view);
	}

	static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, String titlePrefix) {
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
		appWidgetManager.updateAppWidget(appWidgetId, views);
	}
}
