package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

public class AddLinkFinishedEvent extends BackgroundOperationEvent {

    protected ActionResult result;

    public AddLinkFinishedEvent() {}

    public AddLinkFinishedEvent(ActionRequest request) {
        super(request);
    }

    public AddLinkFinishedEvent(ActionRequest request, ActionResult result) {
        super(request);
        this.result = result;
    }

    public String getLink() {
        return request.getLink();
    }

    public ActionResult getResult() {
        return result;
    }

    public void setResult(ActionResult result) {
        this.result = result;
    }

}
