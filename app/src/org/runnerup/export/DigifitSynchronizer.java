/* Copyright (c) 2013, Sean Rees <sean@rees.us>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.runnerup.export;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.TCX;
import org.runnerup.export.util.Part;
import org.runnerup.export.util.StringWritable;
import org.runnerup.export.util.SyncHelper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.FROYO)
public class DigifitSynchronizer extends DefaultSynchronizer {
    public static final String DIGIFIT_URL = "http://my.digifit.com";

    public static final String NAME = "Digifit";

    public static void main(String args[]) throws Exception {
        if (args.length < 2) {
            Log.e("DigifitSynchronizer", "usage: DigifitSynchronizer username password");
            System.exit(1);
        }

        String username = args[0];
        String password = args[1];

        DigifitSynchronizer du = new DigifitSynchronizer(null);
        du.init(username, password);

        Log.e("DigifitSynchronizer", du.connect().toString());
    }

    private long _id;
    private boolean _loggedin;
    private String _password;
    private String _username;

    DigifitSynchronizer(SyncManager unused) {
    }

    private JSONObject buildRequest(String root, Map<String, String> requestParameters)
            throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject request = new JSONObject(requestParameters);
        json.put(root, request);
        return json;
    }

    private JSONObject callDigifitEndpoint(String url, JSONObject request) throws IOException,
            MalformedURLException,
            ProtocolException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod(RequestMethod.POST.name());
        conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        addCookies(conn);

        OutputStream out = conn.getOutputStream();
        out.write(request.toString().getBytes());
        out.flush();
        out.close();

        JSONObject response = null;
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try {
                response = SyncHelper.parse(conn.getInputStream());
            } finally {
                conn.disconnect();
            }
        }

        return response;
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            case FEED:
            case LIVE:
            case WORKOUT_LIST: // list of prepared work outs (e.g an interval
                               // program)
            case GET_WORKOUT: // download a prepared work out (e.g an interval
                              // program)
            case SKIP_MAP:
                return false;
        }

        return false;
    }

    @Override
    public Status connect() {
        if (!isConfigured()) {
            // user/pass needed
            Status s = Status.NEED_AUTH;
            s.authMethod = Synchronizer.AuthMethod.USER_PASS;
            return s;
        }

        if (_loggedin) {
            return Synchronizer.Status.OK;
        }

        JSONObject credentials = new JSONObject();
        try {
            credentials.put("login", _username);
            credentials.put("password", _password);
        } catch (JSONException e) {
            e.printStackTrace();
            return Synchronizer.Status.INCORRECT_USAGE;
        }

        Status errorStatus = Status.ERROR;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(DIGIFIT_URL + "/site/authenticate")
                    .openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            OutputStream out = conn.getOutputStream();
            out.write(credentials.toString().getBytes());
            out.flush();
            out.close();

            /*
             * A success message looks like:
             * <response><result>success</result></response> A failure message
             * looks like: <response><error code="1102"
             * message="Login or Password is not correct" /></response> For
             * flexibility (and ease), we won't do full XML parsing here. We'll
             * simply look for a few key tokens and hope that's good enough.
             */
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = in.readLine();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                if (line.contains("<result>success</result>")) {
                    // Store the authentication token.
                    getCookies(conn);
                    _loggedin = true;
                    return Status.OK;
                } else {
                    Status s = Status.NEED_AUTH;
                    s.authMethod = Synchronizer.AuthMethod.USER_PASS;

                    Log.e(getName(), "Error: " + line);
                    return s;
                }
            }
            conn.disconnect();
        } catch (Exception ex) {
            errorStatus.ex = ex;
            ex.printStackTrace();
        }
        return errorStatus;
    }

    private void deleteFile(long fileId, String fileType) {
        try {
            String deleteUrl = DIGIFIT_URL + "/rpc/json/userfile/delete_workout?file_id=" + fileId
                    + "&file_type="
                    + fileType;
            HttpURLConnection conn = (HttpURLConnection) new URL(deleteUrl).openConnection();
            conn.setRequestMethod(RequestMethod.GET.name());
            conn.addRequestProperty("Referer", DIGIFIT_URL + "/site/workoutimport");
            addCookies(conn);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void downloadActivity(File dst, String key) throws Exception {
        Map<String, String> exportParameters = new HashMap<String, String>();
        exportParameters.put("id", key);
        exportParameters.put("format", "tcx");

        long fileId = 0;
        int fileSize = 0;

        try {
            JSONObject exportRequest = buildRequest("workout", exportParameters);
            callDigifitEndpoint(DIGIFIT_URL + "/rpc/json/workout/export_web", exportRequest);

            // I have observed Digifit taking >15 seconds to generate a file.
            for (int i = 0; i < 60; i++) {
                JSONObject workoutFile = getWorkoutFileId(key);
                if (workoutFile != null) {
                    fileId = workoutFile.getLong("file_id");
                    fileSize = workoutFile.getInt("file_size");
                    break;
                }

                Thread.sleep(500);
            }

            if (fileId == 0) {
                Log.e(getName(), "export file not ready on Digifit within deadline");
                return;
            }

            String downloadUrl = DIGIFIT_URL + "/workout/download/" + fileId;

            HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            conn.setRequestMethod(RequestMethod.GET.name());
            addCookies(conn);

            InputStream in = new BufferedInputStream(conn.getInputStream());
            OutputStream out = new FileOutputStream(dst);
            int cnt = 0, readLen = 0;
            byte buf[] = new byte[1024];
            while ((readLen = in.read(buf)) != -1) {
                out.write(buf, 0, readLen);
                cnt += readLen;
            }
            Log.e(getName(), "Expected " + fileSize + " bytes, got " + cnt + " bytes: "
                    + (fileSize == cnt ? "OK" : "ERROR"));

            in.close();
            out.close();
            conn.disconnect();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // If we error out above, try to ensure we clean up our mess.
            if (fileId == 0) {
                deleteFile(fileId, "export");
            }
        }
    }

    @Override
    public String getAuthConfig() {
        JSONObject json = new JSONObject();

        try {
            json.put("username", _username);
            json.put("password", _password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return json.toString();
    }

    @Override
    public long getId() {
        return _id;
    }

    @Override
    public String getName() {
        return NAME;
    }

    private String getUploadUrl() throws IOException, MalformedURLException, ProtocolException,
            JSONException {
        String getUploadUrl = DIGIFIT_URL + "/rpc/json/workout/import_workouts_url";
        JSONObject response = callDigifitEndpoint(getUploadUrl, new JSONObject());

        String uploadUrl = response.getJSONObject("response").getJSONObject("upload_url")
                .getString("URL");
        return uploadUrl;
    }

    private JSONObject getWorkoutFileId(String key) throws IOException, MalformedURLException,
            ProtocolException,
            JSONException {
        JSONObject exportListResponse = callDigifitEndpoint(DIGIFIT_URL
                + "/rpc/json/workout/export_workouts_list",
                new JSONObject());
        Log.e(getName(), exportListResponse.toString());

        JSONArray exportList = exportListResponse.getJSONObject("response").getJSONArray(
                "export_list");

        for (int idx = 0;; idx++) {
            JSONObject export = exportList.optJSONObject(idx);
            if (export == null) {
                break;
            }
            long workoutId = export.getLong("workoutid");
            if (("" + workoutId).equals(key)) {
                return export;
            }
        }
        return null;
    }

    @Override
    public void init(ContentValues config) {
        _id = config.getAsLong("_id");

        String auth = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (auth != null) {
            try {
                JSONObject json = new JSONObject(auth);
                String username = json.optString("username", null);
                String password = json.optString("password", null);

                init(username, password);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    protected void init(String username, String password) {
        _username = username;
        _password = password;
    }

    @Override
    public boolean isConfigured() {
        return _username != null && _password != null;
    }

    public Status activityList(List<Pair<String, String>> list) {
        Status errorStatus = Status.ERROR;
        Map<String, String> requestParameters = new HashMap<String, String>();
        DateFormat rfc3339fmt = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss", Locale.US);
        Date now = new Date();

        /*
         * For speed of loading (Digifit can be pokey), this month and last
         * month.
         */
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DATE, -30);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date from = cal.getTime();

        requestParameters.put("sortOrder", "1"); // Reverse chronological.
        requestParameters.put("dateTo", rfc3339fmt.format(now));
        requestParameters.put("dateFrom", rfc3339fmt.format(from));

        try {
            JSONObject request = buildRequest("workout", requestParameters);
            JSONObject response = callDigifitEndpoint(DIGIFIT_URL + "/rpc/json/workout/list",
                    request);

            if (response == null)
                return errorStatus;

            JSONArray workouts = response.getJSONObject("response").getJSONArray("workouts");
            for (int idx = 0;; idx++) {
                JSONObject workout = workouts.optJSONObject(idx);
                if (workout == null) {
                    break;
                }
                StringBuilder title = new StringBuilder(workout.getJSONObject("description")
                        .getString("title"));
                String id = "" + workout.getLong("id");
                String startTime = workout.getJSONObject("summary").getString("startTime");

                // startTime is rfc3339, instead of parsing it, just strip
                // everything but the date.
                title.append(" (").append(startTime.substring(0, startTime.indexOf("T")))
                        .append(")");

                list.add(new Pair<String, String>(id, title.toString()));
            }

            return Status.OK;
        } catch (Exception ex) {
            Log.e(getName(), ex.toString());
            errorStatus.ex = ex;
        }
        return errorStatus;
    }

    @Override
    public void reset() {
        init(null, null);
        _id = 0L;
        _loggedin = false;
    }

    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        Status errorStatus = Status.ERROR;
        TCX tcx = new TCX(db);
        tcx.setAddGratuitousTrack(true);

        try {
            // I wonder why there's an API for getting a special upload path.
            // This seems obtuse.
            String uploadUrl = getUploadUrl();
            Log.e(getName(), "Digifit returned uploadUrl = " + uploadUrl);

            StringWriter wr = new StringWriter();
            tcx.export(mID, wr);

            uploadFileToDigifit(wr.toString(), uploadUrl);

            // We're using the form endpoint for the browser rather than what
            // the API does so we don't have reliable error information. The
            // site returns 200 on both success and failure.
            //
            // TODO: capture traffic from the app in order to use a better API
            // endpoint.
            s = Status.OK;
            s.activityId = mID;
            return s;
        } catch (Exception ex) {
            errorStatus.ex = ex;
            Log.e(getName(), "Digifit returned: " + ex);
        }

        return errorStatus;
    }

    private void uploadFileToDigifit(String payload, String uploadUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod(RequestMethod.POST.name());
        addCookies(conn);

        String filename = "RunnerUp.tcx";

        Part<StringWritable> themePart = new Part<StringWritable>("theme", new StringWritable(
                SyncHelper.URLEncode("site")));
        Part<StringWritable> payloadPart = new Part<StringWritable>("userFiles",
                new StringWritable(payload));
        payloadPart.setFilename(filename);
        payloadPart.setContentType("application/octet-stream");
        Part<?> parts[] = {
                themePart, payloadPart
        };
        SyncHelper.postMulti(conn, parts);

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_MOVED_TEMP) {
            throw new Exception("got a " + code + " response code from upload");
        }

        try {
            // Digifit takes a little while to process an import -- that is,
            // the import we just did above won't show up in this list. In the
            // general case, this will remove *old* imports from Digifit only
            // leaving the user with ~1ish file of import cruft.
            JSONObject response = callDigifitEndpoint(DIGIFIT_URL
                    + "/rpc/json/workout/import_workouts_list",
                    new JSONObject());
            JSONArray uploadList = response.getJSONObject("response").getJSONArray("upload_list");
            for (int idx = 0;; idx++) {
                JSONObject upload = uploadList.optJSONObject(idx);
                if (upload == null) {
                    break;
                }
                // Only delete files we created.
                if (upload.getString("file_name").equals(filename))
                    deleteFile(upload.getLong("file_id"), "import");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
