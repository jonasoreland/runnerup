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
import org.runnerup.export.format.GPX;
import org.runnerup.util.FileNameHelper;
import org.runnerup.view.EndurainLoginActivity;
import org.runnerup.workout.FileFormats;
import org.runnerup.workout.Sport;

public class EndurainSynchronizer extends DefaultSynchronizer {

  public static final String NAME = "Endurain";
  private static final String TAG = "EndurainSynchronizer";

  // Updated endpoints based on documentation
  private static final String TOKEN_URL_PATH = "/api/v1/auth/login";
  private static final String MFA_URL_PATH = "/api/v1/auth/mfa/verify";
  private static final String UPLOAD_URL_PATH = "/api/v1/activities/create/upload";

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
      return "https://demo.endurain.com";
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
    Log.d(TAG, "init: authToken=" + authToken);
    if (authToken != null) {
      try {
        JSONObject tmp = new JSONObject(authToken);
        //noinspection ConstantConditions
        username = tmp.optString("username", null);
        //noinspection ConstantConditions
        password = tmp.optString("password", null);
        //noinspection ConstantConditions
        url = tmp.optString("url", null);
        mfaCode = tmp.optString("mfa_code", null); // Read MFA code if present
        hasCorrectConfig = tmp.optBoolean("hasCorrectConfig", false);
        
        access_token = tmp.optString("access_token", null);
        refresh_token = tmp.optString("refresh_token", null);
        csrf_token = tmp.optString("csrf_token", null);
        
        Log.d(TAG, "init parsed: url=" + url + ", hasToken=" + (access_token != null));
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public boolean isConfigured() {
    // We now support both old username/password flow and new PKCE flow (which might not have username/password)
    // But for now, let's keep it simple and assume if we have a URL we are partially configured.
    // The actual check depends on whether we have tokens or credentials.
    // For the dynamic URL task, we just need to ensure 'url' is part of the state.
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
          Log.d(TAG, "MFA required");
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
      Log.d(TAG, "getAuthIntent called with url=" + url);
      Intent intent = new Intent(activity, EndurainLoginActivity.class);
      intent.putExtra(EndurainLoginActivity.EXTRA_URL, url);
      return intent;
  }

  @NonNull
  @Override
  public Status getAuthResult(int resultCode, Intent data) {
      Log.d(TAG, "getAuthResult: resultCode=" + resultCode);
      if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
          access_token = data.getStringExtra(EndurainLoginActivity.EXTRA_ACCESS_TOKEN);
          refresh_token = data.getStringExtra(EndurainLoginActivity.EXTRA_REFRESH_TOKEN);
          csrf_token = data.getStringExtra(EndurainLoginActivity.EXTRA_CSRF_TOKEN);
          hasCorrectConfig = true;
          Log.d(TAG, "getAuthResult: success, token received");
          return Status.OK;
      }
      return Status.ERROR;
  }

  @NonNull
  @Override
  public Status connect() {
    Status s = Status.NEED_AUTH;
    
    Log.d(TAG, "connect: access_token=" + (access_token != null ? "YES" : "NO") + 
               ", username=" + username + ", url=" + url + ", mfaCode=" + (mfaCode != null ? "YES" : "NO"));

    // If we have an access token, we are good
    if (access_token != null) {
      return Status.OK;
    }

    // If we have username/password (legacy flow), try to get token
    if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password) && !TextUtils.isEmpty(url)) {
        Log.d(TAG, "connect: using legacy USER_PASS_URL flow");
        s.authMethod = AuthMethod.USER_PASS_URL;
        try {
          OkHttpClient client = getAuthClient();
          
          // Determine if we are doing initial login or MFA verification
          String endpoint;
          RequestBody formBody;
          
          // Ensure we don't double-append /api/v1 if the user included it
          String baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
          
          if (!TextUtils.isEmpty(mfaCode)) {
              // MFA Verification
              Log.d(TAG, "connect: verifying MFA");
              if (baseUrl.endsWith("/api/v1")) {
                  endpoint = baseUrl + "/auth/mfa/verify";
              } else {
                  endpoint = baseUrl + MFA_URL_PATH;
              }
              
              JSONObject json = new JSONObject();
              json.put("username", username);
              json.put("mfa_code", mfaCode);
              
              formBody = RequestBody.create(MediaType.parse("application/json"), json.toString());
              
          } else {
              // Initial Login
              Log.d(TAG, "connect: initial login");
              if (baseUrl.endsWith("/api/v1")) {
                  endpoint = baseUrl + "/auth/login";
              } else {
                  endpoint = baseUrl + TOKEN_URL_PATH;
              }
              
              // Use application/x-www-form-urlencoded for initial login as per docs
              formBody = new FormBody.Builder()
                  .add("username", username)
                  .add("password", password)
                  .build();
          }

          Log.d(TAG, "connect: requesting " + endpoint);

          Request request = new Request.Builder()
                  .url(endpoint)
                  .addHeader("X-Client-Type", "mobile") // Required by API
                  .post(formBody)
                  .build();
    
          Response response = client.newCall(request).execute();
          String responseBody = response.body() != null ? response.body().string() : "";
          Log.d(TAG, "connect: response code=" + response.code() + ", body=" + responseBody);
    
          if (response.isSuccessful() || response.code() == 202 || response.code() == 200) { // 202 Accepted is used for MFA required
            JSONObject obj = new JSONObject(responseBody);
            response.close();
            return parseAuthData(obj);
          } else if (response.code() == 405) {
              // If 405 Method Not Allowed, try without /api/v1 prefix if it was double appended or server structure is different
              // But we already handle double append.
              // Maybe the server redirects http to https and changes method?
              // Or maybe it requires a trailing slash?
              Log.e(TAG, "connect: 405 Method Not Allowed. Check URL and server configuration.");
          }
    
          response.close();
          return Status.ERROR;
        } catch (Exception e) {
          Log.e(TAG, "connect: exception", e);
          return Status.ERROR;
        }
    } else if (!TextUtils.isEmpty(url)) {
        // If we only have a URL, we need to use the new OAuth2 flow
        Log.d(TAG, "connect: using OAUTH2 flow");
        s.authMethod = AuthMethod.OAUTH2;
    } else {
        // If we don't even have a URL, we need to ask for it (and potentially username/password)
        Log.d(TAG, "connect: missing URL, fallback to USER_PASS_URL");
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

      GPX gpx = new GPX(db, true, true, simplifier);
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

      // Ensure we don't double-append /api/v1 if the user included it
      String baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
      String uploadUrl = baseUrl + UPLOAD_URL_PATH;
      if (baseUrl.endsWith("/api/v1")) {
          uploadUrl = baseUrl + "/activities/create/upload";
      }

      Request request =
          new Request.Builder()
              .url(uploadUrl)
              //.addHeader("Content-Type", "application/json") // Multipart body sets its own content type
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
