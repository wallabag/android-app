package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.entity.Article;

public class ArticleChangedEvent extends DataChangedEvent {

    private Article changedArticle;

    public ArticleChangedEvent() {}

    public ArticleChangedEvent(Article changedArticle) {
        this.changedArticle = changedArticle;
    }

    public Article getChangedArticle() {
        return changedArticle;
    }

    public void setChangedArticle(Article changedArticle) {
        this.changedArticle = changedArticle;
    }

}
