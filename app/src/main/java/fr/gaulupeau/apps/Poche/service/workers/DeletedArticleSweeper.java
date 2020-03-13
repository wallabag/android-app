package fr.gaulupeau.apps.Poche.service.workers;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.greenrobot.greendao.query.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.SweepDeletedArticlesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.SweepDeletedArticlesProgressEvent;
import fr.gaulupeau.apps.Poche.events.SweepDeletedArticlesStartedEvent;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import wallabag.apiwrapper.ArticlesQueryBuilder;
import wallabag.apiwrapper.BatchExistQueryBuilder;
import wallabag.apiwrapper.WallabagService;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class DeletedArticleSweeper extends BaseNetworkWorker {

    private static final String TAG = DeletedArticleSweeper.class.getSimpleName();

    public DeletedArticleSweeper(Context context) {
        super(context);
    }

    public ActionResult sweep(ActionRequest actionRequest) {
        ActionResult result = null;

        SweepDeletedArticlesStartedEvent startEvent
                = new SweepDeletedArticlesStartedEvent(actionRequest);
        postStickyEvent(startEvent);

        try {
            result = sweepDeletedArticles(actionRequest);
        } finally {
            removeStickyEvent(startEvent);

            if (result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);
            postEvent(new SweepDeletedArticlesFinishedEvent(actionRequest, result));
        }

        return result;
    }

    private ActionResult sweepDeletedArticles(final ActionRequest actionRequest) {
        Log.d(TAG, "sweepDeletedArticles() started");

        ActionResult result = new ActionResult();
        ArticlesChangedEvent event = new ArticlesChangedEvent();

        if (WallabagConnection.isNetworkAvailable()) {
            try {
                ProgressListener progressListener = (current, total) ->
                        postEvent(new SweepDeletedArticlesProgressEvent(
                                actionRequest, current, total));

                performSweep(event, progressListener, false);
            } catch (UnsuccessfulResponseException | IOException e) {
                ActionResult r = processException(e, "sweepDeletedArticles()");
                result.updateWith(r);
            } catch (Exception e) {
                Log.e(TAG, "sweepDeletedArticles() exception", e);

                result.setErrorType(ActionResult.ErrorType.UNKNOWN);
                result.setMessage(e.toString());
                result.setException(e);
            }
        } else {
            result.setErrorType(ActionResult.ErrorType.NO_NETWORK);
        }

        if (event.isAnythingChanged()) {
            postEvent(event);
        }

        Log.d(TAG, "sweepDeletedArticles() finished");
        return result;
    }

    private interface ProgressListener {
        void onProgress(int current, int total);
    }

    private void performSweep(ArticlesChangedEvent event, ProgressListener progressListener,
                              boolean force)
            throws IncorrectConfigurationException, UnsuccessfulResponseException, IOException {
        Log.d(TAG, "performSweep() started");

        DaoSession daoSession = getDaoSession();

        ArticleDao articleDao = daoSession.getArticleDao();

        int totalNumber = (int) articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.isNotNull())
                .count();

        if (totalNumber == 0) {
            Log.d(TAG, "performSweep() no articles");
            return;
        }

        WallabagService wallabagService = getWallabagService();

        int remoteTotal = wallabagService
                .getArticlesBuilder()
                .perPage(1)
                .detailLevel(ArticlesQueryBuilder.DetailLevel.METADATA)
                .execute().total;

        Log.d(TAG, String.format("performSweep() local total: %d, remote total: %d",
                totalNumber, remoteTotal));

        if (totalNumber <= remoteTotal) {
            Log.d(TAG, "performSweep() local number is not greater than remote");

            if (!force) {
                Log.d(TAG, "performSweep() aborting sweep");
                return;
            }
        }

        int dbQuerySize = 50;

        QueryBuilder<Article> queryBuilder = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.isNotNull())
                .orderDesc(ArticleDao.Properties.ArticleId).limit(dbQuerySize);

        List<Long> articlesToDelete = new ArrayList<>();

        LinkedList<Article> articleQueue = new LinkedList<>();
        List<Article> addedArticles = new ArrayList<>();
        BatchExistQueryBuilder existQueryBuilder = null;

        int offset = 0;

        while (true) {
            if (articleQueue.isEmpty()) {
                Log.d(TAG, String.format("performSweep() %d/%d", offset, totalNumber));

                if (progressListener != null) {
                    progressListener.onProgress(offset, totalNumber);
                }

                articleQueue.addAll(queryBuilder.list());

                offset += dbQuerySize;
                queryBuilder.offset(offset);
            }

            if (articleQueue.isEmpty() && addedArticles.isEmpty()) break;

            boolean runQuery = true;

            while (!articleQueue.isEmpty()) {
                runQuery = false;

                Article article = articleQueue.element();

                String url = article.getUrl();
                if (TextUtils.isEmpty(url)) {
                    Log.w(TAG, "performSweep() empty or null URL on article with ArticleID: "
                            + article.getArticleId());

                    articleQueue.remove();
                    continue;
                }

                if (existQueryBuilder == null) {
                    existQueryBuilder = wallabagService
                            .getArticlesExistQueryBuilder(7950);
                }

                if (existQueryBuilder.addUrl(url)) {
                    addedArticles.add(article);
                    articleQueue.remove();
                } else if (addedArticles.isEmpty()) {
                    Log.e(TAG, "performSweep() can't check article with ArticleID: "
                            + article.getArticleId());

                    articleQueue.remove();
                } else {
                    Log.d(TAG, "performSweep() can't add more articles to query");

                    runQuery = true;
                    break;
                }
            }

            if (runQuery && existQueryBuilder != null) {
                Log.d(TAG, "performSweep() checking articles; number of articles: "
                        + addedArticles.size());

                Map<String, Boolean> articlesMap = existQueryBuilder.execute();
                existQueryBuilder.reset();

                for (Article a : addedArticles) {
                    Boolean value = articlesMap.get(a.getUrl());
                    Log.v(TAG, String.format("performSweep() articleID: %d, exists: %s",
                            a.getArticleId(), value));

                    if (value != null && !value) {
                        Log.v(TAG, String.format("performSweep() article not found remotely" +
                                "; articleID: %d, article URL: %s", a.getArticleId(), a.getUrl()));

                        Log.v(TAG, "performSweep() trying to find article by ID");

                        // we could use `getArticle(int)`, but `getTags()` is lighter
                        if (wallabagService.getTags(a.getArticleId()) != null) {
                            Log.v(TAG, "performSweep() article found by ID");
                        } else {
                            Log.v(TAG, "performSweep() article not found by ID");

                            articlesToDelete.add(a.getId());

                            event.addArticleChangeWithoutObject(a, FeedsChangedEvent.ChangeType.DELETED);
                        }
                    }
                }

                addedArticles.clear();

                if (articlesToDelete.size() >= totalNumber - remoteTotal) {
                    Log.d(TAG, "performSweep() number of found deleted articles >= expected number");

                    if (!force) {
                        Log.d(TAG, "performSweep() finishing sweep");
                        break;
                    }
                }
            }
        }

        if (!articlesToDelete.isEmpty()) {
            Log.d(TAG, String.format("performSweep() deleting %d articles", articlesToDelete.size()));

            Log.d(TAG, "performSweep() deleting related entities");

            // delete related tag joins
            ArticleTagsJoin.getTagsJoinByArticleQueryBuilder(
                    articlesToDelete, daoSession.getArticleTagsJoinDao())
                    .buildDelete().executeDeleteWithoutDetachingEntities();

            Collection<Long> annotationIds = Annotation.getAnnotationIdsByArticleIds(
                    articlesToDelete, daoSession.getAnnotationDao());

            // delete ranges of related annotations
            AnnotationRange.getAnnotationRangesByAnnotationsQueryBuilder(
                    annotationIds, daoSession.getAnnotationRangeDao())
                    .buildDelete().executeDeleteWithoutDetachingEntities();

            // delete related annotations
            daoSession.getAnnotationDao().deleteByKeyInTx(annotationIds);

            Log.d(TAG, "performSweep() performing content delete");
            daoSession.getArticleContentDao().deleteByKeyInTx(articlesToDelete);
            Log.d(TAG, "performSweep() articles content deleted");

            Log.d(TAG, "performSweep() performing articles delete");
            articleDao.deleteByKeyInTx(articlesToDelete);
            Log.d(TAG, "performSweep() articles deleted");
        }

        Log.d(TAG, "performSweep() finished");
    }

}
