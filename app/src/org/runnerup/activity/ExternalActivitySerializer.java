package org.runnerup.activity;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.db.DBHelper;
import org.runnerup.export.Uploader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Created by itdog on 18.01.15.
 */
public abstract class ExternalActivitySerializer<T extends Uploader>{
    protected SQLiteDatabase mDB = null;
    protected Context context;

    public static final String ACTIVITIES_DIR = "activities";

    public static File getFile(Context ctx, String name) {
        return new File(ctx.getDir(ACTIVITIES_DIR, 0).getPath() + File.separator + name);
    }

    public static void writeJsonToFile(JSONObject obj, File dst) throws Exception {
        if (obj != null) {
            FileOutputStream out = new FileOutputStream(dst);
            out.write(obj.toString().getBytes());
            out.close();
        }
    }

    public ExternalActivitySerializer(Context context){
        this.mDB = new DBHelper(context).getReadableDatabase();
        this.context = context;

    }

    public abstract SportActivity deserialize(File f) throws FileNotFoundException, JSONException;
}
