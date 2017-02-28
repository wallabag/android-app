package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class ProgressEvent extends BackgroundOperationEvent {

    private int current;
    private int total;

    public ProgressEvent(ActionRequest request, int current, int total) {
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
