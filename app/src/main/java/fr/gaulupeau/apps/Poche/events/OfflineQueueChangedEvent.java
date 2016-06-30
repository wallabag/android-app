package fr.gaulupeau.apps.Poche.events;

public class OfflineQueueChangedEvent {

    protected Long queueLength;

    public OfflineQueueChangedEvent(Long queueLength) {
        this.queueLength = queueLength;
    }

    public Long getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(Long queueLength) {
        this.queueLength = queueLength;
    }

}
