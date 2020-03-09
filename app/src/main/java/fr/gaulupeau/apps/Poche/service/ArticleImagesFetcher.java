package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.query.QueryBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.StorageHelper;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesProgressEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesStartedEvent;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class ArticleImagesFetcher extends BaseWorker {

    private static final String TAG = ArticleImagesFetcher.class.getSimpleName();

    public ArticleImagesFetcher(Context context) {
        super(context);
    }

    public ActionResult fetch(ActionRequest actionRequest) {
        Log.d(TAG, "fetch() started");

        FetchImagesStartedEvent startEvent = new FetchImagesStartedEvent(actionRequest);
        postStickyEvent(startEvent);
        try {
            fetchImages(actionRequest);
        } finally {
            removeStickyEvent(startEvent);

            postEvent(new FetchImagesFinishedEvent(actionRequest));
        }

        Log.d(TAG, "fetch() finished");
        return null;
    }

    private void fetchImages(ActionRequest actionRequest) {
        Log.d(TAG, "fetchImages() started");

        if (!StorageHelper.isExternalStorageWritable()) {
            Log.w(TAG, "fetchImages() external storage is not writable");
            return;
        }

        ArticleDao articleDao = getDaoSession().getArticleDao();

        QueryBuilder<Article> queryBuilder = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ImagesDownloaded.eq(false))
                .orderAsc(ArticleDao.Properties.ArticleId);

        int totalNumber = (int) queryBuilder.count();
        Log.d(TAG, "fetchImages() total number: " + totalNumber);

        if (totalNumber == 0) {
            Log.d(TAG, "fetchImages() nothing to do");
            return;
        }

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        List<Integer> processedArticles = new ArrayList<>(totalNumber);
        Set<Integer> changedArticles = new HashSet<>(totalNumber);

        int dbQuerySize = 50;

        queryBuilder.limit(dbQuerySize);

        int offset = 0;

        while (true) {
            Log.d(TAG, "fetchImages() looping; offset: " + offset);

            List<Article> articleList = queryBuilder.list();

            if (articleList.isEmpty()) {
                Log.d(TAG, "fetchImages() no more articles");
                break;
            }

            int i = 0;
            for (Article article : articleList) {
                int index = offset + i++;
                Log.d(TAG, "fetchImages() processing " + index
                        + ". articleID: " + article.getArticleId());
                postEvent(new FetchImagesProgressEvent(actionRequest, index, totalNumber));

                String content = article.getContent();

                // append preview picture URL to content to fetch it too
                // should probably be handled separately
                if (!TextUtils.isEmpty(article.getPreviewPictureURL())) {
                    content = "<img src=\"" + article.getPreviewPictureURL() + "\"/>" + content;
                }

                if (ImageCacheUtils.cacheImages(article.getArticleId().longValue(), content)) {
                    changedArticles.add(article.getArticleId());
                }

                processedArticles.add(article.getArticleId());

                Log.d(TAG, "fetchImages() processing article " + article.getArticleId() + " finished");
            }

            offset += dbQuerySize;
            queryBuilder.offset(offset);
        }

        for (Integer articleID : processedArticles) {
            try {
                Article article = articleDao.queryBuilder()
                        .where(ArticleDao.Properties.ArticleId.eq(articleID))
                        .unique();

                if (article != null) {
                    article.setImagesDownloaded(true);
                    articleDao.update(article);

                    if (changedArticles.contains(articleID)) {
                        // maybe add another change type for unsuccessful articles?
                        event.addArticleChangeWithoutObject(article,
                                FeedsChangedEvent.ChangeType.FETCHED_IMAGES_CHANGED);
                    }
                }
            } catch (DaoException e) {
                Log.e(TAG, "fetchImages() Exception while updating articles", e);
            }
        }

        if (event.isAnythingChanged()) {
            postEvent(event);
        }

        Log.d(TAG, "fetchImages() finished");
    }

}
