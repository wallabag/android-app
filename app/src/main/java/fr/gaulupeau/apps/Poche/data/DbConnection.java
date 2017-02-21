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

            Log.d(TAG, "creating new db session");
            WallabagOpenHelper dbHelper = new WallabagOpenHelper(context, "wallabag", null);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                dbHelper.setWriteAheadLoggingEnabled(true);
            }
            Database db = dbHelper.getWritableDb();
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

    private static class WallabagOpenHelper extends DaoMaster.OpenHelper {

        private static final String TAG = WallabagOpenHelper.class.getSimpleName();

        public WallabagOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
            super(context, name, factory);
        }

        @Override
        public void onUpgrade(Database db, int oldVersion, int newVersion) {
            Log.i(TAG, "Upgrading schema from version " + oldVersion + " to " + newVersion);

            boolean recreateTables = true;

            List<String> offlineUrls = null;
            if(oldVersion == 2) {
                Cursor c = null;
                try {
                    c = db.rawQuery("select url from offline_url order by _id", null);

                    offlineUrls = new ArrayList<>();
                    while(c.moveToNext()) {
                        if(!c.isNull(0)) offlineUrls.add(c.getString(0));
                    }
                } catch(Exception e) {
                    Log.w(TAG, "Exception while migrating from version 2", e);
                } finally {
                    if(c != null) {
                        c.close();
                    }
                }
            } else if(oldVersion == 5) {
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE ARTICLE ADD COLUMN images_downloaded INTEGER DEFAULT 0");

                    db.setTransactionSuccessful();

                    recreateTables = false;
                } catch(Exception e) {
                    Log.w(TAG, "Exception while altering table ARTICLE", e);
                } finally {
                    db.endTransaction();
                }
            }

            if(!recreateTables) return;

            DaoMaster.dropAllTables(db, true);
            onCreate(db);

            new Settings(context).setFirstSyncDone(false);

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
