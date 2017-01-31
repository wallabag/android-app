package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class UpdateFeedsStartedEvent extends BackgroundOperationEvent {

    public UpdateFeedsStartedEvent(ActionRequest request) {
        super(request);
    }

}
