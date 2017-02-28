package fr.gaulupeau.apps.Poche.events;

import java.io.File;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

public class DownloadFileFinishedEvent extends BackgroundOperationFinishedEvent {

    private Article article;
    private File file;

    public DownloadFileFinishedEvent(ActionRequest request,
                                     ActionResult result,
                                     Article article,
                                     File file) {
        super(request, result);
        this.result = result;
        this.article = article;
        this.file = file;
    }

    public Article getArticle() {
        return article;
    }

    public File getFile() {
        return file;
    }

}
