/*
 * Copyright (C) 2021 jonas.oreland@gmail.com
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

package org.runnerup.export;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.export.format.Summary;
import org.runnerup.util.Formatter;

import java.io.IOException;

public class AlgorandSynchronizer extends DefaultSynchronizer {
    public static final String NAME = "Algorand";
    private static final String scheme = "algorand://AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAY5HFKQ";

    private long id = 0;
    private Context mContext;
    private Boolean enabled = false;

    private AlgorandSynchronizer() {}

    AlgorandSynchronizer(Context context) {
        this();
        this.mContext = context;
    }

    @Override
    public long getId() {
        return id;
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @DrawableRes
    @Override
    public int getIconId() {
        return R.drawable.service_algorand;
    }

    @ColorRes
    @Override
    public int getColorId() {
        return R.color.colorPrimary;
    }

    @Override
    public void init(ContentValues config) {
        String authConfig = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        if (authConfig != null) {
            try {
                JSONObject tmp = new JSONObject(authConfig);
                enabled = tmp.optBoolean("enabled");
            } catch (JSONException e) {
                Log.w(getName(), "init: Dropping config due to failure to parse json from " + authConfig + ", " + e);
            }
        }
        id = config.getAsLong("_id");
    }

    @NonNull
    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("enabled", enabled);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return tmp.toString();
    }


    @NonNull
    @Override
    public String overrideAuthConfig(ContentValues config) {
        AlgorandSynchronizer f = new AlgorandSynchronizer();
        f.enabled = config.getAsBoolean("enabled");
        return f.getAuthConfig();
    }

    @Override
    public boolean isConfigured() {
        return enabled == true;
    }

    @Override
    public void reset() {
        enabled = false;
    }

    @NonNull
    @Override
    public Status connect() {
        try {
            if (enabled != true) {
                Status s = Status.NEED_AUTH;
                s.authMethod = AuthMethod.ENABLE;
                return s;
            }
            return Status.OK;
        } catch(Exception ex) {
            Status s = Status.ERROR;
            s.ex = ex;
            return s;
        }
    }

    @Override
    public void validatePermissions(AppCompatActivity activity, Context context) throws Exception {
        if (!appInstalledOrNot("com.algorand.android")) {
            throw new Exception(mContext.getString(R.string.algorand_app_required));
        }
    }

    private boolean appInstalledOrNot(String uri) {
        PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
        }

        return false;
    }

    @NonNull
    @Override
    public Status upload(SQLiteDatabase db, final long mID) {
        Status s  = Status.OK;
        s.activityId = mID;
        if (s != Status.OK) {
            return s;
        }

        try {
            Formatter formatter = new Formatter(mContext);
            Summary summary = new Summary(db, formatter);
            String summaryStr = summary.Summary(mID);
            Log.w("AlgorandSynchronizer", summaryStr);

            // Send transaction to Algorand Wallet application.
            String query = scheme + "?amount=0&note=" + summaryStr;
            Uri uri = Uri.parse(query);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                mContext.startActivity(intent);
            }
            catch (Exception e) {
                s = Status.ERROR;
                s.ex = e;
                return s;
            }
        } catch (IOException ex) {
            Log.w("AlgorandSynchronizer", "IOException: " + ex.toString());
        }

        return s;
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void logout() {
        enabled = false;
    }
}
