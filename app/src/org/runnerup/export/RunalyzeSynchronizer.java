/*
 * Copyright (C) 2016 rickyepoderi@yahoo.es
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

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;
import org.runnerup.export.format.RunalyzePost;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronizer for <em>Runalyze</em> server. See more info in the project <a href="https://runalyze.com/login.php">home page</a>.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class RunalyzeSynchronizer extends DefaultSynchronizer {

    /**
     * Name of the runalyze synchronizer.
     */
    public static final String NAME = "Runalyze";

    /**
     * Public https url for runalyze.
     */
    public static final String PUBLIC_URL = "https://runalyze.com";

    private long _id;
    private String _password;
    private String _username;
    private String _url;

    /**
     * Empty constructor.
     */
    public RunalyzeSynchronizer() {
        _url = PUBLIC_URL;
    }

    /**
     * Initialzes the synchronizer with the information stored in the DB and passed.
     * @param config The auth config stored in the ddbb
     */
    @Override
    public void init(ContentValues config) {
        _id = config.getAsLong("_id");
        String auth = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        if (auth != null) {
            try {
                JSONObject json = new JSONObject(auth);
                _username = json.optString("username", null);
                _password = json.optString("password", null);
                _url = json.optString(Constants.DB.ACCOUNT.URL, null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Features of the synchronizer (just upload)
     * @param f The feature to check
     * @return true of supported, false if not
     */
    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            default:
                return false;
        }
    }

    /**
     * Getter for the synchronizer name
     * @return The synchronizer name
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * return the id of the synchronizer
     * @return The id
     */
    @Override
    public long getId() {
        return _id;
    }

    /**
     * Is the synchronizer configured?
     * @return true if username, password and url is set
     */
    @Override
    public boolean isConfigured() {
        return _username != null  && _password != null && _url != null;
    }

    /**
     * resets the synchronizer.
     */
    @Override
    public void reset() {
        _username = null;
        _password = null;
        _url = PUBLIC_URL;
        clearCookies();
    }

    /**
     * Getter of the auth config with the extra url attribute.
     * @return The auth config information
     */
    @Override
    public String getAuthConfig() {
        JSONObject json = new JSONObject();

        try {
            json.put("username", _username);
            json.put("password", _password);
            json.put(Constants.DB.ACCOUNT.URL, _url);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return json.toString();
    }

    /**
     * Method that writes to the debug the HTML returned by the runalyze calls.
     * @param is The input stream of the connection
     * @return The string with the
     * @throws IOException
     */
    protected String getResponse(InputStream is) throws IOException {
        String result = null;
        BufferedInputStream input = null;
        ByteArrayOutputStream output = null;
        try {
            input = new BufferedInputStream(is);
            output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; // Adjust if you want
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            result =new String(output.toByteArray());
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                Log.e(getName(), "Error closing input", e);
            }
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                Log.e(getName(), "Error closing output", e);
            }
        }
        Log.d(getName(), result);
        return result;
    }

    /**
     * The method was overriden because runalyze return two cookies on login. It seems only the
     * last one should be kept. Original method stores (and then sends) the two of them.
     * @param conn The connection after the login was made
     */
    protected void getCookies(HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        Map<String, String> tmpCookies = new HashMap<>();
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if ("Set-Cookie".equalsIgnoreCase(e.getKey())) {
                for (String v : e.getValue()) {
                    Log.d(getName(), "cookie found = " + e);
                    if (v.indexOf(";") > 0) {
                        v = v.substring(0, v.indexOf(";"));
                    }
                    String cookieName = v.substring(0, v.indexOf("="));
                    String cookieValue = v.substring(v.indexOf("=") + 1, v.length());
                    tmpCookies.put(cookieName, cookieValue);
                }
            }
        }
        // the cookies are in tmpCookies
        for (Map.Entry<String, String> e: tmpCookies.entrySet()) {
            this.cookies.add(e.getKey() + "=" + e.getValue() + "; ");
        }
        // cookies
        Log.d(getName(), "cookies=" + cookies);
    }

    /**
     * Method that performs a silent login in runalyze and save the cookies for later use.
     * @return The status
     */
    protected Status login() {
        OutputStreamWriter writer = null;
        try {
            Log.d(getName(), "Login enter");
            URL login = new URL(_url + "/login.php");
            Log.d(getName(), "URL=" + login);
            HttpURLConnection conn = (HttpURLConnection) login.openConnection();
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write("username=" + URLEncoder.encode(_username, "UTF-8")
                    + "&password=" + URLEncoder.encode(_password, "UTF-8")
                    + "&submit=Login");
            writer.flush();
            conn.connect();
            Log.d(getName(), getResponse(conn.getInputStream()));
            if (conn.getResponseCode() == 302) {
                getCookies(conn);
                Log.d(getName(), "Login exit OK");
                return Status.OK;
            } else {
                Log.e(getName(), "Invalid response code " + conn.getResponseCode());
                Log.d(getName(), "Login exit ERROR");
                return Status.ERROR;
            }
        } catch(MalformedURLException e) {
            Log.e(getName(), "Malformed URL", e);
        } catch(ProtocolException e) {
            Log.e(getName(), "Protocol Exception", e);
        } catch (IOException e) {
            Log.e(getName(), "IO Exception", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                Log.e(getName(), "Error closing writer", e);
            }
        }
        Log.d(getName(), "Login exit ERROR");
        return Status.ERROR;
    }

    /**
     * Connects to the runalyze server.
     * @return The status
     */
    @Override
    public Status connect() {
        if (!isConfigured()) {
            // user/pass needed
            Status s = Status.NEED_AUTH;
            s.authMethod = AuthMethod.USER_PASS_URL;
            return s;
        } else if (!cookies.isEmpty()) {
            // already logged in
            // TODO: do a timestamp to check for inactivity (15min or something)
            return Synchronizer.Status.OK;
        } else {
            // do a login
            return login();
        }
    }

    /**
     * Uploads the activity <em>mID</em> to runalyze.
     * @param db The database
     * @param mID The activity ID
     * @return The status of the upload process
     */
    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }
        OutputStreamWriter writer = null;
        try {
            RunalyzePost post = new RunalyzePost(db);
            URL login = new URL(_url + "/call/call.Training.create.php?json=true");
            HttpURLConnection conn = (HttpURLConnection) login.openConnection();
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            addCookies(conn);
            writer = new OutputStreamWriter(conn.getOutputStream());
            post.export(mID, writer);
            writer.flush();
            conn.connect();
            String response = getResponse(conn.getInputStream());
            if (conn.getResponseCode() != 200 || !response.contains("The activity has been successfully created.")) {
                Log.e(getName(), "Error code: " + conn.getResponseCode());
                cookies.clear();
                return Status.ERROR;
            } else {
                s = Status.OK;
                s.activityId = mID;
                return Status.OK;
            }
        } catch (Exception ex) {
            Log.e(getName(), "Error importing into Runalyze: ", ex);
            return Status.ERROR;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch(IOException e) {
                    Log.e(getName(), "Error closing the writer", e);
                }
            }
        }
    }
}