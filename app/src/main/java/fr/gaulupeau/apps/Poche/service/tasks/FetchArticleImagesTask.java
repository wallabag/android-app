package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import fr.gaulupeau.apps.Poche.service.workers.ArticleImagesFetcher;

public class FetchArticleImagesTask extends ActionRequestTask {

    public FetchArticleImagesTask(ActionRequest actionRequest) {
        super(actionRequest);
    }

    @Override
    protected ActionResult run(Context context, ActionRequest actionRequest) {
        return new ArticleImagesFetcher(context).fetch(actionRequest);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected FetchArticleImagesTask() {}

    public static final TaskCreator<FetchArticleImagesTask> CREATOR
            = new TaskCreator<>(FetchArticleImagesTask.class);

}
