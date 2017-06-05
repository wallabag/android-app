package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;
import org.greenrobot.greendao.query.QueryBuilder;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.data.dao.DaoMaster;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.QueueItemDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;

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
            WallabagOpenHelper dbHelper = new WallabagOpenHelper(context, dbPath, null);
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

    private static class WallabagOpenHelper extends DaoMaster.OpenHelper {

        private static final String TAG = WallabagOpenHelper.class.getSimpleName();

        public WallabagOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
            super(context, name, factory);
        }

        @Override
        public void onUpgrade(Database db, int oldVersion, int newVersion) {
            Log.i(TAG, "Upgrading schema from version " + oldVersion + " to " + newVersion);

            List<String> offlineUrls = null;
            if(oldVersion >= 2) {
                Cursor c = null;
                try {
                    c = db.rawQuery(oldVersion == 2
                            ? "select url from offline_url order by _id"
                            : "select extra from QUEUE_ITEM where action = 1 order by _id", null);

                    offlineUrls = new ArrayList<>();
                    while(c.moveToNext()) {
                        if(!c.isNull(0)) offlineUrls.add(c.getString(0));
                    }
                } catch(Exception e) {
                    Log.w(TAG, "Exception while migrating from version " + oldVersion, e);
                } finally {
                    if(c != null) {
                        c.close();
                    }
                }
            }

            DaoMaster.dropAllTables(db, true);
            onCreate(db);

            Settings settings = new Settings(context);
            settings.setFirstSyncDone(false);
            settings.setLatestUpdatedItemTimestamp(0);
            settings.setLatestUpdateRunTimestamp(0);

            if(offlineUrls != null && !offlineUrls.isEmpty()) {
                boolean inserted = false;

                db.beginTransaction();
                try {
                    DatabaseStatement stmt = db.compileStatement(
                            "insert into " + QueueItemDao.TABLENAME + "("
                                    + QueueItemDao.Properties.Id.columnName + ", "
                                    + QueueItemDao.Properties.QueueNumber.columnName + ", "
                                    + QueueItemDao.Properties.Action.columnName + ", "
                                    + QueueItemDao.Properties.Extra.columnName
                                    + ") values(?, ?, ?, ?)");

                    int i = 1;
                    for(String url: offlineUrls) {
                        try {
                            stmt.bindLong(1, i);
                            stmt.bindLong(2, i);
                            stmt.bindLong(3, QueueItem.Action.ADD_LINK.getId());
                            stmt.bindString(4, url);

                            stmt.executeInsert();

                            inserted = true;
                        } catch(Exception e) {
                            Log.w(TAG, "Exception while inserting an offline url: " + url, e);
                        }

                        i++;
                    }

                    db.setTransactionSuccessful();
                } catch(Exception e) {
                    Log.w(TAG, "Exception while inserting offline urls", e);
                } finally {
                    db.endTransaction();
                }

                if(inserted) EventBus.getDefault().post(new OfflineQueueChangedEvent(null));
            }
        }

    }

}
