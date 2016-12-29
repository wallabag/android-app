package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class FetchImagesStartedEvent extends BackgroundOperationEvent {

    public FetchImagesStartedEvent(ActionRequest request) {
        super(request);
    }

}
