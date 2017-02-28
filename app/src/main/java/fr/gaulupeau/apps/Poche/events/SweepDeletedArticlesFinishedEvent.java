package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

public class SweepDeletedArticlesFinishedEvent extends BackgroundOperationFinishedEvent {

    public SweepDeletedArticlesFinishedEvent(ActionRequest request, ActionResult result) {
        super(request, result);
    }

}
