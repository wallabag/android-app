package fr.gaulupeau.apps.Poche.data.dao.entities;

public class ArticleDeleteItem extends ArticleIdItem<ArticleDeleteItem> {

    ArticleDeleteItem(QueueItem queueItem) {
        super(queueItem);
    }

    @Override
    ArticleDeleteItem self() {
        return this;
    }

}
