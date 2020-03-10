package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.util.Log;

import java.util.List;

import fr.gaulupeau.apps.Poche.data.dao.FtsDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.service.tasks.AddArticleTask;
import fr.gaulupeau.apps.Poche.service.tasks.ArticleChangeTask;
import fr.gaulupeau.apps.Poche.service.tasks.DeleteArticleTask;
import fr.gaulupeau.apps.Poche.service.tasks.UpdateArticleProgressTask;
import fr.gaulupeau.apps.Poche.service.workers.OperationsWorker;

import static fr.gaulupeau.apps.Poche.service.ServiceHelper.enqueueServiceTask;
import static fr.gaulupeau.apps.Poche.service.ServiceHelper.enqueueSimpleServiceTask;

public class OperationsHelper {

    private static final String TAG = OperationsHelper.class.getSimpleName();

    public static void addArticle(Context context, String url) {
        addArticle(context, url, null);
    }

    public static void addArticle(Context context, String url, String originUrl) {
        Log.d(TAG, "addArticle() started");

        enqueueSimpleServiceTask(context, new AddArticleTask(url, originUrl));
    }

    public static void archiveArticle(Context context, int articleId, boolean archive) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newArchiveTask(articleId, archive));
    }

    public static void favoriteArticle(Context context, int articleId, boolean favorite) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newFavoriteTask(articleId, favorite));
    }

    public static void changeArticleTitle(Context context, int articleId, String title) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newChangeTitleTask(articleId, title));
    }

    public static void setArticleProgress(Context context, int articleId, double progress) {
        enqueueSimpleServiceTask(context, new UpdateArticleProgressTask(articleId, progress));
    }

    public static void setArticleTags(Context context, int articleId, List<Tag> newTags,
                                      Runnable postCallCallback) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .setArticleTags(articleId, newTags), postCallCallback);
    }

    public static void addAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .addAnnotation(articleId, annotation), null);
    }

    public static void updateAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .updateAnnotation(articleId, annotation), null);
    }

    public static void deleteAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .deleteAnnotation(articleId, annotation), null);
    }

    public static void deleteArticle(Context context, int articleId) {
        enqueueSimpleServiceTask(context, new DeleteArticleTask(articleId));
    }

    public static void wipeDB(Settings settings) {
        DbUtils.runInNonExclusiveTx(DbConnection.getSession(), session -> {
            FtsDao.deleteAllArticles(session.getDatabase());
            session.getAnnotationRangeDao().deleteAll();
            session.getAnnotationDao().deleteAll();
            session.getArticleContentDao().deleteAll();
            session.getArticleDao().deleteAll();
            session.getTagDao().deleteAll();
            session.getArticleTagsJoinDao().deleteAll();
            session.getQueueItemDao().deleteAll();
        });

        settings.setLatestUpdatedItemTimestamp(0);
        settings.setLatestUpdateRunTimestamp(0);
        settings.setFirstSyncDone(false);

        EventHelper.notifyEverythingRemoved();
    }

}
