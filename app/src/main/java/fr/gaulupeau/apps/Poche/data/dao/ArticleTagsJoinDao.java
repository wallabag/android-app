package fr.gaulupeau.apps.Poche.data.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;

// This code was generated, edit with caution
/** 
 * DAO for table "ARTICLE_TAGS_JOIN".
*/
public class ArticleTagsJoinDao extends AbstractDao<ArticleTagsJoin, Long> {

    public static final String TABLENAME = "ARTICLE_TAGS_JOIN";

    /**
     * Properties of entity ArticleTagsJoin.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property ArticleId = new Property(1, Long.class, "articleId", false, "ARTICLE_ID");
        public final static Property TagId = new Property(2, Long.class, "tagId", false, "TAG_ID");
    }


    public ArticleTagsJoinDao(DaoConfig config) {
        super(config);
    }
    
    public ArticleTagsJoinDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"ARTICLE_TAGS_JOIN\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"ARTICLE_ID\" INTEGER," + // 1: articleId
                "\"TAG_ID\" INTEGER);"); // 2: tagId
        // Add Indexes
        db.execSQL("CREATE INDEX " + constraint + "IDX_ARTICLE_TAGS_JOIN_ARTICLE_ID ON \"ARTICLE_TAGS_JOIN\"" +
                " (\"ARTICLE_ID\" ASC);");
        db.execSQL("CREATE INDEX " + constraint + "IDX_ARTICLE_TAGS_JOIN_TAG_ID ON \"ARTICLE_TAGS_JOIN\"" +
                " (\"TAG_ID\" ASC);");
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"ARTICLE_TAGS_JOIN\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, ArticleTagsJoin entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Long articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(2, articleId);
        }
 
        Long tagId = entity.getTagId();
        if (tagId != null) {
            stmt.bindLong(3, tagId);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, ArticleTagsJoin entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Long articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(2, articleId);
        }
 
        Long tagId = entity.getTagId();
        if (tagId != null) {
            stmt.bindLong(3, tagId);
        }
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public ArticleTagsJoin readEntity(Cursor cursor, int offset) {
        ArticleTagsJoin entity = new ArticleTagsJoin( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getLong(offset + 1), // articleId
            cursor.isNull(offset + 2) ? null : cursor.getLong(offset + 2) // tagId
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, ArticleTagsJoin entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setArticleId(cursor.isNull(offset + 1) ? null : cursor.getLong(offset + 1));
        entity.setTagId(cursor.isNull(offset + 2) ? null : cursor.getLong(offset + 2));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(ArticleTagsJoin entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(ArticleTagsJoin entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(ArticleTagsJoin entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}
