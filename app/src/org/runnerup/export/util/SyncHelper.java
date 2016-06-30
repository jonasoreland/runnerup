/*
 * Copyright (C) 2014 paradix@10g.pl
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

package org.runnerup.export.util;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TargetApi(Build.VERSION_CODES.FROYO)
public final class SyncHelper {

    /**
     * The regex pattern to find a form input parameter in HTML.
     */
    public static final Pattern inputPattern =
            Pattern.compile("<input(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern attributePattern =
            Pattern.compile("(\\w+)=\"(.*?)\"");

    private SyncHelper() {
        throw new UnsupportedOperationException();
    }

    public static String URLEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return s;
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
                    + part.getName() + "\"");
            if (part.getFilename() != null)
                outputStream.writeBytes("; filename=\"" + part.getFilename()
                        + "\"");
            outputStream.writeBytes(lineEnd);

            if (part.getContentType() != null)
                outputStream.writeBytes("Content-Type: " + part.getContentType()
                        + lineEnd);
            if (part.getContentTransferEncoding() != null)
                outputStream.writeBytes("Content-Transfer-Encoding: "
                        + part.getContentTransferEncoding() + lineEnd);
            outputStream.writeBytes(lineEnd);
            part.getValue().write(outputStream);
            outputStream.writeBytes(lineEnd);
        }
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
        outputStream.flush();
        outputStream.close();
    }

    public static Map<String, String> parseAttributes(String attributesStr) {
        Map<String, String> attributes = new HashMap<String, String>();
        Matcher matcher = attributePattern.matcher(attributesStr);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = "";
            String g = matcher.group(2);
            if (g != null) {
                value = g;
            }
            attributes.put(key, value.trim());
        }
        return attributes;
    }

    /**
     * For feed generation...
     */
    public static void setName(ContentValues c, String string) {
        // Jonas Oreland
        if (string.contains(" ")) {
            int index = string.indexOf(' ');
            c.put(Constants.DB.FEED.USER_FIRST_NAME, string.substring(0, index).trim());
            c.put(Constants.DB.FEED.USER_LAST_NAME, string.substring(index).trim());
        } else {
            c.put(Constants.DB.FEED.USER_FIRST_NAME, string);
        }
    }

    public static JSONObject parse(String in) throws JSONException {
        final Scanner s = new Scanner(in);
        final JSONObject o = new JSONObject(s.useDelimiter("\\A").next());
        s.close();
        return o;
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

    public static String readInputStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder buf = new StringBuilder();
        String s = null;
        while ((s = reader.readLine()) != null) {
            buf.append(s);
        }
        return buf.toString();
    }

    public static void postData(HttpURLConnection conn, FormValues fv) throws IOException {
        OutputStream wr = new BufferedOutputStream(
                conn.getOutputStream());
        if (fv != null) {
            fv.write(wr);
        }
        wr.flush();
        wr.close();
    }

    public static Map<String, String> parseHtml(String html) {
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

    public static String findName(Set<String> names, String s) {
        for (String k : names) {
            if (k.contains(s))
                return k;
        }
        return null;
    }
}
