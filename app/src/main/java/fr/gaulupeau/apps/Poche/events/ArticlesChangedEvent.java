package fr.gaulupeau.apps.Poche.events;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

public class ArticlesChangedEvent extends FeedsChangedEvent {

    public static class ArticleEntry {

        public Article article;
        public EnumSet<ChangeType> changes;

        public ArticleEntry() {}

        public ArticleEntry(Article article, EnumSet<ChangeType> changes) {
            this.article = article;
            this.changes = changes;
        }

    }

    private Map<Integer, ArticleEntry> changedArticlesMap = new HashMap<>();

    public ArticlesChangedEvent() {}

    public ArticlesChangedEvent(Article article, ChangeType changeType) {
        addArticleChange(article, changeType);
    }

    public boolean isChanged(Article article, ChangeType change) {
        return isChanged(article.getArticleId(), change);
    }

    public boolean isChanged(int articleId, ChangeType change) {
        EnumSet<ChangeType> articleChanges = getArticleChanges(articleId);

        return articleChanges != null && articleChanges.contains(change);
    }

    public boolean isChangedAny(Article article, EnumSet<ChangeType> changes) {
        return isChangedAny(article.getArticleId(), changes);
    }

    public boolean isChangedAny(int articleId, EnumSet<ChangeType> changes) {
        EnumSet<ChangeType> articleChanges = getArticleChanges(articleId);

        return articleChanges != null && !articleChanges.isEmpty()
                && containsAny(articleChanges, changes);
    }

    public Map<Integer, ArticleEntry> getChangedArticles() {
        return changedArticlesMap;
    }

    public void addArticleChange(Article article, ChangeType changeType) {
        addArticleChange(article, EnumSet.of(changeType));
    }

    public void addArticleChangeWithoutObject(Article article, ChangeType changeType) {
        addArticleChangeWithoutObject(article, EnumSet.of(changeType));
    }

    public void addArticleChange(Article article, EnumSet<ChangeType> changes) {
        addChanges(article, article.getArticleId(), changes, true);
    }

    public void addArticleChangeWithoutObject(Article article, EnumSet<ChangeType> changes) {
        addChanges(article, article.getArticleId(), changes, false);
    }

    protected void addChanges(Article article, int articleID, EnumSet<ChangeType> changes,
                              boolean addArticleObject) {
        EnumSet<ChangeType> resultChanges;

        ArticleEntry articleEntry = getChangedArticles().get(articleID);
        if(articleEntry == null) {
            articleEntry = new ArticleEntry(addArticleObject ? article : null, changes);
            getChangedArticles().put(articleID, articleEntry);

            resultChanges = changes;
        } else {
            articleEntry.changes.addAll(changes);

            resultChanges = articleEntry.changes;
        }

        addChangesToFeeds(article, resultChanges);
    }

    public EnumSet<ChangeType> getArticleChanges(Article article) {
        return getArticleChanges(article.getArticleId());
    }

    public EnumSet<ChangeType> getArticleChanges(Integer articleID) {
        if(getChangedArticles() != null) {
            ArticleEntry entry = getChangedArticles().get(articleID);
            if(entry != null) return entry.changes;
        }

        if(isInvalidateAll()) return invalidateAllChanges;

        return null;
    }

    protected void addChangesToFeeds(Article article, EnumSet<ChangeType> changes) {
        boolean mainUpdated = false;
        boolean favoriteUpdated = false;
        boolean archiveUpdated = false;

        if(article == null || changes.contains(ChangeType.UNSPECIFIED)) {
            mainUpdated = true;
            favoriteUpdated = true;
            archiveUpdated = true;
        } else {
            if(changes.contains(ChangeType.ARCHIVED) || changes.contains(ChangeType.UNARCHIVED)) {
                mainUpdated = archiveUpdated = true;
            } else if(article.getArchive()) {
                archiveUpdated = true;
            } else {
                mainUpdated = true;
            }

            if(article.getFavorite()
                    || changes.contains(ChangeType.FAVORITED)
                    || changes.contains(ChangeType.UNFAVORITED)) {
                favoriteUpdated = true;
            }
        }

        if(mainUpdated) mainFeedChanges.addAll(changes);
        if(favoriteUpdated) favoriteFeedChanges.addAll(changes);
        if(archiveUpdated) archiveFeedChanges.addAll(changes);
    }

}
