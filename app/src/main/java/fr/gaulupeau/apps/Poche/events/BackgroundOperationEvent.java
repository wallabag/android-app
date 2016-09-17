package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionRequest;

public class BackgroundOperationEvent {

    protected ActionRequest request;

    public BackgroundOperationEvent() {}

    public BackgroundOperationEvent(ActionRequest request) {
        this.request = request;
    }

    public ActionRequest getRequest() {
        return request;
    }

    public void setRequest(ActionRequest request) {
        this.request = request;
    }

    public Long getOperationID() {
        return request.getOperationID();
    }

}
