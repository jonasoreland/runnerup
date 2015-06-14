/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.format.GPX;
import org.runnerup.util.KXmlSerializer;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@TargetApi(Build.VERSION_CODES.FROYO)
public class JoggSESynchronizer extends DefaultSynchronizer {

    public static final String NAME = "jogg.se";
    private static String MASTER_USER = null;
    private static String MASTER_KEY = null;

    public static final String BASE_URL = "http://jogg.se/iphoneservice/iphoneservice.asmx";

    long id = 0;
    private String username = null;
    private String password = null;
    private boolean isConnected = false;

    JoggSESynchronizer(final SyncManager syncManager) {
        if (MASTER_USER == null || MASTER_KEY == null) {
            try {
                final JSONObject tmp = new JSONObject(syncManager.loadData(this));
                MASTER_USER = tmp.getString("MASTER_USER");
                MASTER_KEY = tmp.getString("MASTER_KEY");
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(final ContentValues config) {
        id = config.getAsLong("_id");
        final String authToken = config.getAsString(DB.ACCOUNT.AUTH_CONFIG);
        if (authToken != null) {
            try {
                JSONObject tmp = new JSONObject(authToken);
                username = tmp.optString("username", null);
                password = tmp.optString("password", null);
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        if (username != null && password != null)
            return true;
        return false;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", password);
        } catch (final JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        isConnected = false;
    }

    @Override
    public Status connect() {
        if (isConnected) {
            return Status.OK;
        }

        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        Exception ex = null;
        HttpURLConnection conn = null;
        try {
            /**
             * Login by making an empty save-gpx call and see what error message
             * you get Invalid/"Invalid Userdetails" => wrong user/pass
             * NOK/"Root element is missing" => OK
             */
            final String LOGIN_OK = "NOK";

            conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Host", "jogg.se");
            conn.addRequestProperty("Content-Type", "text/xml");

            final BufferedWriter wr = new BufferedWriter(new PrintWriter(conn.getOutputStream()));
            saveGPX(wr, "");
            wr.flush();
            wr.close();

            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            final DocumentBuilder db = dbf.newDocumentBuilder();
            final InputSource is = new InputSource();
            is.setByteStream(in);
            final Document doc = db.parse(is);
            conn.disconnect();
            conn = null;

            final String path[] = {
                    "soap:Envelope", "soap:Body", "SaveGpxResponse", "SaveGpxResult",
                    "ResponseStatus", "ResponseCode"
            };
            final Node e = navigate(doc, path);
            Log.e(getName(), "reply: " + e.getTextContent());
            if (e != null && e.getTextContent() != null
                    && LOGIN_OK.contentEquals(e.getTextContent())) {
                isConnected = true;
                return Synchronizer.Status.OK;
            }

            return s;
        } catch (final MalformedURLException e) {
            ex = e;
        } catch (final IOException e) {
            ex = e;
        } catch (final ParserConfigurationException e) {
            ex = e;
        } catch (final SAXException e) {
            ex = e;
        }

        if (conn != null)
            conn.disconnect();

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    private static Node navigate(final Document doc, final String[] path) {
        // TODO improve...
        final NodeList list = doc.getElementsByTagName(path[path.length - 1]);
        return list.item(0);
    }

    private void saveGPX(final Writer wr, final String gpx) throws IllegalArgumentException,
            IllegalStateException, IOException {
        final KXmlSerializer mXML = new KXmlSerializer();
        mXML.setFeature(
                "http://xmlpull.org/v1/doc/features.html#indent-output",
                true);
        mXML.setOutput(wr);
        mXML.startDocument("UTF-8", true);
        mXML.startTag("", "soap12:Envelope");
        mXML.attribute("", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        mXML.attribute("", "xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
        mXML.attribute("", "xmlns:soap12", "http://www.w3.org/2003/05/soap-envelope");
        mXML.startTag("", "soap12:Body");
        mXML.startTag("", "SaveGpx");
        mXML.attribute("", "xmlns", "http://jogg.se/IphoneService");

        mXML.startTag("", "gpx");
        mXML.text(android.util.Base64.encodeToString(gpx.getBytes(), Base64.NO_WRAP));
        mXML.endTag("", "gpx");

        mXML.startTag("", "user");
        mXML.startTag("", "Email");
        mXML.text(username);
        mXML.endTag("", "Email");
        mXML.startTag("", "Password");
        mXML.text(password);
        mXML.endTag("", "Password");
        mXML.endTag("", "user");

        mXML.startTag("", "credentials");
        mXML.startTag("", "MasterUser");
        mXML.text(MASTER_USER);
        mXML.endTag("", "MasterUser");
        mXML.startTag("", "MasterKey");
        mXML.text(MASTER_KEY);
        mXML.endTag("", "MasterKey");
        mXML.endTag("", "credentials");
        mXML.endTag("", "SaveGpx");
        mXML.endTag("", "soap12:Body");
        mXML.endTag("", "soap12:Envelope");
        mXML.endDocument();
        mXML.flush();
    }

    @Override
    public Status upload(final SQLiteDatabase db, final long mID) {
        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        Exception ex = null;
        HttpURLConnection conn = null;
        final GPX gpx = new GPX(db);
        try {
            final StringWriter gpxString = new StringWriter();
            gpx.export(mID, gpxString);

            conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Host", "jogg.se");
            conn.addRequestProperty("Content-Type", "text/xml; charset=utf-8");

            final BufferedWriter wr = new BufferedWriter(new PrintWriter(
                    conn.getOutputStream()));
            saveGPX(wr, gpxString.toString());
            wr.flush();
            wr.close();

            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            final DocumentBuilder dob = dbf.newDocumentBuilder();
            final InputSource is = new InputSource();
            is.setByteStream(in);
            final Document doc = dob.parse(is);
            conn.disconnect();
            conn = null;

            final String path[] = {
                    "soap:Envelope", "soap:Body",
                    "SaveGpxResponse", "SaveGpxResult", "ResponseStatus",
                    "ResponseCode"
            };
            final Node e = navigate(doc, path);
            Log.e(getName(), "reply: " + e.getTextContent());
            if (e != null && e.getTextContent() != null
                    && "OK".contentEquals(e.getTextContent())) {
                s = Status.OK;
                s.activityId = mID;
                return s;
            }
            throw new Exception(e.getTextContent());
        } catch (final MalformedURLException e) {
            ex = e;
        } catch (final IOException e) {
            ex = e;
        } catch (final ParserConfigurationException e) {
            ex = e;
        } catch (final SAXException e) {
            ex = e;
        } catch (final DOMException e) {
            ex = e;
            e.printStackTrace();
        } catch (final Exception e) {
            ex = e;
        }

        if (conn != null)
            conn.disconnect();

        s = Synchronizer.Status.ERROR;
        s.ex = ex;
        s.activityId = mID;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;

    }

    @Override
    public boolean checkSupport(Synchronizer.Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            default:
                return false;
        }
    }
}
