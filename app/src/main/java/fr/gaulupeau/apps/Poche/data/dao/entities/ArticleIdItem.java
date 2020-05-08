package fr.gaulupeau.apps.Poche.data.dao.entities;

abstract class ArticleIdItem<T extends ArticleIdItem> extends SpecificItem {

    ArticleIdItem(QueueItem queueItem) {
        super(queueItem);
    }

    abstract T self();

    public Integer getArticleId() {
        return queueItem.getArticleId();
    }

    public T setArticleId(Integer articleId) {
        queueItem.setArticleId(articleId);
        return self();
    }

}
