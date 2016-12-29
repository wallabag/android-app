package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class FetchImagesFinishedEvent extends BackgroundOperationEvent {

    public FetchImagesFinishedEvent(ActionRequest request) {
        super(request);
    }

}
