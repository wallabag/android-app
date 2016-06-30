package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class UpdateFeedsStartedEvent extends BackgroundOperationEvent {

    protected FeedUpdater.FeedType feedType;

    public UpdateFeedsStartedEvent(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

    public UpdateFeedsStartedEvent(long operationID, FeedUpdater.FeedType feedType) {
        super(operationID);
        this.feedType = feedType;
    }

    public FeedUpdater.FeedType getFeedType() {
        return feedType;
    }

    public void setFeedType(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

}
