package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class UpdateArticlesProgressEvent extends ProgressEvent {

    public UpdateArticlesProgressEvent(ActionRequest request, int current, int total) {
        super(request, current, total);
    }

}
