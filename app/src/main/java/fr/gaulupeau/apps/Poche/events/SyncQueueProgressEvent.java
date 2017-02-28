package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class SyncQueueProgressEvent extends ProgressEvent {

    public SyncQueueProgressEvent(ActionRequest request, int current, int total) {
        super(request, current, total);
    }

}
