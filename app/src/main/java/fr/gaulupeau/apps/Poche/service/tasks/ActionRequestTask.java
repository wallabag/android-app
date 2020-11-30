package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;
import android.os.Parcel;

import java.util.Objects;

import fr.gaulupeau.apps.Poche.events.ActionResultEvent;
import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;

public class ActionRequestTask extends SimpleTask {

    protected ActionRequest actionRequest;

    public ActionRequestTask(ActionRequest actionRequest) {
        Objects.requireNonNull(actionRequest);
        this.actionRequest = actionRequest;
    }

    @Override
    public void run(Context context) {
        ActionResult result = run(context, actionRequest);

        postEvent(new ActionResultEvent(actionRequest, result));
    }

    protected ActionResult run(Context context, ActionRequest actionRequest) {
        return null;
    }

    // Parcelable implementation

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        dest.writeParcelable(actionRequest, flags);
    }

    @Override
    protected void readFromParcel(Parcel in) {
        super.readFromParcel(in);

        actionRequest = in.readParcelable(getClass().getClassLoader());
    }

    @SuppressWarnings("unused") // needed for CREATOR
    protected ActionRequestTask() {}

    public static final TaskCreator<ActionRequestTask> CREATOR
            = new TaskCreator<>(ActionRequestTask.class);

}
