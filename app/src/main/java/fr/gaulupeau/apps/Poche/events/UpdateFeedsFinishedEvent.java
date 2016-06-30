package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.service.BGService;

public class UpdateFeedsFinishedEvent extends BackgroundOperationEvent {

    protected FeedUpdater.FeedType feedType;
    protected BGService.Result result;

    public UpdateFeedsFinishedEvent(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

    public UpdateFeedsFinishedEvent(long operationID, FeedUpdater.FeedType feedType) {
        super(operationID);
        this.feedType = feedType;
    }

    public UpdateFeedsFinishedEvent(long operationID, FeedUpdater.FeedType feedType, BGService.Result result) {
        super(operationID);
        this.feedType = feedType;
        this.result = result;
    }

    public FeedUpdater.FeedType getFeedType() {
        return feedType;
    }

    public void setFeedType(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

    public BGService.Result getResult() {
        return result;
    }

    public void setResult(BGService.Result result) {
        this.result = result;
    }

}
