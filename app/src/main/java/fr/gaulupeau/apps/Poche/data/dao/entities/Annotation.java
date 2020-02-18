package fr.gaulupeau.apps.Poche.data.dao.entities;

import android.database.Cursor;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.ToMany;
import org.greenrobot.greendao.annotation.Unique;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.query.QueryBuilder;

import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationRangeDao;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;

@Entity
public class Annotation {

    @Id
    private Long id;

    @Unique
    private Integer annotationId;

    @Index
    private Long articleId;

    private String text;
    private String quote;

    private Date createdAt;
    private Date updatedAt;

    private String annotatorSchemaVersion;

    @ToMany(referencedJoinProperty = "annotationId")
    private List<AnnotationRange> ranges;

    /** Used to resolve relations */
    @Generated(hash = 2040040024)
    private transient DaoSession daoSession;

    /** Used for active entity operations. */
    @Generated(hash = 27795980)
    private transient AnnotationDao myDao;

    @Generated(hash = 1426594540)
    public Annotation() {}

    @Generated(hash = 775975383)
    public Annotation(Long id, Integer annotationId, Long articleId, String text, String quote,
            Date createdAt, Date updatedAt, String annotatorSchemaVersion) {
        this.id = id;
        this.annotationId = annotationId;
        this.articleId = articleId;
        this.text = text;
        this.quote = quote;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.annotatorSchemaVersion = annotatorSchemaVersion;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getAnnotationId() {
        return this.annotationId;
    }

    public void setAnnotationId(Integer annotationId) {
        this.annotationId = annotationId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getQuote() {
        return this.quote;
    }

    public void setQuote(String quote) {
        this.quote = quote;
    }

    public Date getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAnnotatorSchemaVersion() {
        return this.annotatorSchemaVersion;
    }

    public void setAnnotatorSchemaVersion(String annotatorSchemaVersion) {
        this.annotatorSchemaVersion = annotatorSchemaVersion;
    }

    /**
     * To-many relationship, resolved on first access (and after reset).
     * Changes to to-many relations are not persisted, make changes to the target entity.
     */
    @Generated(hash = 1813762896)
    public List<AnnotationRange> getRanges() {
        if (ranges == null) {
            final DaoSession daoSession = this.daoSession;
            if (daoSession == null) {
                throw new DaoException("Entity is detached from DAO context");
            }
            AnnotationRangeDao targetDao = daoSession.getAnnotationRangeDao();
            List<AnnotationRange> rangesNew = targetDao._queryAnnotation_Ranges(id);
            synchronized (this) {
                if (ranges == null) {
                    ranges = rangesNew;
                }
            }
        }
        return ranges;
    }

    public void setRanges(List<AnnotationRange> ranges) {
        this.ranges = ranges;
    }

    /** Resets a to-many relationship, making the next get call to query for a fresh result. */
    @Generated(hash = 1076639632)
    public synchronized void resetRanges() {
        ranges = null;
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#delete(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 128553479)
    public void delete() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.delete(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#refresh(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 1942392019)
    public void refresh() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.refresh(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#update(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 713229351)
    public void update() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.update(this);
    }

    /** called by internal mechanisms, do not call yourself. */
    @Generated(hash = 40794177)
    public void __setDaoSession(DaoSession daoSession) {
        this.daoSession = daoSession;
        myDao = daoSession != null ? daoSession.getAnnotationDao() : null;
    }

    @Override
    public String toString() {
        return "Annotation{" +
                "id=" + id +
                ", annotationId=" + annotationId +
                ", articleId=" + articleId +
                ", text='" + text + '\'' +
                ", quote='" + quote + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", annotatorSchemaVersion='" + annotatorSchemaVersion + '\'' +
                '}';
    }

    public static Collection<Long> getAnnotationIdsByArticleIds(
            Collection<Long> articleIds, AnnotationDao dao) {
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = getAnnotationByArticlesQueryBuilder(articleIds, dao).buildCursor().query()) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(cursor.getColumnIndex(AnnotationDao.Properties.Id.columnName)));
            }
        }
        return ids;
    }

    public static QueryBuilder<Annotation> getAnnotationByArticlesQueryBuilder(
            Collection<Long> articleIds, AnnotationDao dao) {
        return dao.queryBuilder().where(AnnotationDao.Properties.ArticleId.in(articleIds));
    }

}
