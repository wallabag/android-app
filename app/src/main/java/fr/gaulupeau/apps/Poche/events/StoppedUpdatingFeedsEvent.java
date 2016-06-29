package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class StoppedUpdatingFeedsEvent {

    protected FeedUpdater.FeedType feedType;

    public StoppedUpdatingFeedsEvent(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

    public FeedUpdater.FeedType getFeedType() {
        return feedType;
    }

    public void setFeedType(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

}
