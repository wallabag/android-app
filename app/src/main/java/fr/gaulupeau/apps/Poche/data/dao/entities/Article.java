package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.*;

import java.util.Date;
import java.util.List;
import org.greenrobot.greendao.DaoException;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;

/**
 * Entity mapped to table "ARTICLE".
 */
@Entity
public class Article {

    @Id
    private Long id;

    @Unique
    private Integer articleId;

    private String content;

    private String title;

    private String domain;

    private String url;

    private int estimatedReadingTime;

    private String language;

    private String previewPictureURL;

    private Boolean favorite;

    private Boolean archive;

    private Date creationDate;

    private Date updateDate;

    private Double articleProgress;

    private Boolean imagesDownloaded;

    @ToMany
    @JoinEntity(
            entity = ArticleTagsJoin.class,
            sourceProperty = "articleId",
            targetProperty = "tagId"
    )
    private List<Tag> tags;

    /** Used to resolve relations */
    @Generated(hash = 2040040024)
    private transient DaoSession daoSession;

    /** Used for active entity operations. */
    @Generated(hash = 434328755)
    private transient ArticleDao myDao;

    @Generated(hash = 742516792)
    public Article() {}

    public Article(Long id) {
        this.id = id;
    }

    @Generated(hash = 768349527)
    public Article(Long id, Integer articleId, String content, String title, String domain, String url,
            int estimatedReadingTime, String language, String previewPictureURL, Boolean favorite,
            Boolean archive, Date creationDate, Date updateDate, Double articleProgress,
            Boolean imagesDownloaded) {
        this.id = id;
        this.articleId = articleId;
        this.content = content;
        this.title = title;
        this.domain = domain;
        this.url = url;
        this.estimatedReadingTime = estimatedReadingTime;
        this.language = language;
        this.previewPictureURL = previewPictureURL;
        this.favorite = favorite;
        this.archive = archive;
        this.creationDate = creationDate;
        this.updateDate = updateDate;
        this.articleProgress = articleProgress;
        this.imagesDownloaded = imagesDownloaded;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getArticleId() {
        return articleId;
    }

    public void setArticleId(Integer articleId) {
        this.articleId = articleId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDomain() {
        return this.domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getEstimatedReadingTime() {
        return this.estimatedReadingTime;
    }

    public void setEstimatedReadingTime(int estimatedReadingTime) {
        this.estimatedReadingTime = estimatedReadingTime;
    }

    public int getEstimatedReadingTime(int readingSpeed) {
        return (int)Math.round(this.estimatedReadingTime * 200. / readingSpeed);
    }

    public String getLanguage() {
        return this.language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPreviewPictureURL() {
        return this.previewPictureURL;
    }

    public void setPreviewPictureURL(String previewPictureURL) {
        this.previewPictureURL = previewPictureURL;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public Boolean getArchive() {
        return archive;
    }

    public void setArchive(Boolean archive) {
        this.archive = archive;
    }

    public Date getCreationDate() {
        return this.creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public Double getArticleProgress() {
        return articleProgress;
    }

    public void setArticleProgress(Double articleProgress) {
        this.articleProgress = articleProgress;
    }

    public Boolean getImagesDownloaded() {
        return imagesDownloaded;
    }

    public void setImagesDownloaded(Boolean imagesDownloaded) {
        this.imagesDownloaded = imagesDownloaded;
    }

    /**
     * To-many relationship, resolved on first access (and after reset).
     * Changes to to-many relations are not persisted, make changes to the target entity.
     */
    @Generated(hash = 1016404958)
    public List<Tag> getTags() {
        if(tags == null) {
            final DaoSession daoSession = this.daoSession;
            if(daoSession == null) {
                throw new DaoException("Entity is detached from DAO context");
            }
            TagDao targetDao = daoSession.getTagDao();
            List<Tag> tagsNew = targetDao._queryArticle_Tags(id);
            synchronized(this) {
                if(tags == null) {
                    tags = tagsNew;
                }
            }
        }
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    /** Resets a to-many relationship, making the next get call to query for a fresh result. */
    @Generated(hash = 404234)
    public synchronized void resetTags() {
        tags = null;
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#delete(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 128553479)
    public void delete() {
        if(myDao == null) {
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
        if(myDao == null) {
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
        if(myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.update(this);
    }

    /** called by internal mechanisms, do not call yourself. */
    @Generated(hash = 2112142041)
    public void __setDaoSession(DaoSession daoSession) {
        this.daoSession = daoSession;
        myDao = daoSession != null ? daoSession.getArticleDao() : null;
    }

}
