package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;

public class UpdateArticlesFinishedEvent extends BackgroundOperationFinishedEvent {

    public UpdateArticlesFinishedEvent(ActionRequest request, ActionResult result) {
        super(request, result);
    }

}
