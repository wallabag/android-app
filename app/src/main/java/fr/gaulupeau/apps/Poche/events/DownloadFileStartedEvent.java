package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class DownloadFileStartedEvent extends BackgroundOperationEvent {

    private Article article;

    public DownloadFileStartedEvent(ActionRequest request, Article article) {
        super(request);
        this.article = article;
    }

    public Article getArticle() {
        return article;
    }

}
