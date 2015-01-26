package org.runnerup.export;

import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import org.runnerup.activity.ExternalActivitySerializer;

import java.io.File;
import java.util.List;

/**
 * Created by itdog on 18.01.15.
 */
public interface Downloader {
    /**
     * List activities!
     *
     * @return list of Pair<Uploader,Workout>
     */
    public Uploader.Status listActivities(List<Pair<String, String>> list);

    /**
     * List activities!
     *
     * @return list of Pair<Uploader,Workout>
     */
    public void downloadActivity(File dst, String key) throws Exception;

    public ExternalActivitySerializer getActivitySerializer();

    public String getName();
}
