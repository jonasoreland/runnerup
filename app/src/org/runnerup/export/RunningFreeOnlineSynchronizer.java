package org.runnerup.export;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;
import org.runnerup.export.format.TCX;
import org.runnerup.util.KXmlSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Export integration with runningfreeonline.com.
 * @author jonfre
 */
public class RunningFreeOnlineSynchronizer extends DefaultSynchronizer {

    private long id = 0;

    private String username = null;
    private String secretKey  = null;
    private boolean isConnected = false;

    public static final String NAME = "runningFreeOnline";
    private static final String BASE_URL = "http://www.runsaturday.com/runsaturday/SportTrackSync.asmx";
    private static final String LOG_TAG = RunningFreeOnlineSynchronizer.class.getName();

    public String getName() {
        return NAME;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void init(ContentValues config) {
        id = config.getAsLong("_id");
        final String authToken = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        if (authToken != null) {
            try {
                JSONObject tmp = new JSONObject(authToken);
                username = tmp.optString("username", null);
                secretKey = tmp.optString("password", null);
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Status connect() {
        Status s = Status.NEED_AUTH;
        s.authMethod = Synchronizer.AuthMethod.USER_PASS;
        if (username == null || secretKey == null) {
            return s;
        }

        // TODO Verify login
        isConnected = true;
        return Synchronizer.Status.OK;
    }

    @Override
    public boolean isConfigured() {
        return username != null && secretKey != null;
    }

    @Override
    public String getAuthConfig() {
        JSONObject tmp = new JSONObject();
        try {
            tmp.put("username", username);
            tmp.put("password", secretKey);
        } catch (final JSONException e) {
            e.printStackTrace();
        }

        return tmp.toString();
    }

    @Override
    public void reset() {
        username = null;
        secretKey = null;
        isConnected = false;
    }
    
    @Override
    public Status upload(SQLiteDatabase db, long mID) {
        Status retval = Status.ERROR;
        Exception exception = null;
        HttpURLConnection conn = null;
        try {
            Log.d(LOG_TAG, "Create TCX");
            TCX tcx = new TCX(db);
            StringWriter writer = new StringWriter();
            tcx.exportWithSport(mID, writer);
            byte[] gzippedTcx = gzip(writer.toString());
            Log.d(LOG_TAG, "TCX created, length = " + gzippedTcx.length);

            Log.d(LOG_TAG, "Connect to server");
            conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            conn.addRequestProperty("Content-Type", "text/xml; charset=utf-8");
            conn.addRequestProperty("SOAPAction", "http://www.runsaturday.com/Upload");
            final BufferedWriter wr = new BufferedWriter(new PrintWriter(conn.getOutputStream()));

            final KXmlSerializer mXML = new KXmlSerializer();
            mXML.setOutput(wr);
            mXML.startDocument("UTF-8", true);
            mXML.startTag("", "soap:Envelope");
            mXML.attribute("", "xmlns:soap", "http://www.w3.org/2003/05/soap-envelope");
            mXML.attribute("", "xmlns:run", "http://www.runsaturday.com");

            mXML.startTag("", "soap:Header");
            mXML.startTag("", "run:SportTrackCredentials");
            mXML.startTag("", "run:UserName");
            mXML.text(username);
            mXML.endTag("", "run:UserName");
            mXML.startTag("", "run:SecretKey");
            mXML.text(secretKey);
            mXML.endTag("", "run:SecretKey");
            mXML.endTag("", "run:SportTrackCredentials");
            mXML.endTag("", "soap:Header");

            mXML.startTag("", "soap:Body");
            mXML.startTag("", "run:Upload");
            mXML.startTag("", "run:compressedData");
            mXML.text(Base64.encodeToString(gzippedTcx, Base64.NO_WRAP));
            mXML.endTag("", "run:compressedData");
            mXML.startTag("", "run:skipDuplicates");
            mXML.text("true");
            mXML.endTag("", "run:skipDuplicates");
            mXML.endTag("", "run:Upload");
            mXML.endTag("", "soap:Body");

            mXML.endTag("", "soap:Envelope");
            mXML.endDocument();
            mXML.flush();

            Log.d(LOG_TAG, "Read response");
            final InputStream in = new BufferedInputStream(conn.getInputStream());

            // TODO remove
//            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
//            String line = reader.readLine();
//            while(line != null) {
//                Log.d(LOG_TAG, line);
//                line = reader.readLine();
//            }
//            reader.close();

            final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            final DocumentBuilder dob = dbf.newDocumentBuilder();
            final InputSource is = new InputSource();
            is.setByteStream(in);
            final Document doc = dob.parse(is);
            conn.disconnect();
            conn = null;

            NodeList nodes = doc.getElementsByTagName("Success");
            if (nodes != null && nodes.getLength() == 1) {
                if ("true".equals(nodes.item(0).getTextContent())) {
                    Log.d(LOG_TAG, "Upload success");
                    retval = Status.OK;
                } else {
                    String errorMessage = null;
                    nodes = doc.getElementsByTagName("ErrorMessage");
                    if (nodes != null && nodes.getLength() == 1) {
                        errorMessage = nodes.item(0).getTextContent();
                    }
                    Log.e(LOG_TAG, String.format("Upload failed; Service said '%s'", errorMessage));
                }
            }
        } catch (MalformedURLException e) {
            exception = e;
        } catch (ProtocolException e) {
            exception = e;
        } catch (IOException e) {
            exception = e;
        } catch (ParserConfigurationException e) {
            exception = e;
        } catch (SAXException e) {
            exception = e;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        if (exception != null) {
            retval.ex = exception;
            Log.e(LOG_TAG, "upload failed", exception);
        }
        retval.activityId = mID;
        return retval;
    }

    public static byte[] gzip(String string) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(string.getBytes());
        gos.close();
        byte[] compressed = os.toByteArray();
        os.close();
        return compressed;
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
