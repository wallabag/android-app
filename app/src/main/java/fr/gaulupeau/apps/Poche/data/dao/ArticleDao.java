package fr.gaulupeau.apps.Poche.data.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

// This code was generated, edit with caution
/** 
 * DAO for table "ARTICLE".
*/
public class ArticleDao extends AbstractDao<Article, Long> {

    public static final String TABLENAME = "ARTICLE";

    /**
     * Properties of entity Article.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property ArticleId = new Property(1, Integer.class, "articleId", false, "ARTICLE_ID");
        public final static Property Title = new Property(2, String.class, "title", false, "TITLE");
        public final static Property Domain = new Property(3, String.class, "domain", false, "DOMAIN");
        public final static Property Url = new Property(4, String.class, "url", false, "URL");
        public final static Property GivenUrl = new Property(5, String.class, "givenUrl", false, "GIVEN_URL");
        public final static Property OriginUrl = new Property(6, String.class, "originUrl", false, "ORIGIN_URL");
        public final static Property EstimatedReadingTime = new Property(7, int.class, "estimatedReadingTime", false, "ESTIMATED_READING_TIME");
        public final static Property Language = new Property(8, String.class, "language", false, "LANGUAGE");
        public final static Property PreviewPictureURL = new Property(9, String.class, "previewPictureURL", false, "PREVIEW_PICTURE_URL");
        public final static Property Authors = new Property(10, String.class, "authors", false, "AUTHORS");
        public final static Property Favorite = new Property(11, Boolean.class, "favorite", false, "FAVORITE");
        public final static Property Archive = new Property(12, Boolean.class, "archive", false, "ARCHIVE");
        public final static Property CreationDate = new Property(13, java.util.Date.class, "creationDate", false, "CREATION_DATE");
        public final static Property UpdateDate = new Property(14, java.util.Date.class, "updateDate", false, "UPDATE_DATE");
        public final static Property PublishedAt = new Property(15, java.util.Date.class, "publishedAt", false, "PUBLISHED_AT");
        public final static Property StarredAt = new Property(16, java.util.Date.class, "starredAt", false, "STARRED_AT");
        public final static Property IsPublic = new Property(17, Boolean.class, "isPublic", false, "IS_PUBLIC");
        public final static Property PublicUid = new Property(18, String.class, "publicUid", false, "PUBLIC_UID");
        public final static Property ArticleProgress = new Property(19, Double.class, "articleProgress", false, "ARTICLE_PROGRESS");
        public final static Property ImagesDownloaded = new Property(20, Boolean.class, "imagesDownloaded", false, "IMAGES_DOWNLOADED");
    }

    private DaoSession daoSession;


    public ArticleDao(DaoConfig config) {
        super(config);
    }
    
    public ArticleDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
        this.daoSession = daoSession;
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"ARTICLE\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"ARTICLE_ID\" INTEGER UNIQUE ," + // 1: articleId
                "\"TITLE\" TEXT," + // 2: title
                "\"DOMAIN\" TEXT," + // 3: domain
                "\"URL\" TEXT," + // 4: url
                "\"GIVEN_URL\" TEXT," + // 5: givenUrl
                "\"ORIGIN_URL\" TEXT," + // 6: originUrl
                "\"ESTIMATED_READING_TIME\" INTEGER NOT NULL ," + // 7: estimatedReadingTime
                "\"LANGUAGE\" TEXT," + // 8: language
                "\"PREVIEW_PICTURE_URL\" TEXT," + // 9: previewPictureURL
                "\"AUTHORS\" TEXT," + // 10: authors
                "\"FAVORITE\" INTEGER," + // 11: favorite
                "\"ARCHIVE\" INTEGER," + // 12: archive
                "\"CREATION_DATE\" INTEGER," + // 13: creationDate
                "\"UPDATE_DATE\" INTEGER," + // 14: updateDate
                "\"PUBLISHED_AT\" INTEGER," + // 15: publishedAt
                "\"STARRED_AT\" INTEGER," + // 16: starredAt
                "\"IS_PUBLIC\" INTEGER," + // 17: isPublic
                "\"PUBLIC_UID\" TEXT," + // 18: publicUid
                "\"ARTICLE_PROGRESS\" REAL," + // 19: articleProgress
                "\"IMAGES_DOWNLOADED\" INTEGER);"); // 20: imagesDownloaded
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"ARTICLE\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, Article entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Integer articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(2, articleId);
        }
 
        String title = entity.getTitle();
        if (title != null) {
            stmt.bindString(3, title);
        }
 
        String domain = entity.getDomain();
        if (domain != null) {
            stmt.bindString(4, domain);
        }
 
        String url = entity.getUrl();
        if (url != null) {
            stmt.bindString(5, url);
        }
 
        String givenUrl = entity.getGivenUrl();
        if (givenUrl != null) {
            stmt.bindString(6, givenUrl);
        }
 
        String originUrl = entity.getOriginUrl();
        if (originUrl != null) {
            stmt.bindString(7, originUrl);
        }
        stmt.bindLong(8, entity.getEstimatedReadingTime());
 
        String language = entity.getLanguage();
        if (language != null) {
            stmt.bindString(9, language);
        }
 
        String previewPictureURL = entity.getPreviewPictureURL();
        if (previewPictureURL != null) {
            stmt.bindString(10, previewPictureURL);
        }
 
        String authors = entity.getAuthors();
        if (authors != null) {
            stmt.bindString(11, authors);
        }
 
        Boolean favorite = entity.getFavorite();
        if (favorite != null) {
            stmt.bindLong(12, favorite ? 1L: 0L);
        }
 
        Boolean archive = entity.getArchive();
        if (archive != null) {
            stmt.bindLong(13, archive ? 1L: 0L);
        }
 
        java.util.Date creationDate = entity.getCreationDate();
        if (creationDate != null) {
            stmt.bindLong(14, creationDate.getTime());
        }
 
        java.util.Date updateDate = entity.getUpdateDate();
        if (updateDate != null) {
            stmt.bindLong(15, updateDate.getTime());
        }
 
        java.util.Date publishedAt = entity.getPublishedAt();
        if (publishedAt != null) {
            stmt.bindLong(16, publishedAt.getTime());
        }
 
        java.util.Date starredAt = entity.getStarredAt();
        if (starredAt != null) {
            stmt.bindLong(17, starredAt.getTime());
        }
 
        Boolean isPublic = entity.getIsPublic();
        if (isPublic != null) {
            stmt.bindLong(18, isPublic ? 1L: 0L);
        }
 
        String publicUid = entity.getPublicUid();
        if (publicUid != null) {
            stmt.bindString(19, publicUid);
        }
 
        Double articleProgress = entity.getArticleProgress();
        if (articleProgress != null) {
            stmt.bindDouble(20, articleProgress);
        }
 
        Boolean imagesDownloaded = entity.getImagesDownloaded();
        if (imagesDownloaded != null) {
            stmt.bindLong(21, imagesDownloaded ? 1L: 0L);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, Article entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        Integer articleId = entity.getArticleId();
        if (articleId != null) {
            stmt.bindLong(2, articleId);
        }
 
        String title = entity.getTitle();
        if (title != null) {
            stmt.bindString(3, title);
        }
 
        String domain = entity.getDomain();
        if (domain != null) {
            stmt.bindString(4, domain);
        }
 
        String url = entity.getUrl();
        if (url != null) {
            stmt.bindString(5, url);
        }
 
        String givenUrl = entity.getGivenUrl();
        if (givenUrl != null) {
            stmt.bindString(6, givenUrl);
        }
 
        String originUrl = entity.getOriginUrl();
        if (originUrl != null) {
            stmt.bindString(7, originUrl);
        }
        stmt.bindLong(8, entity.getEstimatedReadingTime());
 
        String language = entity.getLanguage();
        if (language != null) {
            stmt.bindString(9, language);
        }
 
        String previewPictureURL = entity.getPreviewPictureURL();
        if (previewPictureURL != null) {
            stmt.bindString(10, previewPictureURL);
        }
 
        String authors = entity.getAuthors();
        if (authors != null) {
            stmt.bindString(11, authors);
        }
 
        Boolean favorite = entity.getFavorite();
        if (favorite != null) {
            stmt.bindLong(12, favorite ? 1L: 0L);
        }
 
        Boolean archive = entity.getArchive();
        if (archive != null) {
            stmt.bindLong(13, archive ? 1L: 0L);
        }
 
        java.util.Date creationDate = entity.getCreationDate();
        if (creationDate != null) {
            stmt.bindLong(14, creationDate.getTime());
        }
 
        java.util.Date updateDate = entity.getUpdateDate();
        if (updateDate != null) {
            stmt.bindLong(15, updateDate.getTime());
        }
 
        java.util.Date publishedAt = entity.getPublishedAt();
        if (publishedAt != null) {
            stmt.bindLong(16, publishedAt.getTime());
        }
 
        java.util.Date starredAt = entity.getStarredAt();
        if (starredAt != null) {
            stmt.bindLong(17, starredAt.getTime());
        }
 
        Boolean isPublic = entity.getIsPublic();
        if (isPublic != null) {
            stmt.bindLong(18, isPublic ? 1L: 0L);
        }
 
        String publicUid = entity.getPublicUid();
        if (publicUid != null) {
            stmt.bindString(19, publicUid);
        }
 
        Double articleProgress = entity.getArticleProgress();
        if (articleProgress != null) {
            stmt.bindDouble(20, articleProgress);
        }
 
        Boolean imagesDownloaded = entity.getImagesDownloaded();
        if (imagesDownloaded != null) {
            stmt.bindLong(21, imagesDownloaded ? 1L: 0L);
        }
    }

    @Override
    protected final void attachEntity(Article entity) {
        super.attachEntity(entity);
        entity.__setDaoSession(daoSession);
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public Article readEntity(Cursor cursor, int offset) {
        Article entity = new Article( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getInt(offset + 1), // articleId
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2), // title
            cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3), // domain
            cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4), // url
            cursor.isNull(offset + 5) ? null : cursor.getString(offset + 5), // givenUrl
            cursor.isNull(offset + 6) ? null : cursor.getString(offset + 6), // originUrl
            cursor.getInt(offset + 7), // estimatedReadingTime
            cursor.isNull(offset + 8) ? null : cursor.getString(offset + 8), // language
            cursor.isNull(offset + 9) ? null : cursor.getString(offset + 9), // previewPictureURL
            cursor.isNull(offset + 10) ? null : cursor.getString(offset + 10), // authors
            cursor.isNull(offset + 11) ? null : cursor.getShort(offset + 11) != 0, // favorite
            cursor.isNull(offset + 12) ? null : cursor.getShort(offset + 12) != 0, // archive
            cursor.isNull(offset + 13) ? null : new java.util.Date(cursor.getLong(offset + 13)), // creationDate
            cursor.isNull(offset + 14) ? null : new java.util.Date(cursor.getLong(offset + 14)), // updateDate
            cursor.isNull(offset + 15) ? null : new java.util.Date(cursor.getLong(offset + 15)), // publishedAt
            cursor.isNull(offset + 16) ? null : new java.util.Date(cursor.getLong(offset + 16)), // starredAt
            cursor.isNull(offset + 17) ? null : cursor.getShort(offset + 17) != 0, // isPublic
            cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18), // publicUid
            cursor.isNull(offset + 19) ? null : cursor.getDouble(offset + 19), // articleProgress
            cursor.isNull(offset + 20) ? null : cursor.getShort(offset + 20) != 0 // imagesDownloaded
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, Article entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setArticleId(cursor.isNull(offset + 1) ? null : cursor.getInt(offset + 1));
        entity.setTitle(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setDomain(cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3));
        entity.setUrl(cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4));
        entity.setGivenUrl(cursor.isNull(offset + 5) ? null : cursor.getString(offset + 5));
        entity.setOriginUrl(cursor.isNull(offset + 6) ? null : cursor.getString(offset + 6));
        entity.setEstimatedReadingTime(cursor.getInt(offset + 7));
        entity.setLanguage(cursor.isNull(offset + 8) ? null : cursor.getString(offset + 8));
        entity.setPreviewPictureURL(cursor.isNull(offset + 9) ? null : cursor.getString(offset + 9));
        entity.setAuthors(cursor.isNull(offset + 10) ? null : cursor.getString(offset + 10));
        entity.setFavorite(cursor.isNull(offset + 11) ? null : cursor.getShort(offset + 11) != 0);
        entity.setArchive(cursor.isNull(offset + 12) ? null : cursor.getShort(offset + 12) != 0);
        entity.setCreationDate(cursor.isNull(offset + 13) ? null : new java.util.Date(cursor.getLong(offset + 13)));
        entity.setUpdateDate(cursor.isNull(offset + 14) ? null : new java.util.Date(cursor.getLong(offset + 14)));
        entity.setPublishedAt(cursor.isNull(offset + 15) ? null : new java.util.Date(cursor.getLong(offset + 15)));
        entity.setStarredAt(cursor.isNull(offset + 16) ? null : new java.util.Date(cursor.getLong(offset + 16)));
        entity.setIsPublic(cursor.isNull(offset + 17) ? null : cursor.getShort(offset + 17) != 0);
        entity.setPublicUid(cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18));
        entity.setArticleProgress(cursor.isNull(offset + 19) ? null : cursor.getDouble(offset + 19));
        entity.setImagesDownloaded(cursor.isNull(offset + 20) ? null : cursor.getShort(offset + 20) != 0);
     }
    
    @Override
    protected final Long updateKeyAfterInsert(Article entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(Article entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(Article entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}
