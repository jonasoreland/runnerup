package org.runnerup.export.format;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import org.runnerup.common.util.Constants;
import org.runnerup.util.JsonWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by LFAJER on 2015-02-03.
 */
public class GoogleFitData {

    SQLiteDatabase mDB = null;
    static Context context = null;

    private static final Map<Integer, String> activityType;
    static {
        Map<Integer, String> aMap = new HashMap<Integer, String>();
        aMap.put(Constants.DB.ACTIVITY.SPORT_RUNNING, "8");
        aMap.put(Constants.DB.ACTIVITY.SPORT_BIKING, "1");
        activityType = Collections.unmodifiableMap(aMap);
    }

    private static class DataTypeField {

        public DataTypeField(String name, String format) {
            this.nameValue = Pair.create(NAME, name);
            this.formatValue = Pair.create(FORMAT, format);
        }

        static final String NAME = "name";
        static final String FORMAT = "format";
        Pair<String, String> nameValue = null;
        Pair<String, String> formatValue = null;

    }

    private static final Map<DataSourceType, List<DataTypeField>> dataTypeFields;
    static {
        Map<DataSourceType, List<DataTypeField>> fieldsMap = new HashMap<DataSourceType, List<DataTypeField>>();

        List<DataTypeField> fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("activity", "integer"));
        fieldsMap.put(DataSourceType.ACTIVITY_SEGMENT, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("bpm", "float"));
        fieldsMap.put(DataSourceType.ACTIVITY_HEARTRATE, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("latitude", "bpm"));
        fields.add(new DataTypeField("longitude", "float"));
        fields.add(new DataTypeField("accuracy", "float"));
        fields.add(new DataTypeField("altitude", "float"));
        fieldsMap.put(DataSourceType.ACTIVITY_LOCATION, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("speed", "float"));
        fieldsMap.put(DataSourceType.ACTIVITY_SPEED, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("activity", "integer"));
        fields.add(new DataTypeField("duration", "integer"));
        fields.add(new DataTypeField("num_segments", "integer"));
        fieldsMap.put(DataSourceType.ACTIVITY_SUMMARY, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("average", "float"));
        fields.add(new DataTypeField("max", "float"));
        fields.add(new DataTypeField("min", "float"));
        fieldsMap.put(DataSourceType.HEARTRATE_SUMMARY, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("low_latitude", "float"));
        fields.add(new DataTypeField("high_latitude", "float"));
        fields.add(new DataTypeField("low_longitude", "float"));
        fields.add(new DataTypeField("high_longitude", "float"));
        fieldsMap.put(DataSourceType.LOCATION_SUMMARY, fields);

        fields = new ArrayList<DataTypeField>();
        fields.add(new DataTypeField("average", "float"));
        fields.add(new DataTypeField("max", "float"));
        fields.add(new DataTypeField("min", "float"));
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
            String packageName =  context.getPackageName();
            StringBuilder streamId = new StringBuilder();
            streamId.append(sourceType).append(":").append(dataType).append(":").append(packageName).append(":").append(dataName);
            return streamId.toString();
        }

    }

    public GoogleFitData(final SQLiteDatabase db, Context ctx) {
        this.mDB = db;
        this.context = ctx;
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

    private void fillFieldArray(DataSourceType type, JsonWriter w) throws IOException {
        for(DataTypeField field : dataTypeFields.get(type)) {
            w.beginObject();
            w.name(field.nameValue.first).value(field.nameValue.second);
            w.name(field.formatValue.first).value(field.formatValue.second);
            w.endObject();
        }
    }

    private void addApplicationObject(JsonWriter w) throws IOException {
        w.beginObject();
        w.name("packageName");
        w.value(context.getPackageName());
        w.endObject();
    }

    public List<DataSourceType> getActivityDataSourceTypes(long activityId) {
        List<DataSourceType> neededSources = new ArrayList<DataSourceType>();

        String[] pColumns = {
                Constants.DB.LOCATION.TIME, Constants.DB.LOCATION.HR, Constants.DB.LOCATION.SPEED
        };
        Cursor cursor = mDB.query(Constants.DB.LOCATION.TABLE, pColumns,
                Constants.DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + pColumns[1] + " IS NOT NULL", null, null, null,
                null);

        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_HEARTRATE);
            neededSources.add(DataSourceType.HEARTRATE_SUMMARY);
        }

        cursor = mDB.query(Constants.DB.LOCATION.TABLE, pColumns,
                Constants.DB.LOCATION.ACTIVITY + " = " + activityId + " AND " + pColumns[2] + " IS NOT NULL", null, null, null,
                null);

        if (cursor.getCount() > 0) {
            neededSources.add(DataSourceType.ACTIVITY_SPEED);
            neededSources.add(DataSourceType.SPEED_SUMMARY);
        }

        neededSources.add(DataSourceType.ACTIVITY_SEGMENT);
        neededSources.add(DataSourceType.ACTIVITY_SUMMARY);
        neededSources.add(DataSourceType.ACTIVITY_LOCATION);
        neededSources.add(DataSourceType.LOCATION_SUMMARY);
        return neededSources;
    }

    public void exportDataPoints(long activityId, String dataType) {

    }

    public void exportSession() {

    }
}

