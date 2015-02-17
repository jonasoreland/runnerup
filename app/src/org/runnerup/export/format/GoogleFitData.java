package org.runnerup.export.format;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import org.runnerup.R;
import org.runnerup.export.FormCrawler;
import org.runnerup.export.GoogleFitUploader;
import org.runnerup.util.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.runnerup.common.util.Constants.DB;

public class GoogleFitData {

    SQLiteDatabase mDB = null;
    private final Context context;
    private static String projectId = null;
    public static final int SECONDS_TO_MILLIS = 1000;
    public static final int MICRO_TO_NANOS = 1000000;
    public static final int SECONDS_TO_NANOS = 1000000000;

    private static final Map<Integer, Integer> activityType;
    static {
        Map<Integer, Integer> aMap = new HashMap<Integer, Integer>();
        aMap.put(DB.ACTIVITY.SPORT_RUNNING, 8);
        aMap.put(DB.ACTIVITY.SPORT_BIKING, 1);
        activityType = Collections.unmodifiableMap(aMap);
    }
    private static class DataTypeField {

        public DataTypeField(String name, Pair<String, String> format, String dbColumn) {
            this.nameValue = Pair.create(NAME, name);
            this.formatSourceValue = Pair.create(FORMAT, format.first);
            this.formatDataPointValue = format.second;
            this.column = dbColumn;
        }

        static final String NAME = "name";
        static final String FORMAT = "format";
        Pair<String, String> nameValue = null;
        Pair<String, String> formatSourceValue = null;
        String formatDataPointValue = null;
        String column = null;
    }

    private static final Map<DataSourceType, List<DataTypeField>> dataTypeFields;
    static {
        Map<DataSourceType, List<DataTypeField>> fieldsMap = new HashMap<DataSourceType, List<DataTypeField>>();

        List<DataTypeField> fields = new ArrayList<DataTypeField>();
        Pair<String, String> floatPoint = Pair.create("floatPoint", "fpVal");
        Pair<String, String> integer = Pair.create("integer", "intVal");

        fields.add(new DataTypeField("activity", integer, DB.ACTIVITY.SPORT));
        fieldsMap.put(DataSourceType.ACTIVITY_SEGMENT, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("bpm", floatPoint, DB.LOCATION.HR));
        fieldsMap.put(DataSourceType.ACTIVITY_HEARTRATE, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("latitude", floatPoint, DB.LOCATION.LATITUDE));
        fields.add(new DataTypeField("longitude", floatPoint, DB.LOCATION.LONGITUDE));
        fields.add(new DataTypeField("accuracy", floatPoint, DB.LOCATION.ACCURANCY));
        fields.add(new DataTypeField("altitude", floatPoint, DB.LOCATION.ALTITUDE));
        fieldsMap.put(DataSourceType.ACTIVITY_LOCATION, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("speed", floatPoint, DB.LOCATION.SPEED));
        fieldsMap.put(DataSourceType.ACTIVITY_SPEED, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("activity", integer, DB.ACTIVITY.SPORT));
        fields.add(new DataTypeField("duration", integer, DB.ACTIVITY.TIME));
        fields.add(new DataTypeField("num_segments", integer, "1"));
        fieldsMap.put(DataSourceType.ACTIVITY_SUMMARY, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("average", floatPoint, "AVG(" + DB.LOCATION.HR + ")"));
        fields.add(new DataTypeField("max", floatPoint, "MAX(" + DB.LOCATION.HR + ")"));
        fields.add(new DataTypeField("min", floatPoint, "MIN(" + DB.LOCATION.HR + ")"));
        fieldsMap.put(DataSourceType.HEARTRATE_SUMMARY, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("low_latitude", floatPoint, "MIN(" + DB.LOCATION.LATITUDE + ")"));
        fields.add(new DataTypeField("high_latitude", floatPoint, "MAX(" + DB.LOCATION.LATITUDE + ")"));
        fields.add(new DataTypeField("low_longitude", floatPoint, "MIN(" + DB.LOCATION.LONGITUDE + ")"));
        fields.add(new DataTypeField("high_longitude", floatPoint, "MAX(" + DB.LOCATION.LONGITUDE + ")"));
        fieldsMap.put(DataSourceType.LOCATION_SUMMARY, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("average", floatPoint, "AVG(" + DB.LOCATION.SPEED + ")"));
        fields.add(new DataTypeField("max", floatPoint, "MAX(" + DB.LOCATION.SPEED + ")"));
        fields.add(new DataTypeField("min", floatPoint, "MIN(" + DB.LOCATION.SPEED + ")"));
        fieldsMap.put(DataSourceType.SPEED_SUMMARY, fields);

        dataTypeFields = Collections.unmodifiableMap(fieldsMap);
    }
    public enum DataSourceType {

        ACTIVITY_SEGMENT("com.google.activity.segment", "runnerup_activity_segment"),
        ACTIVITY_HEARTRATE("com.google.heart_rate.bpm", "runnerup_activity_heartrate"),
        ACTIVITY_LOCATION("com.google.location.sample", "runnerup_activity_location"),
        ACTIVITY_SPEED("com.google.speed", "runnerup_activity_speed"),
        ACTIVITY_SUMMARY("com.google.activity.summary", "runnerup_activity_summary"),
        HEARTRATE_SUMMARY("com.google.heart_rate.summary", "runnerup_heartrate_summary"),
        LOCATION_SUMMARY("com.google.location.bounding_box", "runnerup_location_summary"),
        SPEED_SUMMARY("com.google.speed.summary", "runnerup_speed_summary");

        final String dataType;

        final String dataName;
        final String sourceType = "derived";

        DataSourceType(String dataType, String dataName) {
            this.dataType = dataType;
            this.dataName = dataName;
        }

        public String getDataStreamId() {
            StringBuilder streamId = new StringBuilder();
            streamId.append(sourceType).append(":").append(dataType).append(":").append(projectId).append(":").append(dataName);
            return streamId.toString();
        }

    }
    public GoogleFitData(final SQLiteDatabase db, String projectId, Context ctx) {
        this.mDB = db;
        this.projectId = projectId;
        this.context = ctx;
    }

    public List<DataSourceType> getActivityDataSourceTypes(long activityId) {
        List<DataSourceType> neededSources = new ArrayList<DataSourceType>();

        String[] pColumns = { DB.LOCATION.TIME };

        // First we export the location
        Cursor cursor = mDB.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + DB.LOCATION.LATITUDE + " IS NOT NULL", null, null, null,
                null);

        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_LOCATION);
            neededSources.add(DataSourceType.LOCATION_SUMMARY);
        }

        // Than if present the heart rate
        cursor = mDB.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + DB.LOCATION.HR + " IS NOT NULL", null, null, null,
                null);

        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_HEARTRATE);
            neededSources.add(DataSourceType.HEARTRATE_SUMMARY);
        }

        // Next will be the speed
        cursor = mDB.query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + DB.LOCATION.SPEED + " IS NOT NULL", null, null, null,
                null);
        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_SPEED);
            neededSources.add(DataSourceType.SPEED_SUMMARY);
        }

        // At last the segments and summary
        neededSources.add(DataSourceType.ACTIVITY_SEGMENT);
        neededSources.add(DataSourceType.ACTIVITY_SUMMARY);
        return neededSources;
    }

    public void exportDataSource(DataSourceType type, Writer writer) {
        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("dataStreamId").value(type.getDataStreamId());
            w.name("dataStreamName").value(type.dataName);
            w.name("type").value(type.sourceType);
            w.name("dataType");
            w.beginObject();
            w.name("name").value(type.dataType);
            w.name("field");
            w.beginArray();
            fillFieldArray(type, w);
            w.endArray();
            w.endObject();
            w.name("application");
            addApplicationObject(w);
            w.endObject();
            System.out.println("Creating new dataSource: " + type.getDataStreamId());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String exportTypeData(DataSourceType source, long activityId, StringWriter w) {
        String requestUrl = "";
        switch(source) {
            case ACTIVITY_SEGMENT:
                requestUrl = exportActivitySegments(source, activityId, w);
                return requestUrl;
            case ACTIVITY_SUMMARY:
                requestUrl = exportActivitySummary(source, activityId, w);
                return requestUrl;
            case ACTIVITY_LOCATION:
            case ACTIVITY_HEARTRATE:
            case ACTIVITY_SPEED:
            case LOCATION_SUMMARY:
            case HEARTRATE_SUMMARY:
            case SPEED_SUMMARY:
                requestUrl = exportSourceDataPoints(source, activityId, w);
                return requestUrl;
        }
        return requestUrl;
    }

    private String exportActivitySegments(DataSourceType source, long activityId, StringWriter writer) {

        String[] pColumns = {
                DB.ACTIVITY.START_TIME, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
        };
        Cursor cursor =  mDB.query(DB.ACTIVITY.TABLE, pColumns,"_id = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        //time as nanos
        long startTime = cursor.getLong(0) * SECONDS_TO_NANOS;
        long endTime = (cursor.getLong(0) + cursor.getLong(1)) * SECONDS_TO_NANOS;
        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("minStartTimeNs").value(startTime);
            w.name("maxEndTimeNs").value(endTime);
            w.name("dataSourceId").value(source.getDataStreamId());
            w.name("point");
            w.beginArray();
            //export points
            w.beginObject();
            w.name("startTimeNanos").value(startTime);
            w.name("endTimeNanos").value(endTime);
            w.name("dataTypeName").value(source.dataType);
            w.name("originDataSourceId").value(source.getDataStreamId());
            w.name("value");
            w.beginArray();
            w.beginObject();
            w.name("intVal").value(activityType.get(cursor.getInt(2)));
            w.endObject();
            w.endArray();
            w.name("rawTimestampNanos").value(startTime);
            w.name("computationTimeMillis").value(System.currentTimeMillis());
            w.endObject();
            //end export points
            w.endArray();
            w.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return getDataSetURLSuffix(source, startTime, endTime);
    }

    private String exportSourceDataPoints(DataSourceType source, long activityId, StringWriter writer) {

        ArrayList<String> pColumns = new ArrayList<String>();
        pColumns.add("MIN("+DB.LOCATION.TIME+")");
        pColumns.add("MAX("+DB.LOCATION.TIME+")");
        Cursor minMaxTime =  mDB.query(DB.LOCATION.TABLE, pColumns.toArray(new String[pColumns.size()]), DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null, null);
        minMaxTime.moveToFirst();

        pColumns = new ArrayList<String>();
        pColumns.add(DB.LOCATION.TIME);
        List<DataTypeField> fields = dataTypeFields.get(source);
        for (DataTypeField field : fields) {
            pColumns.add(field.column);
        }

        Cursor cursor =  mDB.query(DB.LOCATION.TABLE, pColumns.toArray(new String[pColumns.size()]) , DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        long startTime = minMaxTime.getLong(0)* MICRO_TO_NANOS;
        long endTime = minMaxTime.getLong(1)* MICRO_TO_NANOS;

        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("minStartTimeNs").value(startTime);
            w.name("maxEndTimeNs").value(endTime);
            w.name("dataSourceId").value(source.getDataStreamId());
            w.name("point");
            w.beginArray();

            //export points
            do {
                w.beginObject();
                w.name("startTimeNanos").value(cursor.getLong(0)* MICRO_TO_NANOS);
                if (!cursor.isLast()) {
                    cursor.moveToNext();
                    w.name("endTimeNanos").value(cursor.getLong(0)* MICRO_TO_NANOS);
                    cursor.moveToPrevious();
                } else {
                    w.name("endTimeNanos").value(endTime);
                }
                w.name("originDataSourceId").value(source.getDataStreamId()

                );
                w.name("dataTypeName").value(source.dataType);
                w.name("value");
                w.beginArray();
                writeDataPointValues(fields, cursor, w);
                w.endArray();
                w.name("rawTimestampNanos").value(cursor.getLong(0)* MICRO_TO_NANOS);
                w.name("computationTimeMillis").value(System.currentTimeMillis());
                w.endObject();
            } while (cursor.moveToNext());
            //end export points
            w.endArray();
            w.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getDataSetURLSuffix(source, startTime, endTime);
    }

    private String exportActivitySummary(DataSourceType source, long activityId, StringWriter writer) {

        ArrayList<String> pColumns = new ArrayList<String>();
        pColumns.add(DB.ACTIVITY.START_TIME);
        List<DataTypeField> fields = dataTypeFields.get(source);
        for (DataTypeField field : fields) {
            pColumns.add(field.column);
        }

        Cursor cursor =  mDB.query(DB.ACTIVITY.TABLE, pColumns.toArray(new String[pColumns.size()]),"_id = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        //time as nanos
        long startTime = cursor.getLong(0)* SECONDS_TO_NANOS;
        long endTime = (cursor.getLong(0)+cursor.getLong(1))* SECONDS_TO_NANOS;

        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("minStartTimeNs").value(startTime);
            w.name("maxEndTimeNs").value(endTime);
            w.name("dataSourceId").value(source.getDataStreamId());
            w.name("point");
            w.beginArray();
            //export points
            w.beginObject();
            w.name("startTimeNanos").value(startTime);
            w.name("endTimeNanos").value(endTime);
            w.name("dataTypeName").value(source.dataType);
            w.name("originDataSourceId").value(source.getDataStreamId());
            w.name("value");
            w.beginArray();
            writeDataPointValues(fields, cursor, w);
            w.endArray();
            w.name("rawTimestampNanos").value(startTime);
            w.name("computationTimeMillis").value(System.currentTimeMillis());
            w.endObject();
            //end export points
            w.endArray();
            w.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return getDataSetURLSuffix(source, startTime, endTime);
    }

    private void addApplicationObject(JsonWriter w) throws IOException {
        w.beginObject();
        w.name("name").value(context.getString(R.string.app_name));
        w.endObject();
    }

    private void fillFieldArray(DataSourceType type, JsonWriter w) throws IOException {
        for(DataTypeField field : dataTypeFields.get(type)) {
            w.beginObject();
            w.name(field.nameValue.first).value(field.nameValue.second);
            w.name(field.formatSourceValue.first).value(field.formatSourceValue.second);
            w.endObject();
        }
    }

    private void writeDataPointValues(List<DataTypeField> fields, Cursor cursor, JsonWriter w) throws IOException {
        for (DataTypeField field : fields) {
            w.beginObject();
            w.name(field.formatDataPointValue);
            if (field.formatDataPointValue == "intVal") {
                w.value(cursor.getInt(cursor.getColumnIndex(field.column)));
            } else if (field.formatDataPointValue == "fpVal") {
                w.value(cursor.getDouble(cursor.getColumnIndex(field.column)));
            }
            w.endObject();
        }
    }

    public String exportSession(long activityId, Writer writer) {
        String[] pColumns = {
                DB.ACTIVITY.START_TIME, DB.ACTIVITY.TIME, DB.ACTIVITY.COMMENT, DB.ACTIVITY.SPORT
        };
        Cursor cursor =  mDB.query(DB.ACTIVITY.TABLE, pColumns,"_id = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        long startTime = cursor.getLong(0) * SECONDS_TO_MILLIS;
        long endTime = (cursor.getLong(0) + cursor.getLong(1)) * SECONDS_TO_MILLIS;

        String[] sports = context.getResources().getStringArray(R.array.sportEntries);

        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("id").value(context.getString(R.string.app_name) + "-" + startTime + "-" + endTime);
            w.name("name").value((cursor.getInt(3) == 0 ? sports[0] : sports[1]) + ": " + getWorkoutName(startTime));
            w.name("description").value(cursor.getString(3)); //comment
            w.name("startTimeMillis").value(startTime);
            w.name("endTimeMillis").value(endTime);
            w.name("application");
            addApplicationObject(w);
            w.name("activityType").value(activityType.get(cursor.getInt(3)));
            w.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return getSessionURLSuffix(startTime, endTime);

    }

    private String getWorkoutName(long startTime) {
        return DateFormat.getInstance().format(new Date(startTime));
    }

    private String getDataSetURLSuffix(DataSourceType source, long startTime, long endTime) {
        StringBuilder urlSuffix = new StringBuilder();
        urlSuffix.append(GoogleFitUploader.REST_DATASOURCE).append("/").append(FormCrawler.URLEncode(source.getDataStreamId())).append("/").append(GoogleFitUploader.REST_DATASETS).append("/").append(startTime).append("-").append(endTime);
        return urlSuffix.toString();
    }

    private String getSessionURLSuffix(long startTime, long endTime) {
        StringBuilder urlSuffix = new StringBuilder();
        urlSuffix.append(GoogleFitUploader.REST_SESSIONS).append("/").append(context.getString(R.string.app_name)).append("-").append(startTime).append("-").append(endTime);
        return urlSuffix.toString();
    }
}