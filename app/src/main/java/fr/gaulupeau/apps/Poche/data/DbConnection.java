package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.data.dao.DaoMaster;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;

public class DbConnection {

    private static final String TAG = DbConnection.class.getSimpleName();

    private static Context context;

    public static DaoSession getSession() {
        if(Holder.session == null) {
            // enable some debugging
            if(BuildConfig.DEBUG) {
                QueryBuilder.LOG_SQL = true;
                QueryBuilder.LOG_VALUES = true;
            }

            String dbPath = new Settings(context).getDbPathForDbHelper();

            Log.d(TAG, "creating new db session");
            WallabagDbOpenHelper dbHelper = new WallabagDbOpenHelper(context, dbPath, null);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                dbHelper.setWriteAheadLoggingEnabled(true);
            }
            Database db = dbHelper.getWritableDb();
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                if(!((SQLiteDatabase)db.getRawDatabase()).enableWriteAheadLogging()) {
                    Log.w(TAG, "write ahead logging was not enabled");
                }
            }
            DaoMaster daoMaster = new DaoMaster(db);
            Holder.session = daoMaster.newSession();
        } else {
            Log.d(TAG, "using existing db session");
        }

        return Holder.session;
    }

    public static void resetSession() {
        Holder.session = null;
    }

    public static void setContext(Context context) {
        DbConnection.context = context;
    }

    private static class Holder {
        private static DaoSession session = getSession();
    }

}
