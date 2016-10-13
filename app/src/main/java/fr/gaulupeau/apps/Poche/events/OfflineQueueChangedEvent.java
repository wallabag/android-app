package fr.gaulupeau.apps.Poche.events;

public class OfflineQueueChangedEvent {

    protected Long queueLength;
    protected boolean triggeredByOperation;

    public OfflineQueueChangedEvent(Long queueLength) {
        this.queueLength = queueLength;
    }

    public OfflineQueueChangedEvent(Long queueLength, boolean triggeredByOperation) {
        this.queueLength = queueLength;
        this.triggeredByOperation = triggeredByOperation;
    }

    public Long getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(Long queueLength) {
        this.queueLength = queueLength;
    }

    public boolean isTriggeredByOperation() {
        return triggeredByOperation;
    }

    public void setTriggeredByOperation(boolean triggeredByOperation) {
        this.triggeredByOperation = triggeredByOperation;
    }

}
