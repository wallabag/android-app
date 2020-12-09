package fr.gaulupeau.apps.Poche.data;

import android.util.Log;

import org.greenrobot.greendao.query.QueryBuilder;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.dao.DaoMaster;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;

public class DbConnection {

    private static final String TAG = DbConnection.class.getSimpleName();

    public static DaoSession getSession() {
        if(Holder.session == null) {
            // enable some debugging
            if(BuildConfig.DEBUG) {
                QueryBuilder.LOG_SQL = true;
                QueryBuilder.LOG_VALUES = true;
            }

            String dbPath = App.getSettings().getDbPathForDbHelper();

            Log.d(TAG, "creating new db session");
            WallabagDbOpenHelper dbHelper = new WallabagDbOpenHelper(App.getInstance(), dbPath, null);
            dbHelper.setWriteAheadLoggingEnabled(true);
            DaoMaster daoMaster = new DaoMaster(dbHelper.getWritableDb());
            Holder.session = daoMaster.newSession();
        } else {
            Log.d(TAG, "using existing db session");
        }

        return Holder.session;
    }

    public static void resetSession() {
        Holder.session = null;
    }

    private static class Holder {
        private static DaoSession session = getSession();
    }

}
