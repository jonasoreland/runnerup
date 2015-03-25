package org.runnerup.db.entities;


import android.database.sqlite.SQLiteDatabase;

public interface IObjectValues {

    public long insert(SQLiteDatabase db);

    public void update(SQLiteDatabase db);

}
