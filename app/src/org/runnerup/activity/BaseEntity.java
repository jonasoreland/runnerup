package org.runnerup.activity;

import android.content.ContentValues;

import org.runnerup.db.Column;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

/**
 * Created by itdog on 21.01.15.
 */
public abstract class BaseEntity {
    Class<? extends BaseEntity> clazz;

    public BaseEntity() {
        clazz = this.getClass();
    }

    public BaseEntity(ContentValues cv) {
        super();
        processContentValues(cv, true);

    }

    public ContentValues map() {
        ContentValues result = new ContentValues();
        processContentValues(result, false);
        return result;
    }

    private void processContentValues(ContentValues contentValues, boolean inOut) {
        try {
            for (Field field : clazz.getDeclaredFields()) {
                Column fieldEntityAnnotation = field.getAnnotation(Column.class);
                if (fieldEntityAnnotation != null) {
                    if (!fieldEntityAnnotation.isAutoincrement()) {
                        if (!field.isAccessible())
                            field.setAccessible(true); // for private variables
                        Object fieldValue = field.get(this);
                        String key = field.getAnnotation(Column.class).name();
                        if (inOut) {
                            field.set(this, contentValues.get(key));
                        } else {
                            if (fieldValue instanceof Long) {
                                contentValues.put(key, Long.valueOf(fieldValue.toString()));
                            } else if (fieldValue instanceof String) {
                                contentValues.put(key, fieldValue.toString());
                            } else if (fieldValue instanceof Integer) {
                                contentValues.put(key, Integer.valueOf(fieldValue.toString()));
                            } else if (fieldValue instanceof Float) {
                                contentValues.put(key, Float.valueOf(fieldValue.toString()));
                            } else if (fieldValue instanceof Byte) {
                                contentValues.put(key, Byte.valueOf(fieldValue.toString()));
                            } else if (fieldValue instanceof Short) {
                                contentValues.put(key, Short.valueOf(fieldValue.toString()));
                            } else if (fieldValue instanceof Boolean) {
                                contentValues.put(key, Boolean.parseBoolean(fieldValue.toString()));
                            } else if (fieldValue instanceof Double) {
                                contentValues.put(key, Double.valueOf(fieldValue.toString()));
                            } else if (fieldValue instanceof Byte[] || fieldValue instanceof byte[]) {
                                try {
                                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(
                                            outputStream);
                                    objectOutputStream.writeObject(fieldValue);
                                    contentValues.put(key, outputStream.toByteArray());
                                    objectOutputStream.flush();
                                    objectOutputStream.close();
                                    outputStream.flush();
                                    outputStream.close();
                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                }
            }

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}