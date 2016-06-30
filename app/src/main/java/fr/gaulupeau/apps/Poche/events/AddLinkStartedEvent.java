package fr.gaulupeau.apps.Poche.events;

public class AddLinkStartedEvent extends BackgroundOperationEvent {

    protected String link;

    public AddLinkStartedEvent() {}

    public AddLinkStartedEvent(String link) {
        this.link = link;
    }

    public AddLinkStartedEvent(long operationID, String link) {
        super(operationID);
        this.link = link;
    }

}
