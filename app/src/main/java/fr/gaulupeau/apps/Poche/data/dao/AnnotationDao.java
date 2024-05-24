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

import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;

// This code was generated, edit with caution
/** 
 * DAO for table "ANNOTATION".
*/
public class AnnotationDao extends AbstractDao<Annotation, Long> {

    public static final String TABLENAME = "ANNOTATION";

    /**
     * Properties of entity Annotation.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property AnnotationId = new Property(1, Integer.class, "annotationId", false, "ANNOTATION_ID");
        public final static Property ArticleId = new Property(2, Long.class, "articleId", false, "ARTICLE_ID");
        public final static Property Text = new Property(3, String.class, "text", false, "TEXT");
        public final static Property Quote = new Property(4, String.class, "quote", false, "QUOTE");
        public final static Property CreatedAt = new Property(5, java.util.Date.class, "createdAt", false, "CREATED_AT");
        public final static Property UpdatedAt = new Property(6, java.util.Date.class, "updatedAt", false, "UPDATED_AT");
        public final static Property AnnotatorSchemaVersion = new Property(7, String.class, "annotatorSchemaVersion", false, "ANNOTATOR_SCHEMA_VERSION");
    }

    private DaoSession daoSession;

    private Query<Annotation> article_AnnotationsQuery;

    public AnnotationDao(DaoConfig config) {
        super(config);
    }
    
    public AnnotationDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
        this.daoSession = daoSession;
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"ANNOTATION\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"ANNOTATION_ID\" INTEGER UNIQUE ," + // 1: annotationId
                "\"ARTICLE_ID\" INTEGER," + // 2: articleId
                "\"TEXT\" TEXT," + // 3: text
                "\"QUOTE\" TEXT," + // 4: quote
                "\"CREATED_AT\" INTEGER," + // 5: createdAt
                "\"UPDATED_AT\" INTEGER," + // 6: updatedAt
                "\"ANNOTATOR_SCHEMA_VERSION\" TEXT);"); // 7: annotatorSchemaVersion
        // Add Indexes
        db.execSQL("CREATE INDEX " + constraint + "IDX_ANNOTATION_ARTICLE_ID ON \"ANNOTATION\"" +
                " (\"ARTICLE_ID\" ASC);");
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"ANNOTATION\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, Annotation entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Integer annotationId = entity.getAnnotationId();
        if (annotationId != null) {
            stmt.bindLong(2, annotationId);
        }
 
        Long articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(3, articleId);
        }
 
        String text = entity.getText();
        if (text != null) {
            stmt.bindString(4, text);
        }
 
        String quote = entity.getQuote();
        if (quote != null) {
            stmt.bindString(5, quote);
        }
 
        java.util.Date createdAt = entity.getCreatedAt();
        if (createdAt != null) {
            stmt.bindLong(6, createdAt.getTime());
        }
 
        java.util.Date updatedAt = entity.getUpdatedAt();
        if (updatedAt != null) {
            stmt.bindLong(7, updatedAt.getTime());
        }
 
        String annotatorSchemaVersion = entity.getAnnotatorSchemaVersion();
        if (annotatorSchemaVersion != null) {
            stmt.bindString(8, annotatorSchemaVersion);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, Annotation entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Integer annotationId = entity.getAnnotationId();
        if (annotationId != null) {
            stmt.bindLong(2, annotationId);
        }
 
        Long articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(3, articleId);
        }
 
        String text = entity.getText();
        if (text != null) {
            stmt.bindString(4, text);
        }
 
        String quote = entity.getQuote();
        if (quote != null) {
            stmt.bindString(5, quote);
        }
 
        java.util.Date createdAt = entity.getCreatedAt();
        if (createdAt != null) {
            stmt.bindLong(6, createdAt.getTime());
        }
 
        java.util.Date updatedAt = entity.getUpdatedAt();
        if (updatedAt != null) {
            stmt.bindLong(7, updatedAt.getTime());
        }
 
        String annotatorSchemaVersion = entity.getAnnotatorSchemaVersion();
        if (annotatorSchemaVersion != null) {
            stmt.bindString(8, annotatorSchemaVersion);
        }
    }

    @Override
    protected final void attachEntity(Annotation entity) {
        super.attachEntity(entity);
        entity.__setDaoSession(daoSession);
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public Annotation readEntity(Cursor cursor, int offset) {
        Annotation entity = new Annotation( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getInt(offset + 1), // annotationId
            cursor.isNull(offset + 2) ? null : cursor.getLong(offset + 2), // articleId
            cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3), // text
            cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4), // quote
            cursor.isNull(offset + 5) ? null : new java.util.Date(cursor.getLong(offset + 5)), // createdAt
            cursor.isNull(offset + 6) ? null : new java.util.Date(cursor.getLong(offset + 6)), // updatedAt
            cursor.isNull(offset + 7) ? null : cursor.getString(offset + 7) // annotatorSchemaVersion
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, Annotation entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setAnnotationId(cursor.isNull(offset + 1) ? null : cursor.getInt(offset + 1));
        entity.setArticleId(cursor.isNull(offset + 2) ? null : cursor.getLong(offset + 2));
        entity.setText(cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3));
        entity.setQuote(cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4));
        entity.setCreatedAt(cursor.isNull(offset + 5) ? null : new java.util.Date(cursor.getLong(offset + 5)));
        entity.setUpdatedAt(cursor.isNull(offset + 6) ? null : new java.util.Date(cursor.getLong(offset + 6)));
        entity.setAnnotatorSchemaVersion(cursor.isNull(offset + 7) ? null : cursor.getString(offset + 7));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(Annotation entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(Annotation entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(Annotation entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
    /** Internal query to resolve the "annotations" to-many relationship of Article. */
    public List<Annotation> _queryArticle_Annotations(Long articleId) {
        synchronized (this) {
            if (article_AnnotationsQuery == null) {
                QueryBuilder<Annotation> queryBuilder = queryBuilder();
                queryBuilder.where(Properties.ArticleId.eq(null));
                article_AnnotationsQuery = queryBuilder.build();
            }
        }
        Query<Annotation> query = article_AnnotationsQuery.forCurrentThread();
        query.setParameter(0, articleId);
        return query.list();
    }

}
