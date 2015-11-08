package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import de.greenrobot.dao.query.QueryBuilder;
import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.entity.DaoMaster;
import fr.gaulupeau.apps.Poche.entity.DaoSession;

/**
 * @author Victor Häggqvist
 * @since 10/19/15
 */
public class DbConnection {

    private static final String TAG = DbConnection.class.getSimpleName();
    private static Context context;

    public static DaoSession getSession() {
        if (Holder.session == null) {
            // enable some debugging
            if (BuildConfig.DEBUG) {
                QueryBuilder.LOG_SQL = true;
                QueryBuilder.LOG_VALUES = true;
            }

            Log.d(TAG, "creating new db session");
            DaoMaster.DevOpenHelper dbHelper = new DaoMaster.DevOpenHelper(context, "wallabag", null);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            DaoMaster daoMaster = new DaoMaster(db);
            Holder.session = daoMaster.newSession();
        } else {
            Log.d(TAG, "using existing db session");
        }

        return Holder.session;
    }

    public static void setContext(Context context) {
        DbConnection.context = context;
    }

    private static class Holder {
        private static DaoSession session = getSession();
    }
}