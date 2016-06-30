package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.service.BGService;

public class SyncQueueFinishedEvent extends BackgroundOperationEvent {

    protected BGService.Result result;

    public SyncQueueFinishedEvent() {}

    public SyncQueueFinishedEvent(long operationID, BGService.Result result) {
        super(operationID);
        this.result = result;
    }

    public BGService.Result getResult() {
        return result;
    }

    public void setResult(BGService.Result result) {
        this.result = result;
    }

}
