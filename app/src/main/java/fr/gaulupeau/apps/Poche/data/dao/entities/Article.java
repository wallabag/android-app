package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.*;

import java.util.Date;

/**
 * Entity mapped to table "ARTICLE".
 */
@Entity
public class Article {

    @Id
    private Long id;

    @Property(nameInDb = "article_id")
    @Unique
    private Integer articleId;

    @Property(nameInDb = "content")
    private String content;

    @Property(nameInDb = "author")
    private String author;

    @Property(nameInDb = "title")
    private String title;

    @Property(nameInDb = "url")
    private String url;

    @Property(nameInDb = "favorite")
    private Boolean favorite;

    @Property(nameInDb = "archive")
    private Boolean archive;

    @Property(nameInDb = "update_date")
    private Date updateDate;

    @Property(nameInDb = "article_progress")
    private Double articleProgress;

    @Property(nameInDb = "images_downloaded")
    private Boolean imagesDownloaded;

    @Generated(hash = 742516792)
    public Article() {}

    public Article(Long id) {
        this.id = id;
    }

    @Generated(hash = 1635440040)
    public Article(Long id, Integer articleId, String content, String author,
            String title, String url, Boolean favorite, Boolean archive,
            Date updateDate, Double articleProgress, Boolean imagesDownloaded) {
        this.id = id;
        this.articleId = articleId;
        this.content = content;
        this.author = author;
        this.title = title;
        this.url = url;
        this.favorite = favorite;
        this.archive = archive;
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

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

}
