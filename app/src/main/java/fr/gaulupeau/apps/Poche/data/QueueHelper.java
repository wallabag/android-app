package fr.gaulupeau.apps.Poche.data;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public static class QueueState {

        private List<QueueItem> queueItems;

        private Set<QueueItem> negatedItems;
        private Set<QueueItem> updated;

        private List<QueueItem> completed;

        private QueueState(List<QueueItem> queueItems,
                           Set<QueueItem> negatedItems,
                           Set<QueueItem> updated) {
            this.queueItems = queueItems;
            this.negatedItems = negatedItems;
            this.updated = updated;

            this.completed = new ArrayList<>(this.queueItems.size());
        }

        public List<QueueItem> getQueueItems() {
            return queueItems;
        }

        public void completed(QueueItem queueItem) {
            updated.remove(queueItem);
            completed.add(queueItem);
        }

        public boolean hasChanges() {
            return !completed.isEmpty() || !updated.isEmpty() || !negatedItems.isEmpty();
        }

        public int itemsLeft() {
            return queueItems.size() - completed.size();
        }

    }

    private static final String TAG = QueueHelper.class.getSimpleName();

    private final QueueItemDao queueItemDao;

    public QueueHelper(DaoSession daoSession) {
        queueItemDao = daoSession.getQueueItemDao();
    }

    public QueueState getQueueState() {
        List<QueueItem> queueItems = getQueueItems();

        if (queueItems.size() <= 1) {
            return new QueueState(queueItems, Collections.emptySet(), Collections.emptySet());
        }

        Set<Integer> deletedArticles = new HashSet<>();

        Map<Integer, QueueItem> lastArticleChangeItemMap = new HashMap<>();
        Map<Integer, EnumSet<QueueItem.ArticleChangeType>> articleChangesMap = new HashMap<>();

        Map<Integer, QueueItem> lastArticleDeletedTagsItemMap = new HashMap<>();
        Map<Integer, Set<String>> articleDeletedTagsMap = new HashMap<>();

        for (QueueItem item : queueItems) {
            Integer articleId = item.getArticleId();

            switch (item.getAction()) {
                case ARTICLE_DELETE:
                    deletedArticles.add(articleId);
                    break;

                case ARTICLE_CHANGE: {
                    if (!deletedArticles.contains(articleId)) {
                        EnumSet<QueueItem.ArticleChangeType> articleChanges
                                = articleChangesMap.get(articleId);

                        if (articleChanges == null) {
                            articleChanges = EnumSet.noneOf(QueueItem.ArticleChangeType.class);
                            articleChangesMap.put(articleId, articleChanges);
                        }

                        articleChanges.addAll(item.<ArticleChangeItem>asSpecificItem()
                                .getArticleChanges());

                        lastArticleChangeItemMap.put(articleId, item);
                    }
                    break;
                }

                case ARTICLE_TAGS_DELETE: {
                    if (!deletedArticles.contains(articleId)) {
                        Set<String> tags = articleDeletedTagsMap.get(articleId);

                        if (tags == null) {
                            tags = new HashSet<>();
                            articleDeletedTagsMap.put(articleId, tags);
                        }

                        tags.addAll(item.<ArticleTagsDeleteItem>asSpecificItem().getTagIds());

                        lastArticleDeletedTagsItemMap.put(articleId, item);
                    }
                    break;
                }
            }
        }

        EnumSet<QueueItem.Action> negatedByDeletion = EnumSet.of(
                QueueItem.Action.ARTICLE_CHANGE,
                QueueItem.Action.ARTICLE_TAGS_DELETE,
                QueueItem.Action.ANNOTATION_ADD,
                QueueItem.Action.ANNOTATION_UPDATE,
                QueueItem.Action.ANNOTATION_DELETE);

        Set<QueueItem> negatedItems = new HashSet<>();
        Set<QueueItem> updatedItems = new HashSet<>();

        for (QueueItem item : queueItems) {
            Integer articleId = item.getArticleId();

            if (negatedByDeletion.contains(item.getAction())
                    && deletedArticles.contains(articleId)) {
                negatedItems.add(item);
            } else if (QueueItem.Action.ARTICLE_CHANGE == item.getAction()) {
                if (item != lastArticleChangeItemMap.get(articleId)) {
                    negatedItems.add(item);
                } else {
                    ArticleChangeItem changeItem = item.asSpecificItem();
                    EnumSet<QueueItem.ArticleChangeType> changes = changeItem.getArticleChanges();
                    if (changes.addAll(articleChangesMap.get(articleId))) {
                        changeItem.setArticleChanges(changes);
                        updatedItems.add(item);
                    }
                }
            } else if (Action.ARTICLE_TAGS_DELETE == item.getAction()) {
                if (item != lastArticleDeletedTagsItemMap.get(articleId)) {
                    negatedItems.add(item);
                } else {
                    ArticleTagsDeleteItem tagItem = item.asSpecificItem();
                    Set<String> tags = tagItem.getTagIds();
                    if (tags.addAll(articleDeletedTagsMap.get(articleId))) {
                        tagItem.setTagIds(tags);
                        updatedItems.add(item);
                    }
                }
            }
        }

        queueItems.removeAll(negatedItems);

        return new QueueState(queueItems, negatedItems, updatedItems);
    }

    public void save(QueueState queueState) {
        dequeueItems(queueState.completed);

        for (QueueItem item : queueState.updated) {
            queueItemDao.update(item);
        }

        dequeueItems(queueState.negatedItems);
    }

    public List<QueueItem> getQueueItems() {
        return queueItemDao.queryBuilder()
                .orderAsc(QueueItemDao.Properties.QueueNumber)
                .list();
    }

    public void dequeueItems(Collection<QueueItem> items) {
        if (items.isEmpty()) return;

        // supposed to be called in a transaction, so separate operations are probably fine
        for (QueueItem item : items) {
            queueItemDao.delete(item);
        }
    }

    void changeArticle(int articleID, ArticleChangeType articleChangeType) {
        Log.d(TAG, String.format("changeArticle(%d, %s) started", articleID, articleChangeType));

        ArticleChangeItem item = new QueueItem(Action.ARTICLE_CHANGE)
                .<ArticleChangeItem>asSpecificItem()
                .setArticleId(articleID)
                .setSingleArticleChange(articleChangeType);

        enqueue(item);

        Log.d(TAG, "changeArticle() finished");
    }

    void deleteTagsFromArticle(int articleID, Collection<String> tags) {
        Log.d(TAG, String.format("deleteTagsFromArticle(%d, %s) started",
                articleID, tags.toString()));

        ArticleTagsDeleteItem item = new QueueItem(Action.ARTICLE_TAGS_DELETE)
                .<ArticleTagsDeleteItem>asSpecificItem()
                .setArticleId(articleID)
                .setTagIds(tags);

        enqueue(item);

        Log.d(TAG, "deleteTagsFromArticle() finished");
    }

    void addAnnotationToArticle(int articleId, long annotationId) {
        Log.d(TAG, String.format("addAnnotationToArticle(%d, %d) started", articleId, annotationId));

        AddOrUpdateAnnotationItem item = new QueueItem(Action.ANNOTATION_ADD)
                .<AddOrUpdateAnnotationItem>asSpecificItem()
                .setArticleId(articleId)
                .setLocalAnnotationId(annotationId);

        enqueue(item);

        Log.d(TAG, "addAnnotationToArticle() finished");
    }

    void updateAnnotationOnArticle(int articleId, long annotationId) {
        Log.d(TAG, String.format("updateAnnotationOnArticle(%d, %d) started",
                articleId, annotationId));

        AddOrUpdateAnnotationItem item = new QueueItem(Action.ANNOTATION_UPDATE)
                .<AddOrUpdateAnnotationItem>asSpecificItem()
                .setArticleId(articleId)
                .setLocalAnnotationId(annotationId);

        enqueue(item);

        Log.d(TAG, "updateAnnotationOnArticle() finished");
    }

    void deleteAnnotationFromArticle(int articleId, int remoteAnnotationId) {
        Log.d(TAG, String.format("deleteAnnotationFromArticle(%d, %d) started",
                articleId, remoteAnnotationId));

        DeleteAnnotationItem item = new QueueItem(Action.ANNOTATION_DELETE)
                .<DeleteAnnotationItem>asSpecificItem()
                .setArticleId(articleId)
                .setRemoteAnnotationId(remoteAnnotationId);

        enqueue(item);

        Log.d(TAG, "deleteAnnotationFromArticle() finished");
    }

    void deleteArticle(int articleID) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        ArticleDeleteItem item = new QueueItem(Action.ARTICLE_DELETE)
                .<ArticleDeleteItem>asSpecificItem()
                .setArticleId(articleID);

        enqueue(item);

        Log.d(TAG, "deleteArticle() finished");
    }

    void addLink(String link, String origin) {
        Log.d(TAG, String.format("addLink(%s, %s) started", link, origin));

        AddLinkItem item = new QueueItem(Action.ADD_LINK)
                .<AddLinkItem>asSpecificItem()
                .setUrl(link)
                .setOrigin(origin);

        enqueue(item);

        Log.d(TAG, "addLink() finished");
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

}
