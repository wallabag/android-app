package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class AddLinkStartedEvent extends BackgroundOperationEvent {

    public AddLinkStartedEvent() {}

    public AddLinkStartedEvent(ActionRequest request) {
        super(request);
    }

    public String getLink() {
        return request.getLink();
    }

}
