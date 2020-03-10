package fr.gaulupeau.apps.Poche.data;

import android.database.sqlite.SQLiteDatabase;

import androidx.core.util.Consumer;

import fr.gaulupeau.apps.Poche.data.dao.DaoSession;

public class DbUtils {

    public interface Callable<T> {
        T call(DaoSession daoSession);
    }

    public static void runInNonExclusiveTx(DaoSession session, Consumer<DaoSession> runnable) {
        SQLiteDatabase db = (SQLiteDatabase) session.getDatabase().getRawDatabase();

        db.beginTransactionNonExclusive();
        try {
            runnable.accept(session);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static <T> T callInNonExclusiveTx(DaoSession session, Callable<T> callable) {
        SQLiteDatabase db = (SQLiteDatabase) session.getDatabase().getRawDatabase();

        db.beginTransactionNonExclusive();
        try {
            T result = callable.call(session);

            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

}
