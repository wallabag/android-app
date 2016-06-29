package fr.gaulupeau.apps.Poche.events;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class FeedsChangedEvent {

    private FeedUpdater.FeedType feedType;

    public FeedsChangedEvent(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

    public FeedUpdater.FeedType getFeedType() {
        return feedType;
    }

    public void setFeedType(FeedUpdater.FeedType feedType) {
        this.feedType = feedType;
    }

}
