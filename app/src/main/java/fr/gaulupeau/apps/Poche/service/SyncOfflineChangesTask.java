package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

public class SyncOfflineChangesTask extends ActionRequestTask {

    public SyncOfflineChangesTask(ActionRequest actionRequest) {
        super(actionRequest);
    }

    @Override
    protected ActionResult run(Context context, ActionRequest actionRequest) {
        return new OfflineChangesSynchronizer(context).synchronize(actionRequest);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected SyncOfflineChangesTask() {}

    public static final TaskCreator<SyncOfflineChangesTask> CREATOR
            = new TaskCreator<>(SyncOfflineChangesTask.class);

}
