package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

public class SyncQueueFinishedEvent extends BackgroundOperationEvent {

    protected ActionResult result;
    protected Long queueLength;

    public SyncQueueFinishedEvent() {}

    public SyncQueueFinishedEvent(ActionRequest request, ActionResult result) {
        super(request);
        this.result = result;
    }

    public SyncQueueFinishedEvent(ActionRequest request, ActionResult result, Long queueLength) {
        super(request);
        this.result = result;
        this.queueLength = queueLength;
    }

    public ActionResult getResult() {
        return result;
    }

    public void setResult(ActionResult result) {
        this.result = result;
    }

    public Long getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(Long queueLength) {
        this.queueLength = queueLength;
    }

}
