package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

public class UpdateArticlesTask extends ActionRequestTask {

    public UpdateArticlesTask(ActionRequest actionRequest) {
        super(actionRequest);
    }

    @Override
    protected ActionResult run(Context context, ActionRequest actionRequest) {
        return new ArticleUpdater(context).update(actionRequest);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected UpdateArticlesTask() {}

    public static final TaskCreator<UpdateArticlesTask> CREATOR
            = new TaskCreator<>(UpdateArticlesTask.class);

}
