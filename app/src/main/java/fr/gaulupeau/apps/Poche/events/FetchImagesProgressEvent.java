package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class FetchImagesProgressEvent extends ProgressEvent {

    public FetchImagesProgressEvent(ActionRequest request, int current, int total) {
        super(request, current, total);
    }

}
