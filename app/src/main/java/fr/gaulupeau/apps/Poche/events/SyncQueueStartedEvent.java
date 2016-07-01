package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class SyncQueueStartedEvent extends BackgroundOperationEvent {

    public SyncQueueStartedEvent() {}

    public SyncQueueStartedEvent(ActionRequest request) {
        super(request);
    }

}
