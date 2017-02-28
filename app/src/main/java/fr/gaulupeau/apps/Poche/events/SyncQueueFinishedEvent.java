package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

public class SyncQueueFinishedEvent extends BackgroundOperationFinishedEvent {

    protected Long queueLength;

    public SyncQueueFinishedEvent(ActionRequest request, ActionResult result) {
        super(request, result);
    }

    public SyncQueueFinishedEvent(ActionRequest request, ActionResult result, Long queueLength) {
        super(request, result);
        this.queueLength = queueLength;
    }

    public Long getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(Long queueLength) {
        this.queueLength = queueLength;
    }

}
