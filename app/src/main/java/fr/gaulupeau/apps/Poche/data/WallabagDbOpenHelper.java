package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationRangeDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleContentDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoMaster;
import fr.gaulupeau.apps.Poche.data.dao.FtsDao;
import fr.gaulupeau.apps.Poche.data.dao.QueueItemDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;

class WallabagDbOpenHelper extends DaoMaster.OpenHelper {

    private static final String TAG = WallabagDbOpenHelper.class.getSimpleName();

    private final Context context;

    WallabagDbOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
        this.context = context;
    }

    @Override
    public void onCreate(Database db) {
        Log.d(TAG, "onCreate() creating tables");

        super.onCreate(db);
        FtsDao.createAll(db, false);
    }

    @Override
    public void onUpgrade(Database db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading schema from version " + oldVersion + " to " + newVersion);

        boolean migrationDone = false;
        if (oldVersion >= 101 && newVersion <= 108) {
            try {
                if (oldVersion < 102) {
                    Log.i(TAG, "Migrating to version " + 102);

                    String[] columnsToAdd = new String[]{
                            "\"ORIGIN_URL\" TEXT",
                            "\"AUTHORS\" TEXT",
                            "\"PUBLISHED_AT\" INTEGER",
                            "\"STARRED_AT\" INTEGER",
                            "\"IS_PUBLIC\" INTEGER",
                            "\"PUBLIC_UID\" TEXT"
                    };
                    for (String col : columnsToAdd) {
                        db.execSQL("ALTER TABLE \"ARTICLE\" ADD COLUMN " + col + ";");
                    }
                }

                if (oldVersion < 103) {
                    Log.i(TAG, "Migrating to version " + 103);

                    db.execSQL("create index IDX_ARTICLE_TAGS_JOIN_ARTICLE_ID on " +
                            ArticleTagsJoinDao.TABLENAME +
                            " (" + ArticleTagsJoinDao.Properties.ArticleId.columnName + " asc);");
                    db.execSQL("create index IDX_ARTICLE_TAGS_JOIN_TAG_ID on " +
                            ArticleTagsJoinDao.TABLENAME +
                            " (" + ArticleTagsJoinDao.Properties.TagId.columnName + " asc);");
                }

                if (oldVersion < 104) {
                    Log.i(TAG, "Migrating to version " + 104);

                    ArticleContentDao.createTable(db, false);

                    db.execSQL("insert into " + ArticleContentDao.TABLENAME +
                            "(" + ArticleContentDao.Properties.Id.columnName +
                            ", " + ArticleContentDao.Properties.Content.columnName + ")" +
                            " select " + ArticleDao.Properties.Id.columnName + ", CONTENT" +
                            " from " + ArticleDao.TABLENAME);

                    // SQLite can't drop columns; just removing the data
                    db.execSQL("update " + ArticleDao.TABLENAME + " set CONTENT = null;");
                }

                if (oldVersion < 105) {
                    Log.i(TAG, "Migrating to version " + 105);

                    FtsDao.createAll(db, false);

                    db.execSQL("insert into " + FtsDao.TABLE_NAME +
                            "(" + FtsDao.COLUMN_ID +
                            ", " + FtsDao.COLUMN_TITLE +
                            ", " + FtsDao.COLUMN_CONTENT + ")" +
                            " select rowid, " + ArticleDao.Properties.Title.columnName +
                            ", " + ArticleContentDao.Properties.Content.columnName +
                            " from " + FtsDao.VIEW_FOR_FTS_NAME);
                }

                if (oldVersion < 106) {
                    Log.i(TAG, "Migration to version " + 106);

                    AnnotationDao.createTable(db, false);
                    AnnotationRangeDao.createTable(db, false);
                }

                if (oldVersion < 107) {
                    Log.i(TAG, "Migration to version " + 107);

                    db.execSQL("alter table QUEUE_ITEM add column EXTRA2 TEXT;");
                }

                if (oldVersion < 108) {
                    Log.i(TAG, "Migration to version " + 108);

                    db.execSQL("alter table ARTICLE add column GIVEN_URL TEXT;");

                    db.execSQL("alter table QUEUE_ITEM add column LOCAL_ARTICLE_ID INTEGER;");
                }

                migrationDone = true;
            } catch (Exception e) {
                Log.e(TAG, "Migration error", e);
            }
        }

        if (!migrationDone) genericMigration(db, oldVersion, newVersion);
    }

    private void genericMigration(Database db, int oldVersion, int newVersion) {
        Log.i(TAG, "genericMigration() oldVersion=" + oldVersion + ", newVersion=" + newVersion);

        List<String> offlineUrls = null;
        if(oldVersion >= 2) {
            try (Cursor c = db.rawQuery(oldVersion == 2
                    ? "select url from offline_url order by _id"
                    : "select extra from QUEUE_ITEM where action = 1 order by _id", null)) {

                offlineUrls = new ArrayList<>();
                while (c.moveToNext()) {
                    if (!c.isNull(0)) offlineUrls.add(c.getString(0));
                }
            } catch (Exception e) {
                Log.w(TAG, "Exception while migrating from version " + oldVersion, e);
            }
        }

        FtsDao.dropAll(db, true);
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
