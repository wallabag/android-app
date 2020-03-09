package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

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
