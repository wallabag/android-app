package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class FetchImagesProgressEvent extends BackgroundOperationEvent {

    private int current;
    private int total;

    public FetchImagesProgressEvent(ActionRequest request, int current, int total) {
        super(request);
        this.current = current;
        this.total = total;
    }

    public int getCurrent() {
        return current;
    }

    public int getTotal() {
        return total;
    }

}
