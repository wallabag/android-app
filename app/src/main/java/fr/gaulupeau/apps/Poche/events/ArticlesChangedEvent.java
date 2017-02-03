package fr.gaulupeau.apps.Poche.events;

import java.util.HashMap;
import java.util.Map;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

public class ArticlesChangedEvent extends FeedsChangedEvent {

    public enum ChangeType {
        ARCHIVED, UNARCHIVED, FAVORITED, UNFAVORITED, TITLE_CHANGED, TAGS_CHANGED,
        ADDED, DELETED, UNSPECIFIED
    }

    public static class ArticleEntry {

        public Article article;
        public ChangeType changeType;

        public ArticleEntry() {}

        public ArticleEntry(Article article, ChangeType changeType) {
            this.article = article;
            this.changeType = changeType;
        }

    }

    private Map<Integer, ArticleEntry> changedArticlesMap;

    public ArticlesChangedEvent() {
        this.changedArticlesMap = new HashMap<>();
    }

    public ArticlesChangedEvent(Map<Integer, ArticleEntry> changedArticles) {
        this.changedArticlesMap = changedArticles;
    }

    public ArticlesChangedEvent(Article changedArticle, ChangeType changeType) {
        this();
        addChangedArticle(changedArticle, changeType);
    }

    public Map<Integer, ArticleEntry> getChangedArticles() {
        return changedArticlesMap;
    }

    public void setChangedArticles(Map<Integer, ArticleEntry> changedArticles) {
        this.changedArticlesMap = changedArticles;
    }

    public void addChangedArticle(Article article, ChangeType changeType) {
        getChangedArticles().put(article.getArticleId(), new ArticleEntry(article, changeType));
    }

    public void addChangedArticleID(Article article, ChangeType changeType) {
        getChangedArticles().put(article.getArticleId(), new ArticleEntry(null, changeType));
    }

    public ChangeType getArticleChangeType(Article article) {
        return getArticleChangeType(article.getArticleId());
    }

    public ChangeType getArticleChangeType(Integer articleID) {
        if(isInvalidateAll()) return ChangeType.UNSPECIFIED;

        if(getChangedArticles() == null) return null;

        ArticleEntry entry = getChangedArticles().get(articleID);
        return entry != null ? entry.changeType : null;
    }

}
