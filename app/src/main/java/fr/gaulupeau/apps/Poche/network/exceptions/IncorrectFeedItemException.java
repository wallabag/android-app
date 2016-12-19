package fr.gaulupeau.apps.Poche.network.exceptions;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class IncorrectFeedItemException extends IncorrectFeedException {

    private FeedUpdater.Item item;

    public IncorrectFeedItemException(FeedUpdater.Item item, Throwable cause) {
        super(String.format("Erroneous item link: %s, source url: %s, title: %s, pubDate: %s, text: %s",
                item.link, item.sourceUrl, item.title, item.pubDate,
                truncateText(item.description, 100)), cause);
    }

    public FeedUpdater.Item getItem() {
        return item;
    }

    private static String truncateText(String text, int len) {
        if(text == null || text.isEmpty() || text.length() < len) return text;

        return text.substring(0, len) + "...";
    }

}
