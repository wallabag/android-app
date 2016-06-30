package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.BGService;

public class AddLinkFinishedEvent extends BackgroundOperationEvent {

    protected String link;
    protected BGService.Result result;

    public AddLinkFinishedEvent() {}

    public AddLinkFinishedEvent(String link) {
        this.link = link;
    }

    public AddLinkFinishedEvent(long operationID, String link) {
        super(operationID);
        this.link = link;
    }

    public AddLinkFinishedEvent(long operationID, String link, BGService.Result result) {
        super(operationID);
        this.link = link;
        this.result = result;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public BGService.Result getResult() {
        return result;
    }

    public void setResult(BGService.Result result) {
        this.result = result;
    }

}
