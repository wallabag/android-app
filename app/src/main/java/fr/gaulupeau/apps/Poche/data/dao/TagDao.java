package fr.gaulupeau.apps.Poche.data.dao;

import java.util.List;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;
import org.greenrobot.greendao.query.Query;
import org.greenrobot.greendao.query.QueryBuilder;

import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;

import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

// This code was generated, edit with caution
/** 
 * DAO for table "TAG".
*/
public class TagDao extends AbstractDao<Tag, Long> {

    public static final String TABLENAME = "TAG";

    /**
     * Properties of entity Tag.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property TagId = new Property(1, Integer.class, "tagId", false, "TAG_ID");
        public final static Property Label = new Property(2, String.class, "label", false, "LABEL");
    }

    private Query<Tag> article_TagsQuery;

    public TagDao(DaoConfig config) {
        super(config);
    }
    
    public TagDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"TAG\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"TAG_ID\" INTEGER UNIQUE ," + // 1: tagId
                "\"LABEL\" TEXT);"); // 2: label
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"TAG\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, Tag entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Integer tagId = entity.getTagId();
        if (tagId != null) {
            stmt.bindLong(2, tagId);
        }
 
        String label = entity.getLabel();
        if (label != null) {
            stmt.bindString(3, label);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, Tag entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Integer tagId = entity.getTagId();
        if (tagId != null) {
            stmt.bindLong(2, tagId);
        }
 
        String label = entity.getLabel();
        if (label != null) {
            stmt.bindString(3, label);
        }
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public Tag readEntity(Cursor cursor, int offset) {
        Tag entity = new Tag( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getInt(offset + 1), // tagId
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2) // label
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, Tag entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setTagId(cursor.isNull(offset + 1) ? null : cursor.getInt(offset + 1));
        entity.setLabel(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(Tag entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(Tag entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(Tag entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
    /** Internal query to resolve the "tags" to-many relationship of Article. */
    public List<Tag> _queryArticle_Tags(Long articleId) {
        synchronized (this) {
            if (article_TagsQuery == null) {
                QueryBuilder<Tag> queryBuilder = queryBuilder();
                queryBuilder.join(ArticleTagsJoin.class, ArticleTagsJoinDao.Properties.TagId)
                    .where(ArticleTagsJoinDao.Properties.ArticleId.eq(articleId));
                article_TagsQuery = queryBuilder.build();
            }
        }
        Query<Tag> query = article_TagsQuery.forCurrentThread();
        query.setParameter(0, articleId);
        return query.list();
    }

}
