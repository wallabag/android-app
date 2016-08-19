package fr.gaulupeau.apps.Poche.events;

import org.greenrobot.eventbus.EventBus;

import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class EventHelper {

    public static void postEvent(Object event) {
        EventBus.getDefault().post(event);
    }

    public static void postStickyEvent(Object event) {
        EventBus.getDefault().postSticky(event);
    }

    public static void removeStickyEvent(Object event) {
        EventBus.getDefault().removeStickyEvent(event);
    }

    public static void notifyAboutFeedChanges(
            Article article, ArticleChangedEvent.ChangeType changeType) {
        boolean mainUpdated = false;
        boolean archiveUpdated = false;
        boolean favoriteUpdated = false;

        switch(changeType) {
            case Archived:
            case Unarchived:
                mainUpdated = archiveUpdated = true;
                if(article.getFavorite()) favoriteUpdated = true;
                break;

            case Favorited:
            case Unfavorited:
                favoriteUpdated = true;
                if(article.getArchive()) archiveUpdated = true;
                else mainUpdated = true;
                break;

            case Deleted:
                if(article.getArchive()) archiveUpdated = true;
                else mainUpdated = true;
                if(article.getFavorite()) favoriteUpdated = true;
                break;
        }

        if(archiveUpdated && mainUpdated && favoriteUpdated) {
            postEvent(new FeedsChangedEvent(null));
        } else {
            if(mainUpdated) postEvent(new FeedsChangedEvent(FeedUpdater.FeedType.Main));
            if(archiveUpdated) postEvent(new FeedsChangedEvent(FeedUpdater.FeedType.Archive));
            if(favoriteUpdated) postEvent(new FeedsChangedEvent(FeedUpdater.FeedType.Favorite));
        }
    }

}
