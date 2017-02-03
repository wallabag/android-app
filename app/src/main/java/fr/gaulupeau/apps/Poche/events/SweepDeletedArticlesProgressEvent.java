package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class SweepDeletedArticlesProgressEvent extends ProgressEvent {

    public SweepDeletedArticlesProgressEvent(ActionRequest request, int current, int total) {
        super(request, current, total);
    }

}
