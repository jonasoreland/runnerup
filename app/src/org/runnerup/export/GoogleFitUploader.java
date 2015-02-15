package org.runnerup.export;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.format.GoogleFitData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GoogleFitUploader extends GooglePlus implements Uploader {

    public static final String NAME = "GoogleFit";
    public static final String REST_URL = "https://www.googleapis.com/fitness/v1/users/me/";
    public static final String REST_DATASOURCE = "dataSources";
    public static final String REST_DATASETS = "datasets";
    public static final String REST_SESSIONS = "sessions";
    private static final String SCOPES =
            " https://www.googleapis.com/auth/fitness.activity.write " +
                    "https://www.googleapis.com/auth/fitness.activity.read " +
                    "https://www.googleapis.com/auth/fitness.body.write " +
                    "https://www.googleapis.com/auth/fitness.body.read " +
                    "https://www.googleapis.com/auth/fitness.location.write " +
                    "https://www.googleapis.com/auth/fitness.location.read";

    private final Context context;

    private enum RequestMethod { GET, POST, PATCH, PUT; }

    GoogleFitUploader(Context ctx, UploadManager uploadManager) {
        super(uploadManager);
        this.context = ctx;
    }

    @Override
    public String getScopes() {
        return SCOPES;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public Context getContext() {
        return context;
    }

    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case UPLOAD:
                return true;
            case FEED:
            case LIVE:
            case WORKOUT_LIST:
            case GET_WORKOUT:
            case SKIP_MAP:
                return false;
        }
        return false;
    }

    @Override
    public Status upload(SQLiteDatabase db, long mID) {

        Status s;
        if ((s = connect()) != Status.OK) {
            return s;
        }

        //export DataSource if not yet existing
        GoogleFitData gfd = new GoogleFitData(db, getProjectId(), getContext());
        List<String> presentDataSources = listExistingDataSources();
        List<GoogleFitData.DataSourceType> activitySources = gfd.getActivityDataSourceTypes(mID);

        s = exportActivityDataSourceTypes(gfd, presentDataSources, activitySources);
        if (s == Status.ERROR) {
            return s;
        }

        //export all DataPoint types for activity
        for (GoogleFitData.DataSourceType source : activitySources) {
            s = exportActivityData(gfd, source, mID);
            if(s == Status.ERROR) {
                return s;
            }
        }

        //export Session
        s = exportActivitySession(gfd, mID);

        return s;
    }

    private Status exportActivityDataSourceTypes(GoogleFitData gfd, List<String> presentDataSources, List<GoogleFitData.DataSourceType> activitySources) {
        Status status = Status.OK;
        try {
            for (GoogleFitData.DataSourceType type : activitySources) {
                if (!presentDataSources.contains(type.getDataStreamId())) {

                    StringWriter w = new StringWriter();
                    gfd.exportDataSource(type, w);

                    HttpURLConnection connect = getHttpURLConnection("dataSources", RequestMethod.POST);
                    GZIPOutputStream gos = new GZIPOutputStream(connect.getOutputStream());
                    gos.write(w.toString().getBytes());
                    gos.flush();
                    gos.close();

                    int code = connect.getResponseCode();
                    if (code != 200) {
                        System.out.println(parse(new GZIPInputStream(connect.getErrorStream())));
                        return Status.ERROR;
                    } else {
                        System.out.println(parse(new GZIPInputStream(connect.getInputStream())));
                    }

                    connect.disconnect();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            status = Status.ERROR;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return status;
    }

    private Status exportActivityData(GoogleFitData gfd, GoogleFitData.DataSourceType source, long activityId) {
        Status status = Status.ERROR;
        try {
            StringWriter w = new StringWriter();
            String suffix = gfd.exportTypeData(source, activityId, w);

            HttpURLConnection connect = getHttpURLConnection(suffix, RequestMethod.PATCH);
            GZIPOutputStream gos = new GZIPOutputStream(connect.getOutputStream());
            gos.write(w.toString().getBytes());
            gos.flush();
            gos.close();

            int code = connect.getResponseCode();
            if (code != 200) {
                System.out.println(parse(new GZIPInputStream(connect.getErrorStream())));
                return status;
            } else {
                System.out.println(parse(new GZIPInputStream(connect.getInputStream())));
                status = Status.OK;
            }
            connect.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return status;
    }

    private Status exportActivitySession(GoogleFitData gfd, long mID) {
        Status status = Status.ERROR;
        try {
            StringWriter w = new StringWriter();
            String suffix = gfd.exportSession(mID, w);

            HttpURLConnection connect = getHttpURLConnection(suffix, RequestMethod.PUT);

            GZIPOutputStream gos = new GZIPOutputStream(connect.getOutputStream());
            gos.write(w.toString().getBytes());
            gos.flush();
            gos.close();

            int code = connect.getResponseCode();
            if (code != 200) {
                System.out.println(parse(new GZIPInputStream(connect.getErrorStream())));
                return status;
            } else {
                System.out.println(parse(new GZIPInputStream(connect.getInputStream())));
                status = Status.OK;
            }

            connect.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return status;
    }

    private HttpURLConnection getHttpURLConnection(String suffix, RequestMethod requestMethod) throws IOException {
        URL url = new URL(REST_URL + suffix);
        HttpURLConnection connect = (HttpURLConnection) url.openConnection();
        connect.setDoOutput(true);
        connect.addRequestProperty("Authorization", "Bearer " + getAccessToken());
        connect.addRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connect.addRequestProperty("Content-Encoding", "gzip");
        connect.addRequestProperty("Accept-Encoding", "gzip");
        connect.addRequestProperty("User-Agent", "RunnerUp (gzip)");
        if (requestMethod.equals(RequestMethod.PATCH)) {
            connect.addRequestProperty("X-HTTP-Method-Override", "PATCH");
            connect.setRequestMethod(RequestMethod.POST.name());
        } else {
            connect.setRequestMethod(requestMethod.name());
        }
        return connect;
    }

    @Override
    public String getAuthExtra() {
        return "scope=" + FormCrawler.URLEncode(getScopes());
    }

    public List<String> listExistingDataSources() {
        HttpURLConnection conn = null;
        List<String> dataStreamIds = new ArrayList<String>();
        try {
            conn = (HttpURLConnection) new URL(REST_URL + "dataSources").openConnection();
            conn.setRequestProperty("Authorization", "Bearer "
                    + getAccessToken());
            conn.setRequestMethod(RequestMethod.GET.name());
            final InputStream in = new BufferedInputStream(conn.getInputStream());
            final JSONObject reply = parse(in);
            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode == 200) {
                JSONArray data = reply.getJSONArray("dataSource");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject dataSource = data.getJSONObject(i);
                    dataStreamIds.add(dataSource.getString("dataStreamId"));
                }
                return dataStreamIds;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return dataStreamIds;
    }
}
