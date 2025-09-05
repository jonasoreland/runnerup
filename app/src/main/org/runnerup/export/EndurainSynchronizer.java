/*
 * Copyright (C) 2020 Timo Lüttig <runnerup@tluettig.de>
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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.StringWriter;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.format.ExportOptions;
import org.runnerup.export.format.GPX;
import org.runnerup.util.FileNameHelper;
import org.runnerup.workout.FileFormats;
import org.runnerup.workout.Sport;

public class EndurainSynchronizer extends DefaultSynchronizer {

  public static final String NAME = "Endurain";

  private static final String TOKEN_URL_PATH = "/api/v1/token";
  private static final String UPLOAD_URL_PATH = "/api/v1/activities/create/upload";

  private long id = 0;

  private PathSimplifier simplifier;
  private String username;
  private String password;
  private String url;
  private boolean hasCorrectConfig;

  private String access_token = null;

  public EndurainSynchronizer() {
    super();
  }

  public EndurainSynchronizer(PathSimplifier simplifier) {
    this();
    this.simplifier = simplifier;
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

  @Override
  public String getPublicUrl() {
    if (url == null || url.isEmpty()) {
      return "https://your-endurain.local:98";
    }
    return url;
  }

  @Override
  public int getIconId() {
    return R.drawable.service_endurain;
  }

  @Override
  public void init(ContentValues config) {
    id = config.getAsLong("_id");
    String authToken = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
    if (authToken != null) {
      try {
        JSONObject tmp = new JSONObject(authToken);
        //noinspection ConstantConditions
        username = tmp.optString("username", null);
        //noinspection ConstantConditions
        password = tmp.optString("password", null);
        //noinspection ConstantConditions
        url = tmp.optString("url", null);
        hasCorrectConfig = tmp.optBoolean("hasCorrectConfig", false);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public boolean isConfigured() {
    return username != null && password != null && url != null && hasCorrectConfig;
  }

  @NonNull
  @Override
  public String getAuthConfig() {
    JSONObject tmp = new JSONObject();
    try {
      tmp.put("username", username);
      tmp.put("password", password);
      tmp.put("url", url);
      tmp.put("hasCorrectConfig", hasCorrectConfig);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return tmp.toString();
  }

  private Status parseAuthData(JSONObject obj) {
    try {
      if (obj.has("access_token")) {
        access_token = obj.getString("access_token");
      }
      hasCorrectConfig = true;
      return Status.OK;

    } catch (JSONException e) {
      e.printStackTrace();
    }
    return Status.ERROR;
  }

  @Override
  public void reset() {
    username = null;
    password = null;
    url = null;
    hasCorrectConfig = false;
  }

  @NonNull
  @Override
  public Status connect() {
    Status s = Status.NEED_AUTH;
    s.authMethod = AuthMethod.USER_PASS_URL;
    if (username == null || password == null || url == null) {
      return s;
    }

    if (access_token != null) {
      return Status.OK;
    }

    try {
      OkHttpClient client = getAuthClient();
      RequestBody formBody =
          new FormBody.Builder()
              .add("grant_type", "password")
              .add("username", username)
              .add("password", password)
              .build();

      Request request = new Request.Builder().url(url + TOKEN_URL_PATH).post(formBody).build();

      Response response = client.newCall(request).execute();

      if (response.isSuccessful()) {
        JSONObject obj = new JSONObject(response.body() != null ? response.body().string() : "");
        response.close();
        return parseAuthData(obj);
      }

      response.close();
      return Status.ERROR;
    } catch (Exception e) {
      return Status.ERROR;
    }
  }

  private OkHttpClient getAuthClient() {
    return new OkHttpClient()
        .newBuilder()
        .addInterceptor(
            chain ->
                chain.proceed(
                    chain.request().newBuilder().addHeader("X-Client-Type", "mobile").build()))
        .addInterceptor(
            chain -> {
              if (!TextUtils.isEmpty(access_token)) {
                return chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("Authorization", "Bearer " + access_token)
                        .build());
              }
              return chain.proceed(chain.request());
            })
        .build();
  }

  @NonNull
  @Override
  public Status upload(SQLiteDatabase db, final long mID) {
    Status s = connect();
    if (s != Status.OK) {
      return s;
    }

    Sport sport = Sport.RUNNING;
    long startTime = 0;

    try {
      String[] columns = {
        Constants.DB.ACTIVITY.SPORT, Constants.DB.ACTIVITY.START_TIME,
      };
      try (Cursor c =
          db.query(Constants.DB.ACTIVITY.TABLE, columns, "_id = " + mID, null, null, null, null)) {
        if (c.moveToFirst()) {
          sport = Sport.valueOf(c.getInt(0));
          startTime = c.getLong(1);
        }
      }

      String fileBase = FileNameHelper.getExportFileNameWithModel(startTime, sport.TapiriikType());

      GPX gpx = new GPX(db, ExportOptions.getDefault(), simplifier);
      StringWriter writer = new StringWriter();
      gpx.export(mID, writer);
      s = uploadFile(writer, fileBase, FileFormats.GPX.getValue());
    } catch (Exception e) {
      Log.e(getName(), "Error uploading, exception: ", e);
      s = Status.ERROR;
      s.ex = e;
    }
    return s;
  }

  private Status uploadFile(StringWriter writer, String fileBase, String fileExt) {
    Status s;
    try {
      OkHttpClient client = getAuthClient();

      RequestBody requestBody =
          new MultipartBody.Builder()
              .setType(MultipartBody.FORM)
              .addFormDataPart(
                  "file",
                  (fileBase.replace("/", "") + fileExt),
                  RequestBody.create(
                      MediaType.parse("application/" + fileExt + "+xml"), writer.toString()))
              .build();

      Request request =
          new Request.Builder()
              .url(url + UPLOAD_URL_PATH)
              .addHeader("Content-Type", "application/json")
              .method("POST", requestBody)
              .build();

      int responseCode;
      Response response = client.newCall(request).execute();

      s = response.isSuccessful() ? Status.OK : Status.ERROR;
    } catch (Exception e) {
      s = Status.ERROR;
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
}
