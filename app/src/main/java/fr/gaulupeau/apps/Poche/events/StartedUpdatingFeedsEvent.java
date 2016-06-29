package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class StartedUpdatingFeedsEvent {

    protected FeedUpdater.FeedType feedType;

    public StartedUpdatingFeedsEvent(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

    public FeedUpdater.FeedType getFeedType() {
        return feedType;
    }

    public void setFeedType(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

}
