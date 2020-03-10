package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import fr.gaulupeau.apps.Poche.service.workers.DeletedArticleSweeper;

public class SweepDeletedArticlesTask extends ActionRequestTask {

    public SweepDeletedArticlesTask(ActionRequest actionRequest) {
        super(actionRequest);
    }

    @Override
    protected ActionResult run(Context context, ActionRequest actionRequest) {
        return new DeletedArticleSweeper(context).sweep(actionRequest);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected SweepDeletedArticlesTask() {}

    public static final TaskCreator<SweepDeletedArticlesTask> CREATOR
            = new TaskCreator<>(SweepDeletedArticlesTask.class);

}
