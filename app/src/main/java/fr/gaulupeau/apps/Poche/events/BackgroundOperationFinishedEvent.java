package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

public class BackgroundOperationFinishedEvent extends BackgroundOperationEvent {

    protected ActionResult result;

    public BackgroundOperationFinishedEvent(ActionRequest request, ActionResult result) {
        super(request);
        this.result = result;
    }

    public ActionResult getResult() {
        return result;
    }

    public void setResult(ActionResult result) {
        this.result = result;
    }

}
