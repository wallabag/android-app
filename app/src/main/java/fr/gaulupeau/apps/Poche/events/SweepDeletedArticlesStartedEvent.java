package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class SweepDeletedArticlesStartedEvent extends BackgroundOperationEvent {

    public SweepDeletedArticlesStartedEvent(ActionRequest request) {
        super(request);
    }

}
