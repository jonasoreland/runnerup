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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private boolean _version3;
    private String _csrf_token;
    private Map<String,Map<String,String>> _sports;
    private Map<String,Map<String,String>> _types;

    /**
     * Empty constructor.
     */
    public RunalyzeSynchronizer() {
        _url = PUBLIC_URL;
        _sports = new HashMap<>();
        _types = new HashMap<>();
    }

    /**
     * Initialzes the synchronizer with the information stored in the DB and passed.
     * @param config The auth config stored in the ddbb
     */
    @Override
    public void init(ContentValues config) {
        _id = config.getAsLong("_id");
        String auth = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        //Log.d(getName(), "Initializing: " + auth);
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

    protected void getCSRFToken(String response) {
        Pattern pattern = Pattern.compile("<input type=\"hidden\" name=\"_csrf_token\" value=\"([^\"]+)\">");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            _csrf_token = matcher.group(1);
            Log.d(getName(), "CSRF token = " + _csrf_token);
        } else {
            Log.e(getName(), "Failed to get CSRF token");
        }
    }

        /**
         * Runalyze v3 needs to login with a valid session cookie. So we need to first request the
         * login page and then perform the login. Besides this method in v2 returns just a 404 which
         * let us to know it is a 2.x.
         * @return The return code or -1 in case of strange error
         */
    protected int prepareLogin() {
        try {
            URL url = new URL(_url + "/en/login");
            Log.d(getName(), "URL=" + url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.connect();
            clearCookies();
            String response = getResponse(conn.getInputStream());
            if (conn.getResponseCode() == 200) {
                Log.d(getName(), response);
                getCookies(conn);
                getCSRFToken(response);
            }
            return conn.getResponseCode();
        } catch (MalformedURLException e) {
            Log.e(getName(), "Malformed URL", e);
        } catch (ProtocolException e) {
            Log.e(getName(), "Protocol Exception", e);
        } catch (IOException e) {
            Log.e(getName(), "IO Exception", e);
        }
        return -1;
    }


    /**
     * Method that performs a silent login in runalyze and save the cookies for later use.
     * @return The status
     */
    protected Status login() {
        OutputStreamWriter writer = null;
        try {
            Log.d(getName(), "Login enter");
            URL url = new URL(_url + (_version3? "/en/login_check" : "/login.php"));
            Log.d(getName(), "URL=" + url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            addCookies(conn);
            writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write((_version3? "_username=" : "username=" )
                    + URLEncoder.encode(_username, "UTF-8")
                    + (_version3? "&_password=" : "&password=")
                    + URLEncoder.encode(_password, "UTF-8")
                    + ((_version3) ? "&_target_path=" + URLEncoder.encode("/dashboard", "UTF-8") : "")
                    + ((_version3) ? "&_csrf_token=" + URLEncoder.encode(_csrf_token, "UTF-8") : "")
                    + "&submit=Login");
            writer.flush();
            conn.connect();
            String response = getResponse(conn.getInputStream());
            // v3 just redirects you again to /en/login so check it
            if (conn.getResponseCode() == 302 && !response.contains("/en/login")) {
                clearCookies();
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
     * Obtains the select with the name specified as argument.
     * @param page The html page
     * @param selectName The select name to search for
     * @return The string of the select or null
     */
    protected String obtainSelect(String page, String selectName) {
        // find the start of the
        Pattern selectStart = Pattern.compile("<[Ss][Ee][Ll][Ee][Cc][Tt] [^>]*[Nn][Aa][Mm][Ee]\\s*=\\s*[\"']" + selectName + "[\"'][^>]*>");
        Pattern selectEnd = Pattern.compile("</[Ss][Ee][Ll][Ee][Cc][Tt]>");
        Matcher selectStartMatcher = selectStart.matcher(page);
        if (selectStartMatcher.find()) {
            int start = selectStartMatcher.start();
            Matcher selectEndMatcher = selectEnd.matcher(page);
            if (selectEndMatcher.find(start)) {
                int end = selectEndMatcher.end();
                return page.substring(start, end);
            }
        }
        return null;
    }

    /**
     * Method that parses an option html tag and maps all the properties <em>name="value"</em>
     * into a map.
     * @param option The html option to parse
     * @return The map of attributes in the option tag
     */
    protected Map<String,String> parseOption(String option) {
        Map<String,String> options = new HashMap<>();
        Pattern pattern = Pattern.compile("([\\w-_]+)\\s*=\\s*(\"[^\"]*\"|\'[^\']*\')");
        Matcher matcher = pattern.matcher(option);
        while (matcher.find()) {
            String name = matcher.group(1);
            String value =  matcher.group(2).substring(1, matcher.group(2).length() - 1);
            options.put(name, value);
        }
        return options;
    }

    /**
     * Obtains the name of an html option (the value in between).
     * @param option The html option tag
     * @return The name in the option
     */
    protected String obtainName(String option) {
        Pattern pattern = Pattern.compile(">([^<]+)<");
        Matcher matcher = pattern.matcher(option);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Method that parses a page and searches for a select with a specified name. The select
     * is parsed and a map is returned. The map has all the options keyed by the name and the
     * values are another map with the attributes inside the option.
     * @param page The html page
     * @param selectName The name of the select to search for
     * @return A map with all the values in the options
     */
    protected Map<String,Map<String,String>> parseSelect(String page, String selectName) {
        Map<String,Map<String,String>> map = new HashMap<>();
        String select = obtainSelect(page, selectName);
        if (select != null) {
            Pattern optionStart = Pattern.compile("<[Oo][Pp][Tt][Ii][Oo][Nn] ");
            Pattern optionEnd = Pattern.compile("</[Oo][Pp][Tt][Ii][Oo][Nn]>");
            Matcher optionstartMatcher = optionStart.matcher(select);
            while (optionstartMatcher.find()) {
                int start = optionstartMatcher.start();
                Matcher optionEndMatcher = optionEnd.matcher(select);
                if (optionEndMatcher.find(start)) {
                    int end = optionEndMatcher.end();
                    String option = select.substring(start, end);
                    String name = obtainName(option);
                    Map<String, String> values = parseOption(option);
                    if (name != null && values != null && values.containsKey("value")) {
                        map.put(name, values);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Method that requests the <em>/activity/add</em> page and search for the selects of
     * <em>sportid</em> and <em>typeid</em>. This way the synchronizer is aware of the sports
     * and types defined in runalyze by the user. If no one is defined a default sportid will
     * be sent.
     */
    protected void doAdd() {
        try {
            URL add = new URL(_url + "/activity/add");
            HttpURLConnection conn = (HttpURLConnection) add.openConnection();
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            addCookies(conn);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                String response = getResponse(conn.getInputStream());
                _sports = parseSelect(response, "sportid");
                _types = parseSelect(response, "typeid");
            }
            Log.d(getName(), "sports=" + _sports);
            Log.d(getName(), "types=" + _types);
        } catch(MalformedURLException e) {
            Log.e(getName(), "Could not parse sportid and typeid. Malformed URL", e);
        } catch (ProtocolException e) {
            Log.e(getName(), "Could not parse sportid and typeid. Protocol Exception", e);
        } catch (IOException e) {
            Log.e(getName(), "Could not parse sportid and typeid. IO Exception", e);
        }
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
            Status s;
            // do a login and check username and password
            int rc = prepareLogin();
            if (rc == 404) {
                // it's a v2 version
                Log.d(getName(), "Detected version 2.x");
                _version3 = false;
            } else if (rc == 200) {
                // it's v3 version
                Log.d(getName(), "Detected version 3.x");
                _version3 = true;
            } else {
                // strange error
                return Status.ERROR;
            }
            s = login();
            if (Status.OK.equals(s)) {
                doAdd();
            }
            return s;
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
            RunalyzePost post = new RunalyzePost(db, _sports, _types);
            URL url = new URL(_url + (_version3? "/activity/add" : "/call/call.Training.create.php"));
            Log.d(getName(), "URL=" + url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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