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
        postEvent(new ArticlesChangedEvent(article, changeType));
    }

    public static void notifyEverythingRemoved() {
        postEvent(new OfflineQueueChangedEvent(0L));

        ArticlesChangedEvent event = new ArticlesChangedEvent();
        event.invalidateAll(FeedsChangedEvent.ChangeType.DELETED);

        postEvent(event);
    }

}
