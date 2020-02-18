package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.query.QueryBuilder;

import java.util.Collection;

import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;

@Entity
public class ArticleTagsJoin {

    @Id
    private Long id;

    @Index
    private Long articleId;
    @Index
    private Long tagId;

    @Generated(hash = 444901753)
    public ArticleTagsJoin() {}

    @Generated(hash = 498133307)
    public ArticleTagsJoin(Long id, Long articleId, Long tagId) {
        this.id = id;
        this.articleId = articleId;
        this.tagId = tagId;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTagId() {
        return this.tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public Long getArticleId() {
        return this.articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public static QueryBuilder<ArticleTagsJoin> getTagsJoinByArticleQueryBuilder(
            Collection<Long> articleIds, ArticleTagsJoinDao dao) {
        return dao.queryBuilder().where(ArticleTagsJoinDao.Properties.ArticleId.in(articleIds));
    }

}
