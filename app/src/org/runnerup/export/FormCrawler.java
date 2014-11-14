/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.Uploader.Status;
import org.runnerup.feed.FeedList.FeedUpdater;
import org.runnerup.util.Constants.DB;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Pair;

@TargetApi(Build.VERSION_CODES.FROYO)
public class FormCrawler {

    protected Set<String> cookies = new HashSet<String>();
    protected FormValues formValues = new FormValues();

    public FormCrawler() {
        super();
        logout();
    }

    public void logout() {
        cookies.clear();
        formValues.clear();
    }

    protected interface Writable {
        void write(OutputStream out) throws IOException;
    }

    protected class StringWritable implements Writable {
        String s;

        public StringWritable(String s) {
            this.s = s;
        }

        public void write(OutputStream out) throws IOException {
            out.write(s.getBytes());
        }
    }

    class Part<Value extends Writable> {
        public Part(String name, Value value) {
            this.name = name;
            this.value = value;
        }

        String name = null;
        String filename = null;
        String contentType = null;
        String contentTransferEncoding = null;
        Value value = null;
    }

    public static String URLEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return s;
        }
    }

    public static class FormValues extends HashMap<String, String> implements Writable {
        /**
		 * 
		 */
        private static final long serialVersionUID = -5681066662144155533L;

        public FormValues() {
            super();
        }

        @Override
        public void write(OutputStream o) throws IOException {
            boolean first = true;
            DataOutputStream out = new DataOutputStream(o);
            for (String k : keySet()) {
                if (!first)
                    out.writeByte('&');
                out.writeBytes(URLEncode(k));
                out.writeByte('=');
                out.writeBytes(URLEncode(get(k)));
                first = false;
            }
        }

        public String queryString() {
            StringBuilder buf = new StringBuilder();
            boolean first = true;
            for (String k : keySet()) {
                if (!first)
                    buf.append('&');
                buf.append(URLEncode(k));
                buf.append('=');
                buf.append(URLEncode(get(k)));
                first = false;
            }
            return buf.toString();
        }
    }

    public static void postMulti(HttpURLConnection conn, Part<?> parts[]) throws IOException {
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****" + Long.toString(System.currentTimeMillis())
                + "*****";
        conn.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary);
        DataOutputStream outputStream = new DataOutputStream(
                conn.getOutputStream());
        for (Part<?> part : parts) {
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\""
                    + part.name + "\"");
            if (part.filename != null)
                outputStream.writeBytes("; filename=\"" + part.filename
                        + "\"");
            outputStream.writeBytes(lineEnd);

            if (part.contentType != null)
                outputStream.writeBytes("Content-Type: " + part.contentType
                        + lineEnd);
            if (part.contentTransferEncoding != null)
                outputStream.writeBytes("Content-Transfer-Encoding: "
                        + part.contentTransferEncoding + lineEnd);
            outputStream.writeBytes(lineEnd);
            part.value.write(outputStream);
            outputStream.writeBytes(lineEnd);
        }
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
        outputStream.flush();
        outputStream.close();
    }

    protected void addCookies(HttpURLConnection conn) {
        boolean first = true;
        StringBuilder buf = new StringBuilder();
        for (String cookie : cookies) {
            if (!first)
                buf.append("; ");
            buf.append(cookie.split(";", 2)[0]);
            first = false;
        }
        conn.addRequestProperty("Cookie", buf.toString());
    }

    protected void getCookies(HttpURLConnection conn) {
        List<String> connCookies = conn.getHeaderFields().get("Set-Cookie");
        /**
         * work-around for weird android 2.2 bug ref
         * http://stackoverflow.com/questions
         * /9134657/nullpointer-exception-with-
         * cookie-on-android-2-2-works-fine-on-2-3-and-above
         */
        if (connCookies == null)
            connCookies = conn.getHeaderFields().get("set-cookie");

        if (connCookies != null) {
            cookies.addAll(connCookies);
        }
    }

    protected void clearCookies() {
        cookies.clear();
    }

    protected String getFormValues(HttpURLConnection conn) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder buf = new StringBuilder();
        String s = null;
        while ((s = in.readLine()) != null) {
            buf.append(s);
        }
        String html = buf.toString();
        Map<String, String> values = parseHtml(html);
        formValues.putAll(values);
        return html;
    }

    private Map<String, String> parseHtml(String html) {
        Matcher matcher = inputPattern.matcher(html);
        Map<String, String> parameters = new HashMap<String, String>();

        while (matcher.find()) {
            Map<String, String> attributes = parseAttributes(matcher.group(1));
            String name = attributes.get("name");
            if (name != null) {
                String value = attributes.get("value");
                if (value == null) {
                    value = "";
                }
                parameters.put(name, value);
            }
        }
        return parameters;
    }

    private static Map<String, String> parseAttributes(String attributesStr) {
        Map<String, String> attributes = new HashMap<String, String>();
        Matcher matcher = attributePattern.matcher(attributesStr);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = "";
            String g = matcher.group(2).trim();
            if (g != null) {
                value = g;
            }
            attributes.put(key, value.trim());
        }
        return attributes;
    }

    /**
     * The regex pattern to find a form input parameter in HTML.
     */
    private static final Pattern inputPattern =
            Pattern.compile("<input(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern attributePattern =
            Pattern.compile("(\\w+)=\"(.*?)\"");

    protected String findName(Set<String> names, String s) {
        for (String k : names) {
            if (k.contains(s))
                return k;
        }
        return null;
    }

    /** Below are default empty methods from Uploader */
    public Status getAuthResult(int resultCode, Intent data) {
        return Status.OK;
    }

    public Intent getAuthIntent(Activity a) {
        return null;
    }

    public boolean checkSupport(Uploader.Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            case FEED:
            case GET_WORKOUT:
            case LIVE:
            case WORKOUT_LIST:
            case SKIP_MAP:
        }
        return false;
    }

    public Status listWorkouts(List<Pair<String, String>> list) {
        return Status.OK;
    }

    public void downloadWorkout(File dst, String key) throws Exception {
    }

    public Status getFeed(FeedUpdater feedUpdater) {
        return Status.OK;
    }

    protected void postData(HttpURLConnection conn, FormValues fv) throws IOException {
        OutputStream wr = new BufferedOutputStream(
                conn.getOutputStream());
        if (fv != null) {
            fv.write(wr);
        }
        wr.flush();
        wr.close();
    }

    public Status upload(SQLiteDatabase db, long mID) {
        return Status.OK;
    }

    /**
     * For feed generation...
     */
    public static void setName(ContentValues c, String string) {
        // Jonas Oreland
        if (string.contains(" ")) {
            int index = string.indexOf(' ');
            c.put(DB.FEED.USER_FIRST_NAME, string.substring(0, index).trim());
            c.put(DB.FEED.USER_LAST_NAME, string.substring(index).trim());
        } else {
            c.put(DB.FEED.USER_FIRST_NAME, string);
        }
    }

    public static JSONObject parse(InputStream in) throws JSONException {
        final Scanner s = new Scanner(in);
        final JSONObject o = new JSONObject(s.useDelimiter("\\A").next());
        s.close();
        return o;
    }

    public static JSONObject parse(Reader in) throws JSONException {
        final Scanner s = new Scanner(in);
        final JSONObject o = new JSONObject(s.useDelimiter("\\A").next());
        s.close();
        return o;
    }
}
