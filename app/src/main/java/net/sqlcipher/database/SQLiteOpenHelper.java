package net.sqlcipher.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

/**
 * Stub for https://github.com/greenrobot/greenDAO/issues/428.
 * Remove after https://github.com/greenrobot/greenDAO/pull/924 is released.
 */
public abstract class SQLiteOpenHelper {

    public SQLiteOpenHelper(Context context, String name, CursorFactory factory, int version) {}

    public abstract void onCreate(SQLiteDatabase db);

    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    public void onOpen(SQLiteDatabase db) {}

    public SQLiteDatabase getReadableDatabase(String password) {
        return null;
    }

    public SQLiteDatabase getReadableDatabase(char[] password) {
        return null;
    }

    public SQLiteDatabase getWritableDatabase(String password) {
        return null;
    }

    public SQLiteDatabase getWritableDatabase(char[] password) {
        return null;
    }

}
