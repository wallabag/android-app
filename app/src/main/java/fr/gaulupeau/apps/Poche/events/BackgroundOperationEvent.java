package fr.gaulupeau.apps.Poche.events;

public class BackgroundOperationEvent {

    protected long operationID = -1;

    public BackgroundOperationEvent() {}

    public BackgroundOperationEvent(long operationID) {
        this.operationID = operationID;
    }

    public long getOperationID() {
        return operationID;
    }

    public void setOperationID(long operationID) {
        this.operationID = operationID;
    }

}
