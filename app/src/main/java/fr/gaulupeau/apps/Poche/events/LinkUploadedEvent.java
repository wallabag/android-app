package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.ActionResult;

public class LinkUploadedEvent {

    protected ActionResult result;
    protected String link;

    public LinkUploadedEvent() {}

    public LinkUploadedEvent(ActionResult result) {
        this.result = result;
    }

    public LinkUploadedEvent(ActionResult result, String link) {
        this.result = result;
        this.link = link;
    }

    public ActionResult getResult() {
        return result;
    }

    public void setResult(ActionResult result) {
        this.result = result;
    }

    public String getLink() {
        return link;
    }

}
