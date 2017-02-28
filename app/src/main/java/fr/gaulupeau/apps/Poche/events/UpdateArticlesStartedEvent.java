package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class UpdateArticlesStartedEvent extends BackgroundOperationEvent {

    public UpdateArticlesStartedEvent(ActionRequest request) {
        super(request);
    }

}
