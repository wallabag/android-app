package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import fr.gaulupeau.apps.Poche.service.workers.OfflineChangesSynchronizer;

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
