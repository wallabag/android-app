package fr.gaulupeau.apps.Poche.events;

public class FeedsChangedEvent {

    public enum FeedType { MAIN, FAVORITE, ARCHIVE }

    private boolean invalidateAll;

    private boolean mainFeedChanged;
    private boolean favoriteFeedChanged;
    private boolean archiveFeedChanged;

    public FeedsChangedEvent() {}

    public boolean isInvalidateAll() {
        return invalidateAll;
    }

    public void setInvalidateAll(boolean invalidateAll) {
        this.invalidateAll = invalidateAll;
        if(invalidateAll) {
            this.mainFeedChanged = true;
            this.favoriteFeedChanged = true;
            this.archiveFeedChanged = true;
        }
    }

    public boolean isMainFeedChanged() {
        return mainFeedChanged;
    }

    public void setMainFeedChanged(boolean mainFeedChanged) {
        this.mainFeedChanged = mainFeedChanged;
    }

    public boolean isFavoriteFeedChanged() {
        return favoriteFeedChanged;
    }

    public void setFavoriteFeedChanged(boolean favoriteFeedChanged) {
        this.favoriteFeedChanged = favoriteFeedChanged;
    }

    public boolean isArchiveFeedChanged() {
        return archiveFeedChanged;
    }

    public void setArchiveFeedChanged(boolean archiveFeedChanged) {
        this.archiveFeedChanged = archiveFeedChanged;
    }

    public boolean isAnythingChanged() {
        return invalidateAll || mainFeedChanged || favoriteFeedChanged || archiveFeedChanged;
    }

    public void setChangedByFeedType(FeedType feedType) {
        if(feedType == null) return;

        switch(feedType) {
            case MAIN:
                mainFeedChanged = true;
                break;

            case FAVORITE:
                favoriteFeedChanged = true;
                break;

            case ARCHIVE:
                archiveFeedChanged = true;
                break;
        }
    }

}
