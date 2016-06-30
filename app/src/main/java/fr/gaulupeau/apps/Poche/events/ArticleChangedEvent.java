package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.entity.Article;

public class ArticleChangedEvent extends ContentChangedEvent {

    public enum ChangeType {
        Archived, Unarchived, Favorited, Unfavorited, Deleted
    }

    private Article changedArticle;
    private ChangeType changeType;

    public ArticleChangedEvent() {}

    public ArticleChangedEvent(Article changedArticle) {
        this.changedArticle = changedArticle;
    }

    public ArticleChangedEvent(Article changedArticle, ChangeType changeType) {
        this.changedArticle = changedArticle;
        this.changeType = changeType;
    }

    public Article getChangedArticle() {
        return changedArticle;
    }

    public void setChangedArticle(Article changedArticle) {
        this.changedArticle = changedArticle;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

}
