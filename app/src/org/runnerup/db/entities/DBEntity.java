package org.runnerup.db.entities;


import android.database.sqlite.SQLiteDatabase;

public interface DBEntity {

    long insert(SQLiteDatabase db);

    void update(SQLiteDatabase db);

}
