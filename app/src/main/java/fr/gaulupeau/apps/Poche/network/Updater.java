package fr.gaulupeau.apps.Poche.network;

import android.text.TextUtils;
import android.util.Log;

import com.di72nn.stuff.wallabag.apiwrapper.WallabagService;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import com.di72nn.stuff.wallabag.apiwrapper.models.Articles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;

public class Updater {

    public enum UpdateType { FULL, FAST }

    private static final String TAG = Updater.class.getSimpleName();

    private final Settings settings;
    private final DaoSession daoSession;
    private final WallabagServiceWrapper wallabagServiceWrapper;

    public Updater(Settings settings, DaoSession daoSession,
                   WallabagServiceWrapper wallabagServiceWrapper) {
        this.settings = settings;
        this.daoSession = daoSession;
        this.wallabagServiceWrapper = wallabagServiceWrapper;
    }

    public ArticlesChangedEvent update(UpdateType updateType)
            throws UnsuccessfulResponseException, IOException {
        boolean clean = updateType != UpdateType.FAST;

        Log.i(TAG, "update() started; clean: " + clean);

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        long latestUpdatedItemTimestamp = 0;

        daoSession.getDatabase().beginTransaction();
        try {
            ArticleDao articleDao = daoSession.getArticleDao();

            if(clean) {
                Log.d(TAG, "update() deleting old articles");
                articleDao.deleteAll();

                event.setInvalidateAll(true);
            }

            latestUpdatedItemTimestamp = settings.getLatestUpdatedItemTimestamp();
            Log.v(TAG, "update() latestUpdatedItemTimestamp: " + latestUpdatedItemTimestamp);

            Log.d(TAG, "update() updating articles");
            latestUpdatedItemTimestamp = performUpdate(articleDao, event, clean, latestUpdatedItemTimestamp);
            Log.d(TAG, "update() articles updated");
            Log.v(TAG, "update() latestUpdatedItemTimestamp: " + latestUpdatedItemTimestamp);

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        settings.setLatestUpdatedItemTimestamp(latestUpdatedItemTimestamp);
        settings.setLatestUpdateRunTimestamp(System.currentTimeMillis());
        settings.setFirstSyncDone(true);

        Log.i(TAG, "update() finished");

        return event;
    }

    private long performUpdate(ArticleDao articleDao, ArticlesChangedEvent event,
                               boolean full, long latestUpdatedItemTimestamp)
            throws UnsuccessfulResponseException, IOException {
        Log.d(TAG, String.format("performUpdate(full: %s, latestUpdatedItemTimestamp: %d) started",
                full, latestUpdatedItemTimestamp));

        WallabagService.ArticlesQueryBuilder articlesQueryBuilder
                = wallabagServiceWrapper.getWallabagService().getArticlesBuilder();

        if(full) {
            articlesQueryBuilder
                    .sortCriterion(WallabagService.SortCriterion.CREATED)
                    .sortOrder(WallabagService.SortOrder.ASCENDING);

            latestUpdatedItemTimestamp = 0;
        } else {
            articlesQueryBuilder
                    .sortCriterion(WallabagService.SortCriterion.UPDATED)
                    .sortOrder(WallabagService.SortOrder.ASCENDING)
                    .since(latestUpdatedItemTimestamp / 1000); // convert milliseconds to seconds
        }

        WallabagService.ArticlesPageIterator pageIterator = articlesQueryBuilder
                .perPage(30).pageIterator();

        List<Article> articlesToInsert = null;

        Log.d(TAG, "performUpdate() starting to iterate though pages");
        while(pageIterator.hasNext()) {
            Articles articles = pageIterator.next();

            Log.d(TAG, String.format("performUpdate() page: %d/%d, total articles: %d",
                    articles.page, articles.pages, articles.total));

            if(articles.embedded.items.isEmpty()) {
                Log.d(TAG, "performUpdate() no items; skipping");
                continue;
            }

            if(articlesToInsert == null) {
                articlesToInsert = new ArrayList<>(30);
            } else {
                articlesToInsert.clear();
            }

            for(com.di72nn.stuff.wallabag.apiwrapper.models.Article apiArticle: articles.embedded.items) {
                int id = apiArticle.id;

                Article article = null;

                if(!full) {
                    article = articleDao.queryBuilder()
                            .where(ArticleDao.Properties.ArticleId.eq(id)).build().unique();
                }

                boolean existing = true;
                if(article == null) {
                    article = new Article(null);
                    existing = false;
                }

                // TODO: change detection?

                if(!existing || (article.getImagesDownloaded()
                        && !TextUtils.equals(article.getContent(), apiArticle.content))) {
                    article.setImagesDownloaded(false);
                }

                article.setTitle(apiArticle.title);
                article.setContent(apiArticle.content);
                article.setUrl(apiArticle.url);
                article.setArticleId(id);
                article.setUpdateDate(apiArticle.updatedAt);
                article.setArchive(apiArticle.archived);
                article.setFavorite(apiArticle.starred);

                if(apiArticle.updatedAt.getTime() > latestUpdatedItemTimestamp) {
                    latestUpdatedItemTimestamp = apiArticle.updatedAt.getTime();
                }

                if(event != null) {
                    ArticlesChangedEvent.ChangeType changeType = existing
                            ? ArticlesChangedEvent.ChangeType.UNSPECIFIED
                            : ArticlesChangedEvent.ChangeType.ADDED;

                    event.setInvalidateAll(true); // improve?
                    event.addChangedArticleID(article, changeType);
                }

                articlesToInsert.add(article);
            }

            if(!articlesToInsert.isEmpty()) {
                Log.v(TAG, "performUpdate() performing articleDao.insertInTx()");
                // TODO: check: is it faster to insert (without replace) new articles?
                articleDao.insertOrReplaceInTx(articlesToInsert, false);
                Log.v(TAG, "performUpdate() done articleDao.insertInTx()");
            }
        }

        return latestUpdatedItemTimestamp;
    }

}
