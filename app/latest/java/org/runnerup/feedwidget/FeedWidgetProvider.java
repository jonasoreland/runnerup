package org.runnerup.feedwidget;



import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Button;
import android.widget.RemoteViews;

import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.feed.FeedList;
import org.runnerup.util.Formatter;
import org.runnerup.view.FeedActivity;
import org.runnerup.view.MainLayout;
import org.runnerup.view.StartActivity;

import java.util.Set;

public class FeedWidgetProvider extends AppWidgetProvider {
    private DBHelper mDBHelper = null;
    private SyncManager mSyncManager = null;
    static private boolean UpdateInProgress = false;
    private class ProgressDialogStub extends ProgressDialog {
        public ProgressDialogStub(Context context) { super(context); }
        public void setCancelable(boolean cancelable) {}
        public void show() {}
        public void dismiss() {}
        public void setTitle(String title) {}
        public void setMessage(String message) {}
        public Button getButton(int pos) { return null; }
        public void cancel() {}
        public void setCanceledOnTouchOutside(boolean canceled) {}
        public void setMax(int max) {}
        public void setButton(int pos, String arg, DialogInterface.OnClickListener listener) {}
        public void setProgress(int progress) {}
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra("reload_feed")) {
            if (UpdateInProgress) {
                Log.e(getClass().getSimpleName(), "Feed already being refreshed, cancelling this request");
            } else {
                Log.i(getClass().getSimpleName(), "Downloading latest feed...");
                if (mDBHelper == null) {
                    mDBHelper = new DBHelper(context);
                    mSyncManager = new SyncManager(context, new ProgressDialogStub(context));
                }

                mSyncManager.clear();
                FeedList feed = new FeedList(mDBHelper);
                feed.reset();
                feed.getList().clear();
                UpdateInProgress = true;
                Set<String> set = mSyncManager.feedSynchronizersSet();
                // this will trigger onUpdate automatically
                mSyncManager.synchronizeFeed(new SyncManager.Callback() {
                    @Override
                    public void run(String synchronizerName, Synchronizer.Status status) {
                        UpdateInProgress = false;
                    }
                }, set, feed, null);
            }
        } else {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] ids = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, FeedWidgetProvider.class));
            onUpdate(context, appWidgetManager, ids);
        }
    }

    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.i(getClass().getSimpleName(), "Updating feed widget with id " + appWidgetId);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.feed_widget);

            // FeedWidgetService is taking care of filling the list with latest feed
            Intent adapterIntent = new Intent(context, FeedWidgetService.class);
            views.setRemoteAdapter(R.id.widget_list, adapterIntent);
            views.setEmptyView(R.id.widget_list, R.id.widget_empty);

            // when we click on Runner Up logo, we launch it
            Intent mainActivity = new Intent(context, MainLayout.class);
            views.setOnClickPendingIntent(R.id.widget_app_icon, PendingIntent.getActivity(context, 0, mainActivity, 0));

            // on any other click (including on any item of the list), we will retrieve latest feed
            // and update widget with it
            Intent updateIntent = new Intent();
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra("reload_feed", true);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, 0, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent);
            views.setPendingIntentTemplate(R.id.widget_list, pendingIntent);

            // finally update widget - notifyAppWidgetViewDataChanged is required for data to be
            // reloaded in FeedWidgetService, otherwise nothing is done...
            appWidgetManager.updateAppWidget(appWidgetId, views);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
        }
    }

    public static void RefreshWidget(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] ids = appWidgetManager.getAppWidgetIds(
                 new ComponentName(context, FeedWidgetProvider.class));
        appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }
}

