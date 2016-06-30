package fr.gaulupeau.apps.Poche.events;

public class SyncQueueStartedEvent extends BackgroundOperationEvent {

    public SyncQueueStartedEvent() {}

    public SyncQueueStartedEvent(long operationID) {
        super(operationID);
    }

}
