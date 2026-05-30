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
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import org.runnerup.view.EndurainLoginActivity;
import org.runnerup.workout.FileFormats;
import org.runnerup.workout.Sport;

public class EndurainSynchronizer extends DefaultSynchronizer {

  public static final String NAME = "Endurain";
  private static final String TOKEN_URL_PATH = "/api/v1/auth/login";
  private static final String MFA_URL_PATH = "/api/v1/auth/mfa/verify";
  private static final String UPLOAD_URL_PATH = "/api/v1/activities/create/upload";
  private static final String REFRESH_URL_PATH = "/api/v1/auth/refresh";

  private long id = 0;

  private PathSimplifier simplifier;
  private String username;
  private String password;
  private String url;
  private String mfaCode; // Temporary storage for MFA code
  private boolean hasCorrectConfig;

  private String access_token = null;
  private String refresh_token = null;
  private String csrf_token = null;

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
      return "https://endurain.example.com";
    }
    return url;
  }

  @Override
  public int getIconId() {
    return R.drawable.service_endurain;
  }

  private String sanitizeUrl(String input) {
    if (input == null) return null;
    String res = input.trim();
    while (res.endsWith("/")) {
      res = res.substring(0, res.length() - 1);
    }
    return res;
  }

  private String getEndpoint(String path) {
    if (url == null) return "";
    String baseUrl = url;
    if (baseUrl.endsWith("/api/v1")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 7);
    }
    return baseUrl + path;
  }

  private Status getNeedAuthStatus() {
    Status needAuth = Status.NEED_AUTH;
    if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password) && !TextUtils.isEmpty(url)) {
      needAuth.authMethod = AuthMethod.USER_PASS_URL;
    } else if (!TextUtils.isEmpty(url)) {
      needAuth.authMethod = AuthMethod.OAUTH2;
    } else {
      needAuth.authMethod = AuthMethod.USER_PASS_URL;
    }
    return needAuth;
  }

  @Override
  public void init(ContentValues config) {
    id = config.getAsLong("_id");
    String authToken = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
    if (authToken != null) {
      try {
        JSONObject tmp = new JSONObject(authToken);
        username = tmp.optString("username", null);
        password = tmp.optString("password", null);
        url = sanitizeUrl(tmp.optString("url", null));
        mfaCode = tmp.optString("mfa_code", null); // Read MFA code if present
        hasCorrectConfig = tmp.optBoolean("hasCorrectConfig", false);
        
        access_token = tmp.optString("access_token", null);
        refresh_token = tmp.optString("refresh_token", null);
        csrf_token = tmp.optString("csrf_token", null);

      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public boolean isConfigured() {
    return url != null && !url.isEmpty();
  }

  @NonNull
  @Override
  public String getAuthConfig() {
    JSONObject tmp = new JSONObject();
    try {
      tmp.put("username", username);
      tmp.put("password", password);
      tmp.put("url", url);
      // Do NOT persist mfaCode
      tmp.put("hasCorrectConfig", hasCorrectConfig);
      tmp.put("access_token", access_token);
      tmp.put("refresh_token", refresh_token);
      tmp.put("csrf_token", csrf_token);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return tmp.toString();
  }

  private Status parseAuthData(JSONObject obj) {
    try {
      if (obj.has("mfa_required") && obj.getBoolean("mfa_required")) {
          Status s = Status.NEED_AUTH;
          s.authMethod = AuthMethod.MFA;
          return s;
      }
        
      if (obj.has("access_token")) {
        access_token = obj.getString("access_token");
      }
      if (obj.has("refresh_token")) {
        refresh_token = obj.getString("refresh_token");
      }
      if (obj.has("csrf_token")) {
        csrf_token = obj.getString("csrf_token");
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
    mfaCode = null;
    hasCorrectConfig = false;
    access_token = null;
    refresh_token = null;
    csrf_token = null;
  }

  @NonNull
  @Override
  public Intent getAuthIntent(AppCompatActivity activity) {
      Intent intent = new Intent(activity, EndurainLoginActivity.class);
      intent.putExtra(EndurainLoginActivity.EXTRA_URL, url);
      return intent;
  }

  @NonNull
  @Override
  public Status getAuthResult(int resultCode, Intent data) {
      if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
          access_token = data.getStringExtra(EndurainLoginActivity.EXTRA_ACCESS_TOKEN);
          refresh_token = data.getStringExtra(EndurainLoginActivity.EXTRA_REFRESH_TOKEN);
          csrf_token = data.getStringExtra(EndurainLoginActivity.EXTRA_CSRF_TOKEN);
          hasCorrectConfig = true;
          return Status.OK;
      }
      return Status.ERROR;
  }

  @NonNull
  @Override
  public Status connect() {
    Status s = Status.NEED_AUTH;

    if (access_token != null) {
      return Status.OK;
    }

    if (refresh_token != null) {
      return Status.NEED_REFRESH;
    }

    if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password) && !TextUtils.isEmpty(url)) {
        s.authMethod = AuthMethod.USER_PASS_URL;
        try {
          OkHttpClient client = getAuthClient();

          String endpoint;
          RequestBody formBody;
          
          if (!TextUtils.isEmpty(mfaCode)) {
              endpoint = getEndpoint(MFA_URL_PATH);
              
              JSONObject json = new JSONObject();
              json.put("username", username);
              json.put("mfa_code", mfaCode);
              
              formBody = RequestBody.create(MediaType.parse("application/json"), json.toString());
              
          } else {
              endpoint = getEndpoint(TOKEN_URL_PATH);

              formBody = new FormBody.Builder()
                  .add("username", username)
                  .add("password", password)
                  .build();
          }

          Request request = new Request.Builder()
                  .url(endpoint)
                  .addHeader("X-Client-Type", "mobile") // Required by API
                  .post(formBody)
                  .build();
    
          try (Response response = client.newCall(request).execute()) {
              String responseBody = response.body() != null ? response.body().string() : "";
        
              if (response.isSuccessful() || response.code() == 202 || response.code() == 200) {
                JSONObject obj = new JSONObject(responseBody);
                return parseAuthData(obj);
              }
        
              return Status.ERROR;
          }
        } catch (Exception e) {
          return Status.ERROR;
        }
    } else if (!TextUtils.isEmpty(url)) {
        s.authMethod = AuthMethod.OAUTH2;
    } else {
        s.authMethod = AuthMethod.USER_PASS_URL;
    }
    
    return s;
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
              Request.Builder builder = chain.request().newBuilder();
              if (!TextUtils.isEmpty(access_token)) {
                builder.header("Authorization", "Bearer " + access_token);
              }
              if (!TextUtils.isEmpty(csrf_token)) {
                builder.header("X-CSRF-Token", csrf_token);
              }
              return chain.proceed(builder.build());
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

      String uploadUrl = getEndpoint(UPLOAD_URL_PATH);

      Request request =
          new Request.Builder()
              .url(uploadUrl)
              .method("POST", requestBody)
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (response.isSuccessful()) {
          s = Status.OK;
        } else if (response.code() == 401) {
          // clear token
          access_token = null;
          csrf_token = null;

          s = Status.NEED_REFRESH;
        } else {
          s = Status.ERROR;
        }
      }
    } catch (Exception e) {
      s = Status.ERROR;
    }

    return s;
  }

  @NonNull
  @Override
  public Status refreshToken() {
    if (TextUtils.isEmpty(refresh_token)) {
      return getNeedAuthStatus();
    }

    String endpoint = getEndpoint(REFRESH_URL_PATH);

    try {
      OkHttpClient client = new OkHttpClient();
      RequestBody body = RequestBody.create(MediaType.parse("application/json"), "{}");
      Request request = new Request.Builder()
          .url(endpoint)
          .addHeader("X-Client-Type", "mobile")
          .addHeader("Authorization", "Bearer " + refresh_token)
          .post(body)
          .build();

      try (Response response = client.newCall(request).execute()) {
        String responseBody = response.body() != null ? response.body().string() : "";

        if (response.isSuccessful()) {
          JSONObject obj = new JSONObject(responseBody);
          return parseAuthData(obj);
        }

        // clear token if response is not successful
        access_token = null;
        csrf_token = null;
      }
    } catch (Exception e) {
      Log.e(getName(), "refreshToken: exception during request", e);

      // clear token
      access_token = null;
      csrf_token = null;
    }

    return getNeedAuthStatus();
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
