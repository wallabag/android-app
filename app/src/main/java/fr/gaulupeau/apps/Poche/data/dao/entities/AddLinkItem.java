package fr.gaulupeau.apps.Poche.data.dao.entities;

public class AddLinkItem extends SpecificItem {

    AddLinkItem(QueueItem queueItem) {
        super(queueItem);
    }

    public String getUrl() {
        return queueItem.getExtra();
    }

    public AddLinkItem setUrl(String url) {
        queueItem.setExtra(url);
        return this;
    }

    public String getOrigin() {
        return queueItem.getExtra2();
    }

    public AddLinkItem setOrigin(String origin) {
        queueItem.setExtra2(origin);
        return this;
    }

    public Long getLocalArticleId() {
        return queueItem.getLocalArticleId();
    }

    public AddLinkItem setLocalArticleId(long articleId) {
        queueItem.setLocalArticleId(articleId);
        return this;
    }

}
