package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

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
