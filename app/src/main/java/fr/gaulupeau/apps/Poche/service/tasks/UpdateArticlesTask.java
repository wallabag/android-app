package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import fr.gaulupeau.apps.Poche.service.workers.ArticleUpdater;

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
