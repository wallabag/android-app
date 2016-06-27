package fr.gaulupeau.apps.Poche.data;

import android.util.Log;

import java.util.List;

import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.entity.QueueItem;
import fr.gaulupeau.apps.Poche.entity.QueueItemDao;

public class QueueHelper {

    public static final int QI_STATUS_QUEUED = 1;
    public static final int QI_STATUS_FAILED = 2;

    public static final int QI_ACTION_ADD_LINK = 1;
    public static final int QI_ACTION_ARCHIVE = 2;
    public static final int QI_ACTION_UNARCHIVE = 3;
    public static final int QI_ACTION_FAVORITE = 4;
    public static final int QI_ACTION_UNFAVORITE = 5;
    public static final int QI_ACTION_DELETE = 6;

    private static final String TAG = QueueHelper.class.getSimpleName();

    private DaoSession daoSession;
    private QueueItemDao queueItemDao;

    public QueueHelper(DaoSession daoSession) {
        this.daoSession = daoSession;
        this.queueItemDao = daoSession.getQueueItemDao();
    }

    public boolean archiveArticle(int articleID, boolean archive) {
        Log.d(TAG, String.format("archiveArticle(%d, %s) started", articleID, archive));

        boolean cancel = false;
        QueueItem itemToCancel = null;

        for(QueueItem item: getQueuedItemsForArticle(articleID)) {
            switch(item.getAction()) {
                case QI_ACTION_ARCHIVE:
                    cancel = true;
                    if(archive) {
                        Log.d(TAG, "archiveArticle(): already in queue for Archiving; ignoring");
                    } else {
                        Log.d(TAG, "archiveArticle(): in queue for Archive; canceled out");
                        itemToCancel = item;
                    }
                    break;

                case QI_ACTION_UNARCHIVE:
                    cancel = true;
                    if(!archive) {
                        Log.d(TAG, "archiveArticle(): already in queue for UnArchiving; ignoring");
                    } else {
                        Log.d(TAG, "archiveArticle(): in queue for Archive; canceled out");
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
            removeItemFromQueue(itemToCancel);
        }

        Log.d(TAG, "archiveArticle() finished");
        return !cancel;
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

    private void enqueue(QueueItem item) {
        item.setStatus(QI_STATUS_QUEUED);
        item.setQueueNumber(getNewQueueNumber());

        queueItemDao.insertWithoutSettingPk(item);
    }

    // probably not needed anymore, but I'll leave it just yet
    private long getNewQueueNumberForArticle(int articleID) {
        List<QueueItem> items = getQueuedItemsForArticle(articleID);
        long queueNumber;
        if(!items.isEmpty()) {
            queueNumber = items.get(items.size() - 1).getQueueNumber() + 1;
        } else {
            queueNumber = 1;
        }

        return queueNumber;
    }

    private long getNewQueueNumber() {
        List<QueueItem> items = queueItemDao.queryBuilder()
                .orderDesc(QueueItemDao.Properties.QueueNumber)
                .limit(1)
                .list();

        if(items.isEmpty()) return 1;

        return items.get(0).getQueueNumber() + 1;
    }

    private void removeItemFromQueue(QueueItem item) {
        queueItemDao.delete(item);
    }

    private List<QueueItem> getQueuedItemsForArticle(int articleID) {
        // TODO: check: status
        return queueItemDao.queryBuilder()
                .where(QueueItemDao.Properties.ArticleId.eq(articleID))
                .orderAsc(QueueItemDao.Properties.QueueNumber)
                .list();
    }

}
