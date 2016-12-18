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

package org.runnerup.export.format;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import org.runnerup.R;
import org.runnerup.export.GoogleFitSynchronizer;
import org.runnerup.export.util.SyncHelper;
import org.runnerup.util.JsonWriter;
import org.runnerup.workout.Sport;

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

@TargetApi(Build.VERSION_CODES.FROYO)
public class GoogleFitData {

    public static final int SECONDS_TO_MILLIS = 1000;
    public static final int MICRO_TO_NANOS = 1000000;
    public static final int SECONDS_TO_NANOS = 1000000000;
    private static final Map<Sport, Integer> ACTIVITY_TYPE;
    static {
        Map<Sport, Integer> aMap = new HashMap<>();
        // sports list can be found at https://developers.google.com/fit/rest/v1/reference/activity-types
        aMap.put(Sport.RUNNING, 8);
        aMap.put(Sport.BIKING, 1);
        aMap.put(Sport.OTHER, 4);
        aMap.put(Sport.ORIENTEERING, 4); //not supported so considering unknown
        aMap.put(Sport.WALKING, 7);
        ACTIVITY_TYPE = Collections.unmodifiableMap(aMap);
    }
    private static final Map<DataSourceType, List<DataTypeField>> DATA_TYPE_FIELDS;
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

        DATA_TYPE_FIELDS = Collections.unmodifiableMap(fieldsMap);
    }
    private final Context mContext;
    private final String mProjectId;
    private SQLiteDatabase mDB = null;

    public GoogleFitData(final SQLiteDatabase db, String project, Context ctx) {
        this.mDB = db;
        this.mProjectId = project;
        this.mContext = ctx;
    }

    public final String getProjectId() {
        return mProjectId;
    }

    public final SQLiteDatabase getDB() {
        return mDB;
    }


    public final List<DataSourceType> getActivityDataSourceTypes(long activityId) {
        List<DataSourceType> neededSources = new ArrayList<DataSourceType>();

        String[] pColumns = {DB.LOCATION.TIME};

        // First we export the location
        Cursor cursor = getDB().query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + DB.LOCATION.LATITUDE + " IS NOT NULL", null, null, null,
                null);

        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_LOCATION);
            neededSources.add(DataSourceType.LOCATION_SUMMARY);
        }
        cursor.close();

        // Than if present the heart rate
        cursor = getDB().query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + DB.LOCATION.HR + " IS NOT NULL", null, null, null,
                null);

        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_HEARTRATE);
            neededSources.add(DataSourceType.HEARTRATE_SUMMARY);
        }
        cursor.close();

        // Next will be the speed
        cursor = getDB().query(DB.LOCATION.TABLE, pColumns,
                DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + DB.LOCATION.SPEED + " IS NOT NULL", null, null, null,
                null);
        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_SPEED);
            neededSources.add(DataSourceType.SPEED_SUMMARY);
        }
        cursor.close();

        // At last the segments and summary
        neededSources.add(DataSourceType.ACTIVITY_SEGMENT);
        neededSources.add(DataSourceType.ACTIVITY_SUMMARY);
        return neededSources;
    }

    public final void exportDataSource(DataSourceType type, Writer writer) {
        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("dataStreamId").value(type.getDataStreamId(this));
            w.name("dataStreamName").value(type.getDataName());
            w.name("type").value(type.SOURCE_TYPE);
            w.name("dataType");
            w.beginObject();
            w.name("name").value(type.getDataType());
            w.name("field");
            w.beginArray();
            fillFieldArray(type, w);
            w.endArray();
            w.endObject();
            w.name("application");
            addApplicationObject(w);
            w.endObject();
            Log.i(getClass().getName(), "Creating new dataSource: " + type.getDataStreamId(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public final String exportTypeData(DataSourceType source, long activityId, StringWriter w) {
        String requestUrl = "";
        switch (source) {
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
        Cursor cursor = getDB().query(DB.ACTIVITY.TABLE, pColumns, "_id = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        //time as nanos
        long startTime = cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.START_TIME)) * SECONDS_TO_NANOS;
        long endTime = (cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.START_TIME)) + cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.TIME))) * SECONDS_TO_NANOS;
        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("minStartTimeNs").value(startTime);
            w.name("maxEndTimeNs").value(endTime);
            w.name("dataSourceId").value(source.getDataStreamId(this));
            w.name("point");
            w.beginArray();
            //export points
            w.beginObject();
            w.name("startTimeNanos").value(startTime);
            w.name("endTimeNanos").value(endTime);
            w.name("dataTypeName").value(source.getDataType());
            w.name("originDataSourceId").value(source.getDataStreamId(this));
            w.name("value");
            w.beginArray();
            w.beginObject();
            w.name("intVal").value(ACTIVITY_TYPE.get(Sport.valueOf((cursor.getInt(2)))));
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
        cursor.close();
        return getDataSetURLSuffix(source, startTime, endTime);
    }

    private String exportSourceDataPoints(DataSourceType source, long activityId, StringWriter writer) {

        ArrayList<String> pColumns = new ArrayList<String>();
        pColumns.add("MIN(" + DB.LOCATION.TIME + ") AS MIN");
        pColumns.add("MAX(" + DB.LOCATION.TIME + ")");
        Cursor minMaxTime = getDB().query(DB.LOCATION.TABLE, pColumns.toArray(new String[pColumns.size()]), DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null, null);
        minMaxTime.moveToFirst();

        pColumns = new ArrayList<String>();
        pColumns.add(DB.LOCATION.TIME);
        List<DataTypeField> fields = DATA_TYPE_FIELDS.get(source);
        for (DataTypeField field : fields) {
            pColumns.add(field.getColumn());
        }

        Cursor cursor = getDB().query(DB.LOCATION.TABLE, pColumns.toArray(new String[pColumns.size()]), DB.LOCATION.ACTIVITY + " = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        long startTime = minMaxTime.getLong(0) * MICRO_TO_NANOS;
        long endTime = minMaxTime.getLong(1) * MICRO_TO_NANOS;
        minMaxTime.close();

        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("minStartTimeNs").value(startTime);
            w.name("maxEndTimeNs").value(endTime);
            w.name("dataSourceId").value(source.getDataStreamId(this));
            w.name("point");
            w.beginArray();

            //export points
            do {
                w.beginObject();
                w.name("startTimeNanos").value(cursor.getLong(cursor.getColumnIndex(DB.LOCATION.TIME)) * MICRO_TO_NANOS);
                if (!cursor.isLast()) {
                    cursor.moveToNext();
                    w.name("endTimeNanos").value(cursor.getLong(cursor.getColumnIndex(DB.LOCATION.TIME)) * MICRO_TO_NANOS);
                    cursor.moveToPrevious();
                } else {
                    w.name("endTimeNanos").value(endTime);
                }
                w.name("originDataSourceId").value(source.getDataStreamId(this)

                );
                w.name("dataTypeName").value(source.getDataType());
                w.name("value");
                w.beginArray();
                writeDataPointValues(fields, cursor, w);
                w.endArray();
                w.name("rawTimestampNanos").value(cursor.getLong(cursor.getColumnIndex(DB.LOCATION.TIME)) * MICRO_TO_NANOS);
                w.name("computationTimeMillis").value(System.currentTimeMillis());
                w.endObject();
            } while (cursor.moveToNext());
            //end export points
            w.endArray();
            w.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        cursor.close();
        return getDataSetURLSuffix(source, startTime, endTime);
    }

    private String exportActivitySummary(DataSourceType source, long activityId, StringWriter writer) {

        ArrayList<String> pColumns = new ArrayList<String>();
        pColumns.add(DB.ACTIVITY.START_TIME);
        pColumns.add(DB.ACTIVITY.TIME);
        List<DataTypeField> fields = DATA_TYPE_FIELDS.get(source);
        for (DataTypeField field : fields) {
            pColumns.add(field.getColumn());
        }

        Cursor cursor = getDB().query(DB.ACTIVITY.TABLE, pColumns.toArray(new String[pColumns.size()]), "_id = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        //time as nanos
        long startTime = cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.START_TIME)) * SECONDS_TO_NANOS;
        long endTime = (cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.START_TIME)) + cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.TIME))) * SECONDS_TO_NANOS;

        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("minStartTimeNs").value(startTime);
            w.name("maxEndTimeNs").value(endTime);
            w.name("dataSourceId").value(source.getDataStreamId(this));
            w.name("point");
            w.beginArray();
            //export points
            w.beginObject();
            w.name("startTimeNanos").value(startTime);
            w.name("endTimeNanos").value(endTime);
            w.name("dataTypeName").value(source.getDataType());
            w.name("originDataSourceId").value(source.getDataStreamId(this));
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
        w.name("name").value(mContext.getString(R.string.app_name));
        w.endObject();
    }

    private void fillFieldArray(DataSourceType type, JsonWriter w) throws IOException {
        for (DataTypeField field : DATA_TYPE_FIELDS.get(type)) {
            w.beginObject();
            w.name(field.getNameValue().first).value(field.getNameValue().second);
            w.name(field.getFormatSourceValue().first).value(field.getFormatSourceValue().second);
            w.endObject();
        }
    }

    private void writeDataPointValues(List<DataTypeField> fields, Cursor cursor, JsonWriter w) throws IOException {
        for (DataTypeField field : fields) {
            w.beginObject();
            w.name(field.getFormatDataPointValue());
            if (field.getFormatDataPointValue().equals("intVal")) {
                w.value(cursor.getInt(cursor.getColumnIndex(field.getColumn())));
            } else if (field.getFormatDataPointValue().equals("fpVal")) {
                w.value(cursor.getDouble(cursor.getColumnIndex(field.getColumn())));
            }
            w.endObject();
        }
    }

    public final String exportSession(long activityId, Writer writer) {
        String[] pColumns = {
                DB.ACTIVITY.START_TIME, DB.ACTIVITY.TIME, DB.ACTIVITY.COMMENT, DB.ACTIVITY.SPORT
        };
        Cursor cursor = getDB().query(DB.ACTIVITY.TABLE, pColumns, "_id = " + activityId, null, null, null, null);
        cursor.moveToFirst();

        long startTime = cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.START_TIME)) * SECONDS_TO_MILLIS;
        long endTime = (cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.START_TIME)) + cursor.getLong(cursor.getColumnIndex(DB.ACTIVITY.TIME))) * SECONDS_TO_MILLIS;

        String[] sports = mContext.getResources().getStringArray(R.array.sportEntries);

        JsonWriter w = new JsonWriter(writer);
        try {
            w.beginObject();
            w.name("id").value(mContext.getString(R.string.app_name) + "-" + startTime + "-" + endTime);
            w.name("name").value((cursor.getInt(cursor.getColumnIndex(DB.ACTIVITY.SPORT)) == 0 ? sports[0] : sports[1]) + ": " + getWorkoutName(startTime));
            w.name("description").value(cursor.getString(cursor.getColumnIndex(DB.ACTIVITY.COMMENT))); //comment
            w.name("startTimeMillis").value(startTime);
            w.name("endTimeMillis").value(endTime);
            w.name("application");
            addApplicationObject(w);
            w.name("activityType").value(ACTIVITY_TYPE.get(Sport.valueOf(cursor.getInt(cursor.getColumnIndex(DB.ACTIVITY.SPORT)))));
            w.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }

        cursor.close();
        return getSessionURLSuffix(startTime, endTime);

    }

    private String getWorkoutName(long startTime) {
        return DateFormat.getInstance().format(new Date(startTime));
    }

    private String getDataSetURLSuffix(DataSourceType source, long startTime, long endTime) {
        return GoogleFitSynchronizer.REST_DATASOURCE + "/" + SyncHelper.URLEncode(source.getDataStreamId(this)) + "/" + GoogleFitSynchronizer.REST_DATASETS + "/" + startTime + "-" + endTime;
    }

    private String getSessionURLSuffix(long startTime, long endTime) {
        return GoogleFitSynchronizer.REST_SESSIONS + "/" + mContext.getString(R.string.app_name) + "-" + startTime + "-" + endTime;
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
        static final String SOURCE_TYPE = "derived";
        private final String dataType;
        private final String dataName;

        DataSourceType(String type, String name) {
            this.dataType = type;
            this.dataName = name;
        }

        public String getDataStreamId(GoogleFitData gfd) {
            return SOURCE_TYPE + ":" + getDataType() + ":" + getProjectId(gfd) + ":" + getDataName();
        }

        public String getProjectId(GoogleFitData gfd) {
            return gfd.getProjectId();
        }

        public String getDataType() {
            return dataType;
        }

        public String getDataName() {
            return dataName;
        }
    }

    private static class DataTypeField {

        static final String NAME = "name";
        static final String FORMAT = "format";
        private Pair<String, String> nameValue = null;
        private Pair<String, String> formatSourceValue = null;
        private String formatDataPointValue = null;
        private String column = null;
        public DataTypeField(String name, Pair<String, String> format, String dbColumn) {
            this.setNameValue(Pair.create(NAME, name));
            this.setFormatSourceValue(Pair.create(FORMAT, format.first));
            this.setFormatDataPointValue(format.second);
            this.setColumn(dbColumn);
        }

        public Pair<String, String> getNameValue() {
            return nameValue;
        }

        public void setNameValue(Pair<String, String> value) {
            this.nameValue = value;
        }

        public Pair<String, String> getFormatSourceValue() {
            return formatSourceValue;
        }

        public void setFormatSourceValue(Pair<String, String> value) {
            this.formatSourceValue = value;
        }

        public String getFormatDataPointValue() {
            return formatDataPointValue;
        }

        public void setFormatDataPointValue(String value) {
            this.formatDataPointValue = value;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String name) {
            this.column = name;
        }
    }
}
