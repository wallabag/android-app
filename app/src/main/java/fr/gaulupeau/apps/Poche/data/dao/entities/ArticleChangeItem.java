package fr.gaulupeau.apps.Poche.data.dao.entities;

import java.util.EnumSet;

public class ArticleChangeItem extends ArticleIdItem<ArticleChangeItem> {

    ArticleChangeItem(QueueItem queueItem) {
        super(queueItem);
    }

    @Override
    ArticleChangeItem self() {
        return this;
    }

    public EnumSet<QueueItem.ArticleChangeType> getArticleChanges() {
        return QueueItem.ArticleChangeType.stringToEnumSet(queueItem.getExtra());
    }

    public ArticleChangeItem setSingleArticleChange(QueueItem.ArticleChangeType articleChange) {
        queueItem.setExtra(articleChange.name());
        return this;
    }

    public ArticleChangeItem setArticleChanges(EnumSet<QueueItem.ArticleChangeType> articleChanges) {
        queueItem.setExtra(QueueItem.ArticleChangeType.enumSetToString(articleChanges));
        return this;
    }

}
