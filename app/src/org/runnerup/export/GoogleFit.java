package org.runnerup.export;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;

import org.runnerup.feed.FeedList;

import java.io.File;
import java.util.List;

/**
 * Created by LFAJER on 2015-01-29.
 */
public class GoogleFit extends Activity implements Uploader {

    private GoogleApiClient mClient = null;

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void init(ContentValues config) {
        mClient = new GoogleApiClient.Builder(this).addApi(Fitness.API).addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE)).build();
    }

    @Override
    public String getAuthConfig() {
        return null;
    }

    @Override
    public Intent getAuthIntent(Activity activity) {
        return null;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void reset() {

    }

    @Override
    public Status connect() {
        return null;
    }

    @Override
    public Status getAuthResult(int resultCode, Intent data) {
        return null;
    }

    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        return null;
    }

    @Override
    public boolean checkSupport(Feature f) {

        switch (f) {
            case UPLOAD:
                return true;
            case FEED:
            case LIVE:
            case WORKOUT_LIST:
            case GET_WORKOUT:
            case SKIP_MAP:
                return false;
        }

        return false;
    }

    @Override
    public Status listWorkouts(List<Pair<String, String>> list) {
        return null;
    }

    @Override
    public void downloadWorkout(File dst, String key) throws Exception {

    }

    @Override
    public void logout() {

    }

    @Override
    public Status getFeed(FeedList.FeedUpdater feedUpdater) {
        return null;
    }
}
