package fr.gaulupeau.apps.Poche.data;

import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.QueueItemDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;

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
        if(items.isEmpty()) return;

        // supposed to be called in a transaction, so separate operations are probably fine
        for(QueueItem item: items) {
            queueItemDao.delete(item);
        }
    }

    public boolean changeArticle(int articleID, ArticleChangeType articleChangeType) {
        Log.d(TAG, String.format("changeArticle(%d, %s) started", articleID, articleChangeType));

        QueueItem existingChangeItem = null;

        for(QueueItem item: getQueuedItemsForArticle(articleID)) {
            switch(item.getAction()) {
                case ARTICLE_CHANGE:
                    existingChangeItem = item;
                    Log.d(TAG, "changeArticle() found existing change item");
                    break;

                case ARTICLE_DELETE:
                    Log.d(TAG, "changeArticle(): already in queue for Deleting; ignoring");
                    return false;
            }
        }

        boolean queueChanged = false;

        if(existingChangeItem != null) {
            Log.v(TAG, "changeArticle() existing changes: " + existingChangeItem.getExtra());

            EnumSet<ArticleChangeType> changes
                    = ArticleChangeType.stringToEnumSet(existingChangeItem.getExtra());

            if(!changes.contains(articleChangeType)) {
                Log.d(TAG, "changeArticle() adding the change to change set");

                changes.add(articleChangeType);
                existingChangeItem.setExtra(ArticleChangeType.enumSetToString(changes));

                queueItemDao.update(existingChangeItem);

                queueChanged = true;
            } else {
                Log.d(TAG, "changeArticle() change type is already queued: " + articleChangeType);
            }
        } else {
            enqueueArticleChange(articleID, articleChangeType);
            queueChanged = true;
        }

        Log.d(TAG, "changeArticle() finished; queue changed: " + queueChanged);
        return queueChanged;
    }

    public boolean deleteTagsFromArticle(int articleID, String tagsString) {
        Log.d(TAG, String.format("deleteTagsFromArticle(%d, %s) started", articleID, tagsString));

        QueueItem queueItem = null;

        for(QueueItem item: getQueuedItemsForArticle(articleID)) {
            switch(item.getAction()) {
                case ARTICLE_TAGS_DELETE:
                    queueItem = item;
                    Log.d(TAG, "deleteTagsFromArticle() found existing tags delete item");
                    break;

                case ARTICLE_DELETE:
                    Log.d(TAG, "deleteTagsFromArticle(): article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        Collection<String> tags = Arrays.asList(tagsString.split(DELETED_TAGS_DELIMITER));

        if(queueItem != null) {
            Log.v(TAG, "deleteTagsFromArticle() existing deleted tags: " + queueItem.getExtra());

            Set<String> existingDeletedTags = new HashSet<>(
                    Arrays.asList(queueItem.getExtra().split(DELETED_TAGS_DELIMITER)));

            int oldSize = existingDeletedTags.size();

            existingDeletedTags.addAll(tags);

            if(existingDeletedTags.size() == oldSize) {
                Log.d(TAG, "deleteTagsFromArticle() no new tags to delete");
                return false;
            }

            queueItem.setExtra(TextUtils.join(DELETED_TAGS_DELIMITER, existingDeletedTags));
            queueItemDao.update(queueItem);
        } else {
            enqueueDeleteTagsFromArticle(articleID, TextUtils.join(DELETED_TAGS_DELIMITER, tags));
        }

        Log.d(TAG, "deleteTagsFromArticle() finished");
        return true;
    }

    public boolean addAnnotationToArticle(int articleId, long annotationId) {
        Log.d(TAG, String.format("addAnnotationToArticle(%d, %d) started", articleId, annotationId));

        for (QueueItem item : getQueuedItemsForArticle(articleId)) {
            switch (item.getAction()) {
                case ARTICLE_DELETE:
                    Log.d(TAG, "addAnnotationToArticle(): article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        enqueueGenericAction(articleId, Action.ANNOTATION_ADD, String.valueOf(annotationId));

        Log.d(TAG, "addAnnotationToArticle() finished");
        return true;
    }

    public boolean updateAnnotationOnArticle(int articleId, long annotationId) {
            Log.d(TAG, String.format("updateAnnotationOnArticle(%d, %d) started", articleId, annotationId));

        for (QueueItem item : getQueuedItemsForArticle(articleId)) {
            switch (item.getAction()) {
                case ARTICLE_DELETE:
                    Log.d(TAG, "updateAnnotationOnArticle(): article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        enqueueGenericAction(articleId, Action.ANNOTATION_UPDATE, String.valueOf(annotationId));

        Log.d(TAG, "updateAnnotationOnArticle() finished");
        return true;
    }

    public boolean deleteAnnotationFromArticle(int articleId, int remoteAnnotationId) {
        Log.d(TAG, String.format("deleteAnnotationFromArticle(%d, %d) started", articleId, remoteAnnotationId));

        for (QueueItem item : getQueuedItemsForArticle(articleId)) {
            switch (item.getAction()) {
                case ANNOTATION_DELETE:
                    if (item.getExtra().equals(String.valueOf(remoteAnnotationId))) {
                        Log.d(TAG, "deleteAnnotationFromArticle() found existing annotation delete item");
                        return false;
                    }
                    break;

                case ARTICLE_DELETE:
                    Log.d(TAG, "deleteAnnotationFromArticle(): article is already in queue for Deleting; ignoring");
                    return false;
            }
        }

        enqueueGenericAction(articleId, Action.ANNOTATION_DELETE, String.valueOf(remoteAnnotationId));

        Log.d(TAG, "deleteAnnotationFromArticle() finished");
        return true;
    }

    public boolean deleteArticle(int articleID) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        boolean ignore = false;
        List<QueueItem> itemsToCancel = new LinkedList<>();

        for(QueueItem item: getQueuedItemsForArticle(articleID)) {
            switch(item.getAction()) {
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

        if(!itemsToCancel.isEmpty()) {
            dequeueItems(itemsToCancel);
            queueChanged = true;
        }

        if(!ignore) {
            enqueueDeleteArticle(articleID);
            queueChanged = true;
        }

        Log.d(TAG, "deleteArticle() finished");
        return queueChanged;
    }

    public boolean addLink(String link) {
        Log.d(TAG, String.format("addLink(\"%s\") started", link));

        boolean cancel = queueItemDao.queryBuilder()
                .where(QueueItemDao.Properties.Action.eq(Action.ADD_LINK.getId()),
                        QueueItemDao.Properties.Extra.eq(link)).count() != 0;

        if(!cancel) {
            enqueueAddLink(link);
        } else {
            Log.d(TAG, "addLink() the link is already in queue");
        }

        Log.d(TAG, "addLink() finished");
        return !cancel;
    }

    private void enqueueArticleChange(int articleId, ArticleChangeType articleChangeType) {
        enqueueGenericAction(articleId, Action.ARTICLE_CHANGE, articleChangeType.name());
    }

    private void enqueueDeleteTagsFromArticle(int articleId, String tags) {
        enqueueGenericAction(articleId, Action.ARTICLE_TAGS_DELETE, tags);
    }

    private void enqueueGenericAction(int articleId, QueueItem.Action action, String extra) {
        Log.d(TAG, String.format("enqueueGenericAction(%d, %s, %s) started", articleId, action, extra));

        QueueItem item = new QueueItem();
        item.setAction(action);
        item.setArticleId(articleId);
        item.setExtra(extra);

        enqueue(item);

        Log.d(TAG, "enqueueGenericAction() finished");
    }

    private void enqueueDeleteArticle(int articleID) {
        Log.d(TAG, String.format("enqueueDeleteArticle(%d) started", articleID));

        QueueItem item = new QueueItem();
        item.setAction(Action.ARTICLE_DELETE);
        item.setArticleId(articleID);

        enqueue(item);

        Log.d(TAG, "enqueueDeleteArticle() finished");
    }

    private void enqueueAddLink(String link) {
        Log.d(TAG, String.format("enqueueAddLink(%s) started", link));

        QueueItem item = new QueueItem();
        item.setAction(Action.ADD_LINK);
        item.setExtra(link);

        enqueue(item);

        Log.d(TAG, "enqueueAddLink() finished");
    }

    public long getQueueLength() {
        return queueItemDao.queryBuilder().count();
    }

    private void enqueue(QueueItem item) {
        item.setQueueNumber(getNewQueueNumber());

        queueItemDao.insertWithoutSettingPk(item);
    }

    private long getNewQueueNumber() {
        List<QueueItem> items = queueItemDao.queryBuilder()
                .orderDesc(QueueItemDao.Properties.QueueNumber)
                .limit(1)
                .list();

        if(items.isEmpty()) return 1;

        return items.get(0).getQueueNumber() + 1;
    }

    private List<QueueItem> getQueuedItemsForArticle(int articleID) {
        return queueItemDao.queryBuilder()
                .where(QueueItemDao.Properties.ArticleId.eq(articleID))
                .orderAsc(QueueItemDao.Properties.QueueNumber)
                .list();
    }

}
