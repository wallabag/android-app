package fr.gaulupeau.apps.Poche.data.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem.Action;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem.ActionConverter;

import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;

// This code was generated, edit with caution
/** 
 * DAO for table "QUEUE_ITEM".
*/
public class QueueItemDao extends AbstractDao<QueueItem, Long> {

    public static final String TABLENAME = "QUEUE_ITEM";

    /**
     * Properties of entity QueueItem.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property QueueNumber = new Property(1, Long.class, "queueNumber", false, "QUEUE_NUMBER");
        public final static Property Action = new Property(2, Integer.class, "action", false, "ACTION");
        public final static Property ArticleId = new Property(3, Integer.class, "articleId", false, "ARTICLE_ID");
        public final static Property LocalArticleId = new Property(4, Long.class, "localArticleId", false, "LOCAL_ARTICLE_ID");
        public final static Property Extra = new Property(5, String.class, "extra", false, "EXTRA");
        public final static Property Extra2 = new Property(6, String.class, "extra2", false, "EXTRA2");
    }

    private final ActionConverter actionConverter = new ActionConverter();

    public QueueItemDao(DaoConfig config) {
        super(config);
    }
    
    public QueueItemDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"QUEUE_ITEM\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"QUEUE_NUMBER\" INTEGER," + // 1: queueNumber
                "\"ACTION\" INTEGER," + // 2: action
                "\"ARTICLE_ID\" INTEGER," + // 3: articleId
                "\"LOCAL_ARTICLE_ID\" INTEGER," + // 4: localArticleId
                "\"EXTRA\" TEXT," + // 5: extra
                "\"EXTRA2\" TEXT);"); // 6: extra2
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"QUEUE_ITEM\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, QueueItem entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Long queueNumber = entity.getQueueNumber();
        if (queueNumber != null) {
            stmt.bindLong(2, queueNumber);
        }
 
        Action action = entity.getAction();
        if (action != null) {
            stmt.bindLong(3, actionConverter.convertToDatabaseValue(action));
        }
 
        Integer articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(4, articleId);
        }
 
        Long localArticleId = entity.getLocalArticleId();
        if (localArticleId != null) {
            stmt.bindLong(5, localArticleId);
        }
 
        String extra = entity.getExtra();
        if (extra != null) {
            stmt.bindString(6, extra);
        }
 
        String extra2 = entity.getExtra2();
        if (extra2 != null) {
            stmt.bindString(7, extra2);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, QueueItem entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Long queueNumber = entity.getQueueNumber();
        if (queueNumber != null) {
            stmt.bindLong(2, queueNumber);
        }
 
        Action action = entity.getAction();
        if (action != null) {
            stmt.bindLong(3, actionConverter.convertToDatabaseValue(action));
        }
 
        Integer articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(4, articleId);
        }
 
        Long localArticleId = entity.getLocalArticleId();
        if (localArticleId != null) {
            stmt.bindLong(5, localArticleId);
        }
 
        String extra = entity.getExtra();
        if (extra != null) {
            stmt.bindString(6, extra);
        }
 
        String extra2 = entity.getExtra2();
        if (extra2 != null) {
            stmt.bindString(7, extra2);
        }
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public QueueItem readEntity(Cursor cursor, int offset) {
        QueueItem entity = new QueueItem( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getLong(offset + 1), // queueNumber
            cursor.isNull(offset + 2) ? null : actionConverter.convertToEntityProperty(cursor.getInt(offset + 2)), // action
            cursor.isNull(offset + 3) ? null : cursor.getInt(offset + 3), // articleId
            cursor.isNull(offset + 4) ? null : cursor.getLong(offset + 4), // localArticleId
            cursor.isNull(offset + 5) ? null : cursor.getString(offset + 5), // extra
            cursor.isNull(offset + 6) ? null : cursor.getString(offset + 6) // extra2
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, QueueItem entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setQueueNumber(cursor.isNull(offset + 1) ? null : cursor.getLong(offset + 1));
        entity.setAction(cursor.isNull(offset + 2) ? null : actionConverter.convertToEntityProperty(cursor.getInt(offset + 2)));
        entity.setArticleId(cursor.isNull(offset + 3) ? null : cursor.getInt(offset + 3));
        entity.setLocalArticleId(cursor.isNull(offset + 4) ? null : cursor.getLong(offset + 4));
        entity.setExtra(cursor.isNull(offset + 5) ? null : cursor.getString(offset + 5));
        entity.setExtra2(cursor.isNull(offset + 6) ? null : cursor.getString(offset + 6));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(QueueItem entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(QueueItem entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(QueueItem entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}
