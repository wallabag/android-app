package fr.gaulupeau.apps.Poche.events;

import org.greenrobot.eventbus.EventBus;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

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

    public static void notifyAboutArticleChange(
            Article article, ArticlesChangedEvent.ChangeType changeType) {
        boolean mainUpdated = false;
        boolean archiveUpdated = false;
        boolean favoriteUpdated = false;

        switch(changeType) {
            case ARCHIVED:
            case UNARCHIVED:
                mainUpdated = archiveUpdated = true;
                if(article.getFavorite()) favoriteUpdated = true;
                break;

            case FAVORITED:
            case UNFAVORITED:
                favoriteUpdated = true;
                if(article.getArchive()) archiveUpdated = true;
                else mainUpdated = true;
                break;

            default:
                if(article.getArchive()) archiveUpdated = true;
                else mainUpdated = true;
                if(article.getFavorite()) favoriteUpdated = true;
                break;
        }

        ArticlesChangedEvent event = new ArticlesChangedEvent(article, changeType);
        event.setMainFeedChanged(mainUpdated);
        event.setArchiveFeedChanged(archiveUpdated);
        event.setFavoriteFeedChanged(favoriteUpdated);
        postEvent(event);
    }

    public static void notifyEverythingChanged() {
        ArticlesChangedEvent event = new ArticlesChangedEvent();
        event.setInvalidateAll(true);
        postEvent(event);
    }

}
