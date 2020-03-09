package fr.gaulupeau.apps.Poche.data;

import android.util.Log;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.QueueItemDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.AddLinkItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.AddOrUpdateAnnotationItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleChangeItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleDeleteItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsDeleteItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.DeleteAnnotationItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.SpecificItem;

import static fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem.*;

public class QueueHelper {

    private static final String TAG = QueueHelper.class.getSimpleName();

    private final QueueItemDao queueItemDao;

    public QueueHelper(DaoSession daoSession) {
        queueItemDao = daoSession.getQueueItemDao();
    }

    public List<QueueItem> getQueueItems() {
        return queueItemDao.queryBuilder()
                .orderAsc(QueueItemDao.Properties.QueueNumber)
                .list();
    }

    public void dequeueItems(List<QueueItem> items) {
        if (items.isEmpty()) return;

        // supposed to be called in a transaction, so separate operations are probably fine
        for (QueueItem item : items) {
            queueItemDao.delete(item);
        }
    }

    boolean changeArticle(int articleID, ArticleChangeType articleChangeType) {
        Log.d(TAG, String.format("changeArticle(%d, %s) started", articleID, articleChangeType));

        ArticleChangeItem existingChangeItem = null;

        for (QueueItem item : getQueuedItemsForArticle(articleID)) {
            switch (item.getAction()) {
                case ARTICLE_CHANGE:
                    existingChangeItem = item.asSpecificItem();
                    Log.d(TAG, "changeArticle() found existing change item");
                    break;

                case ARTICLE_DELETE:
                    Log.d(TAG, "changeArticle(): already in queue for Deleting; ignoring");
                    return false;
            }
        }

        boolean queueChanged = false;

        if (existingChangeItem != null) {
            EnumSet<ArticleChangeType> changes = existingChangeItem.getArticleChanges();
            Log.v(TAG, "changeArticle() existing changes: " + changes);

            if (!changes.contains(articleChangeType)) {
                Log.d(TAG, "changeArticle() adding the change to change set");

                changes.add(articleChangeType);
                existingChangeItem.setArticleChanges(changes);

                queueItemDao.update(existingChangeItem.genericItem());

                queueChanged = true;
            } else {
                Log.d(TAG, "changeArticle() change type is already queued: "
                        + articleChangeType);
            }
        } else {
            ArticleChangeItem item = new QueueItem(Action.ARTICLE_CHANGE)
                    .<ArticleChangeItem>asSpecificItem()
                    .setArticleId(articleID)
                    .setSingleArticleChange(articleChangeType);

            enqueue(item);

            queueChanged = true;
        }

        Log.d(TAG, "changeArticle() finished; queue changed: " + queueChanged);
        return queueChanged;
    }

    boolean deleteTagsFromArticle(int articleID, Collection<String> tags) {
        Log.d(TAG, String.format("deleteTagsFromArticle(%d, %s) started",
                articleID, tags.toString()));

        ArticleTagsDeleteItem queueItem = null;

        for (QueueItem item : getQueuedItemsForArticle(articleID)) {
            switch (item.getAction()) {
                case ARTICLE_TAGS_DELETE:
                    queueItem = item.asSpecificItem();
                    Log.d(TAG, "deleteTagsFromArticle() found existing tags delete item");
                    break;

                case ARTICLE_DELETE:
                    Log.d(TAG, "deleteTagsFromArticle(): " +
                            "article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        if (queueItem != null) {
            Set<String> existingDeletedTags = queueItem.getTagIds();
            Log.v(TAG, "deleteTagsFromArticle() existing deleted tags: " + existingDeletedTags);

            if (!existingDeletedTags.addAll(tags)) {
                Log.d(TAG, "deleteTagsFromArticle() no new tags to delete");
                return false;
            }

            queueItem.setTagIds(existingDeletedTags);
            queueItemDao.update(queueItem.genericItem());
        } else {
            ArticleTagsDeleteItem item = new QueueItem(Action.ARTICLE_TAGS_DELETE)
                    .<ArticleTagsDeleteItem>asSpecificItem()
                    .setArticleId(articleID)
                    .setTagIds(tags);

            enqueue(item);
        }

        Log.d(TAG, "deleteTagsFromArticle() finished");
        return true;
    }

    boolean addAnnotationToArticle(int articleId, long annotationId) {
        Log.d(TAG, String.format("addAnnotationToArticle(%d, %d) started", articleId, annotationId));

        for (QueueItem item : getQueuedItemsForArticle(articleId)) {
            switch (item.getAction()) {
                case ARTICLE_DELETE:
                    Log.d(TAG, "addAnnotationToArticle(): " +
                            "article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        AddOrUpdateAnnotationItem item = new QueueItem(Action.ANNOTATION_ADD)
                .<AddOrUpdateAnnotationItem>asSpecificItem()
                .setArticleId(articleId)
                .setLocalAnnotationId(annotationId);

        enqueue(item);

        Log.d(TAG, "addAnnotationToArticle() finished");
        return true;
    }

    boolean updateAnnotationOnArticle(int articleId, long annotationId) {
        Log.d(TAG, String.format("updateAnnotationOnArticle(%d, %d) started",
                articleId, annotationId));

        for (QueueItem item : getQueuedItemsForArticle(articleId)) {
            switch (item.getAction()) {
                case ARTICLE_DELETE:
                    Log.d(TAG, "updateAnnotationOnArticle():" +
                            " article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        AddOrUpdateAnnotationItem item = new QueueItem(Action.ANNOTATION_UPDATE)
                .<AddOrUpdateAnnotationItem>asSpecificItem()
                .setArticleId(articleId)
                .setLocalAnnotationId(annotationId);

        enqueue(item);

        Log.d(TAG, "updateAnnotationOnArticle() finished");
        return true;
    }

    boolean deleteAnnotationFromArticle(int articleId, int remoteAnnotationId) {
        Log.d(TAG, String.format("deleteAnnotationFromArticle(%d, %d) started",
                articleId, remoteAnnotationId));

        for (QueueItem item : getQueuedItemsForArticle(articleId)) {
            switch (item.getAction()) {
                case ANNOTATION_DELETE:
                    if (item.<DeleteAnnotationItem>asSpecificItem()
                            .getRemoteAnnotationId() == remoteAnnotationId) {
                        Log.d(TAG, "deleteAnnotationFromArticle() " +
                                "found existing annotation delete item");
                        return false;
                    }
                    break;

                case ARTICLE_DELETE:
                    Log.d(TAG, "deleteAnnotationFromArticle(): " +
                            "article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        DeleteAnnotationItem item = new QueueItem(Action.ANNOTATION_DELETE)
                .<DeleteAnnotationItem>asSpecificItem()
                .setArticleId(articleId)
                .setRemoteAnnotationId(remoteAnnotationId);

        enqueue(item);

        Log.d(TAG, "deleteAnnotationFromArticle() finished");
        return true;
    }

    boolean deleteArticle(int articleID) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        boolean ignore = false;
        List<QueueItem> itemsToCancel = new LinkedList<>();

        for (QueueItem item : getQueuedItemsForArticle(articleID)) {
            switch (item.getAction()) {
                case ARTICLE_CHANGE:
                case ANNOTATION_ADD:
                case ANNOTATION_UPDATE:
                case ANNOTATION_DELETE:
                    Log.d(TAG, "deleteArticle(): going to dequeue item with action id: " +
                            item.getAction());
                    itemsToCancel.add(item);
                    break;

                case ARTICLE_DELETE:
                    Log.d(TAG, "deleteArticle(): already in queue for Delete; ignoring");
                    ignore = true;
                    break;
            }
        }

        boolean queueChanged = false;

        if (!itemsToCancel.isEmpty()) {
            dequeueItems(itemsToCancel);
            queueChanged = true;
        }

        if (!ignore) {
            ArticleDeleteItem item = new QueueItem(Action.ARTICLE_DELETE)
                    .<ArticleDeleteItem>asSpecificItem()
                    .setArticleId(articleID);

            enqueue(item);

            queueChanged = true;
        }

        Log.d(TAG, "deleteArticle() finished");
        return queueChanged;
    }

    boolean addLink(String link, String origin) {
        Log.d(TAG, String.format("addLink(%s, %s) started", link, origin));

        boolean cancel = queueItemDao.queryBuilder()
                .where(QueueItemDao.Properties.Action.eq(Action.ADD_LINK.getId()),
                        QueueItemDao.Properties.Extra.eq(link)).count() != 0;

        if (!cancel) {
            AddLinkItem item = new QueueItem(Action.ADD_LINK)
                    .<AddLinkItem>asSpecificItem()
                    .setUrl(link)
                    .setOrigin(origin);

            enqueue(item);
        } else {
            Log.d(TAG, "addLink() the link is already in queue");
        }

        Log.d(TAG, "addLink() finished");
        return !cancel;
    }

    public long getQueueLength() {
        return queueItemDao.queryBuilder().count();
    }

    private void enqueue(SpecificItem item) {
        enqueue(item.genericItem());
    }

    private void enqueue(QueueItem item) {
        Log.d(TAG, "enqueue() started for item: " + item);

        item.setQueueNumber(getNewQueueNumber());

        queueItemDao.insertWithoutSettingPk(item);

        Log.d(TAG, "enqueue() finished");
    }

    private long getNewQueueNumber() {
        List<QueueItem> items = queueItemDao.queryBuilder()
                .orderDesc(QueueItemDao.Properties.QueueNumber)
                .limit(1)
                .list();

        if (items.isEmpty()) return 1;

        return items.get(0).getQueueNumber() + 1;
    }

    private List<QueueItem> getQueuedItemsForArticle(int articleID) {
        return queueItemDao.queryBuilder()
                .where(QueueItemDao.Properties.ArticleId.eq(articleID))
                .orderAsc(QueueItemDao.Properties.QueueNumber)
                .list();
    }

}
