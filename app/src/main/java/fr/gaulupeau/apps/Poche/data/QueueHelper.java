package fr.gaulupeau.apps.Poche.data;

import android.util.Log;

import java.util.LinkedList;
import java.util.List;

import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.entity.QueueItem;
import fr.gaulupeau.apps.Poche.entity.QueueItemDao;

public class QueueHelper {

    public static final int QI_ACTION_ADD_LINK = 1;
    public static final int QI_ACTION_ARCHIVE = 2;
    public static final int QI_ACTION_UNARCHIVE = 3;
    public static final int QI_ACTION_FAVORITE = 4;
    public static final int QI_ACTION_UNFAVORITE = 5;
    public static final int QI_ACTION_DELETE = 6;

    private static final String TAG = QueueHelper.class.getSimpleName();

    private DaoSession daoSession;
    private QueueItemDao queueItemDao;

    // that's not a very good implementation
    private boolean queueChanged;

    public QueueHelper(DaoSession daoSession) {
        this.daoSession = daoSession;
        this.queueItemDao = daoSession.getQueueItemDao();
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

        queueChanged = true;
    }

    // TODO: reuse code in {,enqueue}{archive,favorite,delete}Article

    public boolean archiveArticle(int articleID, boolean archive) {
        Log.d(TAG, String.format("archiveArticle(%d, %s) started", articleID, archive));

        boolean cancel = false;
        QueueItem itemToCancel = null;

        for(QueueItem item: getQueuedItemsForArticle(articleID)) {
            switch(item.getAction()) {
                case QI_ACTION_ARCHIVE:
                    cancel = true;
                    if(archive) {
                        Log.d(TAG, "archiveArticle(): already in queue for Archive; ignoring");
                    } else {
                        Log.d(TAG, "archiveArticle(): in queue for Archive; canceled out");
                        itemToCancel = item;
                    }
                    break;

                case QI_ACTION_UNARCHIVE:
                    cancel = true;
                    if(!archive) {
                        Log.d(TAG, "archiveArticle(): already in queue for UnArchive; ignoring");
                    } else {
                        Log.d(TAG, "archiveArticle(): in queue for UnArchive; canceled out");
                        itemToCancel = item;
                    }
                    break;

                case QI_ACTION_DELETE:
                    cancel = true;
                    Log.d(TAG, "archiveArticle(): already in queue for Deleting; ignoring");
                    break;
            }
        }

        if(itemToCancel != null) {
            dequeueItem(itemToCancel);
        }

        Log.d(TAG, "archiveArticle() finished");
        return !cancel;
    }

    public boolean favoriteArticle(int articleID, boolean favorite) {
        Log.d(TAG, String.format("favoriteArticle(%d, %s) started", articleID, favorite));

        boolean cancel = false;
        QueueItem itemToCancel = null;

        for(QueueItem item: getQueuedItemsForArticle(articleID)) {
            switch(item.getAction()) {
                case QI_ACTION_FAVORITE:
                    cancel = true;
                    if(favorite) {
                        Log.d(TAG, "favoriteArticle(): already in queue for Favorite; ignoring");
                    } else {
                        Log.d(TAG, "favoriteArticle(): in queue for Favorite; canceled out");
                        itemToCancel = item;
                    }
                    break;

                case QI_ACTION_UNFAVORITE:
                    cancel = true;
                    if(!favorite) {
                        Log.d(TAG, "favoriteArticle(): already in queue for UnFavorite; ignoring");
                    } else {
                        Log.d(TAG, "favoriteArticle(): in queue for UnFavorite; canceled out");
                        itemToCancel = item;
                    }
                    break;

                case QI_ACTION_DELETE:
                    cancel = true;
                    Log.d(TAG, "favoriteArticle(): already in queue for Delete; ignoring");
                    break;
            }
        }

        if(itemToCancel != null) {
            dequeueItem(itemToCancel);
        }

        Log.d(TAG, "favoriteArticle() finished");
        return !cancel;
    }

    public boolean deleteArticle(int articleID) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        boolean cancel = false;
        List<QueueItem> itemsToCancel = new LinkedList<>();

        for(QueueItem item: getQueuedItemsForArticle(articleID)) {
            switch(item.getAction()) {
                case QI_ACTION_ARCHIVE:
                case QI_ACTION_UNARCHIVE:
                case QI_ACTION_FAVORITE:
                case QI_ACTION_UNFAVORITE:
                    Log.d(TAG, "deleteArticle(): going to dequeue item with action id: " +
                            item.getAction());
                    itemsToCancel.add(item);
                    break;

                case QI_ACTION_DELETE:
                    cancel = true;
                    Log.d(TAG, "deleteArticle(): already in queue for Delete; ignoring");
                    break;
            }
        }

        if(!itemsToCancel.isEmpty()) {
            dequeueItems(itemsToCancel);
        }

        Log.d(TAG, "deleteArticle() finished");
        return !cancel;
    }

    public boolean addLink(String link) {
        Log.d(TAG, String.format("deleteArticle(\"%s\") started", link));

        long count = queueItemDao.queryBuilder()
                .where(QueueItemDao.Properties.Action.eq(QI_ACTION_ADD_LINK),
                        QueueItemDao.Properties.Extra.eq(link)).count();

        Log.d(TAG, "deleteArticle() finished");
        return count == 0;
    }

    // dumb insert, all the checks are done in archiveArticle()
    public void enqueueArchiveArticle(int articleID, boolean archive) {
        Log.d(TAG, String.format("enqueueArchiveArticle(%d, %s) started", articleID, archive));

        QueueItem item = new QueueItem();
        item.setAction(archive ? QI_ACTION_ARCHIVE : QI_ACTION_UNARCHIVE);
        item.setArticleId(articleID);

        enqueue(item);

        Log.d(TAG, "enqueueArchiveArticle() finished");
    }

    // dumb insert, all the checks are done in favoriteArticle()
    public void enqueueFavoriteArticle(int articleID, boolean archive) {
        Log.d(TAG, String.format("enqueueFavoriteArticle(%d, %s) started", articleID, archive));

        QueueItem item = new QueueItem();
        item.setAction(archive ? QI_ACTION_FAVORITE : QI_ACTION_UNFAVORITE);
        item.setArticleId(articleID);

        enqueue(item);

        Log.d(TAG, "enqueueFavoriteArticle() finished");
    }

    // dumb insert, all the checks are done in deleteArticle()
    public void enqueueDeleteArticle(int articleID) {
        Log.d(TAG, String.format("enqueueDeleteArticle(%d) started", articleID));

        QueueItem item = new QueueItem();
        item.setAction(QI_ACTION_DELETE);
        item.setArticleId(articleID);

        enqueue(item);

        Log.d(TAG, "enqueueDeleteArticle() finished");
    }

    // dumb insert, all the checks are done in addLink()
    public void enqueueAddLink(String link) {
        Log.d(TAG, String.format("enqueueAddLink(%s) started", link));

        QueueItem item = new QueueItem();
        item.setAction(QI_ACTION_ADD_LINK);
        item.setExtra(link);

        enqueue(item);

        Log.d(TAG, "enqueueAddLink() finished");
    }

    public boolean isQueueChanged() {
        return queueChanged;
    }

    public long getQueueLength() {
        return queueItemDao.queryBuilder().count();
    }

    private void enqueue(QueueItem item) {
        item.setQueueNumber(getNewQueueNumber());

        queueItemDao.insertWithoutSettingPk(item);

        queueChanged = true;
    }

    private long getNewQueueNumber() {
        List<QueueItem> items = queueItemDao.queryBuilder()
                .orderDesc(QueueItemDao.Properties.QueueNumber)
                .limit(1)
                .list();

        if(items.isEmpty()) return 1;

        return items.get(0).getQueueNumber() + 1;
    }

    private void dequeueItem(QueueItem item) {
        queueItemDao.delete(item);

        queueChanged = true;
    }

    private List<QueueItem> getQueuedItemsForArticle(int articleID) {
        return queueItemDao.queryBuilder()
                .where(QueueItemDao.Properties.ArticleId.eq(articleID))
                .orderAsc(QueueItemDao.Properties.QueueNumber)
                .list();
    }

}
