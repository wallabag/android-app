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

import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;

// This code was generated, edit with caution
/** 
 * DAO for table "ANNOTATION_RANGE".
*/
public class AnnotationRangeDao extends AbstractDao<AnnotationRange, Long> {

    public static final String TABLENAME = "ANNOTATION_RANGE";

    /**
     * Properties of entity AnnotationRange.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property AnnotationId = new Property(1, Long.class, "annotationId", false, "ANNOTATION_ID");
        public final static Property Start = new Property(2, String.class, "start", false, "START");
        public final static Property End = new Property(3, String.class, "end", false, "END");
        public final static Property StartOffset = new Property(4, long.class, "startOffset", false, "START_OFFSET");
        public final static Property EndOffset = new Property(5, long.class, "endOffset", false, "END_OFFSET");
    }

    private Query<AnnotationRange> annotation_RangesQuery;

    public AnnotationRangeDao(DaoConfig config) {
        super(config);
    }
    
    public AnnotationRangeDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"ANNOTATION_RANGE\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"ANNOTATION_ID\" INTEGER," + // 1: annotationId
                "\"START\" TEXT," + // 2: start
                "\"END\" TEXT," + // 3: end
                "\"START_OFFSET\" INTEGER NOT NULL ," + // 4: startOffset
                "\"END_OFFSET\" INTEGER NOT NULL );"); // 5: endOffset
        // Add Indexes
        db.execSQL("CREATE INDEX " + constraint + "IDX_ANNOTATION_RANGE_ANNOTATION_ID ON \"ANNOTATION_RANGE\"" +
                " (\"ANNOTATION_ID\" ASC);");
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"ANNOTATION_RANGE\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, AnnotationRange entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Long annotationId = entity.getAnnotationId();
        if (annotationId != null) {
            stmt.bindLong(2, annotationId);
        }
 
        String start = entity.getStart();
        if (start != null) {
            stmt.bindString(3, start);
        }
 
        String end = entity.getEnd();
        if (end != null) {
            stmt.bindString(4, end);
        }
        stmt.bindLong(5, entity.getStartOffset());
        stmt.bindLong(6, entity.getEndOffset());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, AnnotationRange entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Long annotationId = entity.getAnnotationId();
        if (annotationId != null) {
            stmt.bindLong(2, annotationId);
        }
 
        String start = entity.getStart();
        if (start != null) {
            stmt.bindString(3, start);
        }
 
        String end = entity.getEnd();
        if (end != null) {
            stmt.bindString(4, end);
        }
        stmt.bindLong(5, entity.getStartOffset());
        stmt.bindLong(6, entity.getEndOffset());
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public AnnotationRange readEntity(Cursor cursor, int offset) {
        AnnotationRange entity = new AnnotationRange( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getLong(offset + 1), // annotationId
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2), // start
            cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3), // end
            cursor.getLong(offset + 4), // startOffset
            cursor.getLong(offset + 5) // endOffset
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, AnnotationRange entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setAnnotationId(cursor.isNull(offset + 1) ? null : cursor.getLong(offset + 1));
        entity.setStart(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setEnd(cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3));
        entity.setStartOffset(cursor.getLong(offset + 4));
        entity.setEndOffset(cursor.getLong(offset + 5));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(AnnotationRange entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(AnnotationRange entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(AnnotationRange entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
    /** Internal query to resolve the "ranges" to-many relationship of Annotation. */
    public List<AnnotationRange> _queryAnnotation_Ranges(Long annotationId) {
        synchronized (this) {
            if (annotation_RangesQuery == null) {
                QueryBuilder<AnnotationRange> queryBuilder = queryBuilder();
                queryBuilder.where(Properties.AnnotationId.eq(null));
                annotation_RangesQuery = queryBuilder.build();
            }
        }
        Query<AnnotationRange> query = annotation_RangesQuery.forCurrentThread();
        query.setParameter(0, annotationId);
        return query.list();
    }

}
