package fr.gaulupeau.apps.Poche.events;

import android.annotation.SuppressLint;

import org.greenrobot.eventbus.EventBus;

import java.util.EnumSet;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.EventBusIndex;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

public class EventHelper {

    private static class EventBusHolder {
        static final EventBus BUS = createEventBus();
    }

    @SuppressWarnings("FieldCanBeLocal")
    @SuppressLint("StaticFieldLeak")
    private static EventProcessor eventProcessor;

    static EventBus createEventBus() {
        EventBus eventBus = EventBus.builder()
                .sendNoSubscriberEvent(false)
                .sendSubscriberExceptionEvent(false)
                .throwSubscriberException(BuildConfig.DEBUG)
                .addIndex(new EventBusIndex())
                .installDefaultEventBus();

        eventProcessor = new EventProcessor(App.getInstance());
        eventBus.register(eventProcessor);

        return eventBus;
    }

    public static EventBus bus() {
        return EventBusHolder.BUS;
    }

    public static void register(Object subscriber) {
        bus().register(subscriber);
    }

    public static void unregister(Object subscriber) {
        bus().unregister(subscriber);
    }

    public static void postEvent(Object event) {
        bus().post(event);
    }

    public static void postStickyEvent(Object event) {
        bus().postSticky(event);
    }

    public static void removeStickyEvent(Object event) {
        bus().removeStickyEvent(event);
    }

    public static void notifyAboutArticleChange(
            Article article, ArticlesChangedEvent.ChangeType changeType) {
        postEvent(new ArticlesChangedEvent(article, changeType));
    }

    /**
     * Notifies that the unread feed changed, without naming a specific article.
     *
     * <p>Unlike {@link #notifyAboutArticleChange(Article, ArticlesChangedEvent.ChangeType)} this
     * works for a pending article: that one unboxes the article's server-side ID, which a pending
     * article does not have.
     */
    public static void notifyMainFeedChanged(ArticlesChangedEvent.ChangeType changeType) {
        ArticlesChangedEvent event = new ArticlesChangedEvent();
        event.addChangesByFeedType(FeedsChangedEvent.FeedType.MAIN, EnumSet.of(changeType));

        postEvent(event);
    }

    public static void notifyEverythingRemoved() {
        postEvent(new OfflineQueueChangedEvent(0L));

        ArticlesChangedEvent event = new ArticlesChangedEvent();
        event.invalidateAll(FeedsChangedEvent.ChangeType.DELETED);

        postEvent(event);
    }

}
