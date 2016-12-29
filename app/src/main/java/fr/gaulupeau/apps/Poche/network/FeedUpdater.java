package fr.gaulupeau.apps.Poche.network;

import android.text.TextUtils;
import android.util.Log;

import com.di72nn.stuff.wallabag.apiwrapper.WallabagService;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import com.di72nn.stuff.wallabag.apiwrapper.models.Articles;

import org.greenrobot.greendao.query.WhereCondition;

import java.io.IOException;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;

public class FeedUpdater {

    public enum UpdateType { FULL, FAST }

    public enum FeedType {
        MAIN(R.string.feedName_unread),
        FAVORITE(R.string.feedName_favorites),
        ARCHIVE(R.string.feedName_archived);

        int nameResID;

        FeedType(int nameResID) {
            this.nameResID = nameResID;
        }

        public int getLocalizedResourceID() {
            return nameResID;
        }

    }

    private static final String TAG = FeedUpdater.class.getSimpleName();

    private final WallabagServiceWrapper wallabagServiceWrapper;

    public FeedUpdater(WallabagServiceWrapper wallabagServiceWrapper) {
        this.wallabagServiceWrapper = wallabagServiceWrapper;
    }

    public ArticlesChangedEvent update(FeedType feedType, UpdateType updateType)
            throws UnsuccessfulResponseException, IOException {
        ArticlesChangedEvent event = new ArticlesChangedEvent();

        if(feedType == null && updateType == null) {
            updateAllFeeds();

            event.setInvalidateAll(true);
        } else {
            if(feedType == null) {
                throw new IllegalArgumentException("If updateType is set, feedType must be set too");
            }
            if(updateType == null) {
                updateType = UpdateType.FULL;
            }

            updateInternal(feedType, updateType, event);
        }

        return event;
    }

    private void updateAllFeeds() throws UnsuccessfulResponseException, IOException {
        Log.i(TAG, "updateAllFeeds() started");

        ArticleDao articleDao = DbConnection.getSession().getArticleDao();

        Log.d(TAG, "updateAllFeeds() deleting old articles");
        articleDao.deleteAll();

        Log.d(TAG, "updateAllFeeds() updating MAIN feed");
        updateByFeed(articleDao, FeedType.MAIN, UpdateType.FULL, 0, null);

        Log.d(TAG, "updateAllFeeds() updating ARCHIVE feed");
        updateByFeed(articleDao, FeedType.ARCHIVE, UpdateType.FULL, 0, null);

        Log.d(TAG, "updateAllFeeds() updating FAVORITE feed");
        updateByFeed(articleDao, FeedType.FAVORITE, UpdateType.FAST, 0, null);

        Log.d(TAG, "updateAllFeeds() finished");
    }

    private void updateInternal(
            FeedType feedType, UpdateType updateType, ArticlesChangedEvent event)
            throws UnsuccessfulResponseException, IOException {
        Log.i(TAG, String.format("updateInternal(%s, %s) started", feedType, updateType));

        ArticleDao articleDao = DbConnection.getSession().getArticleDao();

        Integer latestID = null;
        if(feedType == FeedType.MAIN || feedType == FeedType.ARCHIVE) {
            WhereCondition cond = feedType == FeedType.MAIN
                    ? ArticleDao.Properties.Archive.notEq(true)
                    : ArticleDao.Properties.Archive.eq(true);
            List<Article> l = articleDao.queryBuilder().where(cond)
                    .orderDesc(ArticleDao.Properties.ArticleId).limit(1).list();

            if(!l.isEmpty()) {
                latestID = l.get(0).getArticleId();
            }
        }

        updateByFeed(articleDao, feedType, updateType, latestID, event);

        Log.i(TAG, String.format("updateInternal(%s, %s) finished", feedType, updateType));
    }

    private void updateByFeed(ArticleDao articleDao, FeedType feedType, UpdateType updateType,
                              Integer id, ArticlesChangedEvent event)
            throws UnsuccessfulResponseException, IOException {
        Log.d(TAG, "updateByFeed() started");

        processFeed(articleDao, feedType, updateType, id, event);

        Log.d(TAG, "updateByFeed() finished successfully");
    }

    private void processFeed(ArticleDao articleDao, FeedType feedType, UpdateType updateType,
                             Integer latestID, ArticlesChangedEvent event)
            throws UnsuccessfulResponseException, IOException {
        Log.d(TAG, "processFeed() latestID: " + latestID);

        WallabagService.ArticlesQueryBuilder articlesQueryBuilder
                = wallabagServiceWrapper.getWallabagService().getArticlesBuilder();

        switch(feedType) {
            case MAIN:
                articlesQueryBuilder.archive(false);
                break;

            case ARCHIVE:
                articlesQueryBuilder.archive(true);
                break;

            case FAVORITE:
                articlesQueryBuilder.starred(true);
                break;
        }

        WallabagService.ArticlesPageIterator pageIterator = articlesQueryBuilder
                .perPage(30).pageIterator();

        Log.d(TAG, "processFeed() starting to iterate though pages");
        while(pageIterator.hasNext()) {
            Articles articles = pageIterator.next();

            Log.d(TAG, String.format("processFeed() page: %d/%d, total articles: %d",
                    articles.page, articles.pages, articles.total));

            for(com.di72nn.stuff.wallabag.apiwrapper.models.Article apiArticle: articles.embedded.items) {
                int id = apiArticle.id;

                if(updateType == UpdateType.FAST && latestID != null && latestID >= id) {
                    Log.d(TAG, "processFeed(): update type fast, everything up to date");
                    break;
                }

                Article article = articleDao.queryBuilder()
                        .where(ArticleDao.Properties.ArticleId.eq(id)).build().unique();

                boolean existing = true;
                if(article == null) {
                    article = new Article(null);
                    existing = false;
                }

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

                if(event != null) {
                    ArticlesChangedEvent.ChangeType changeType = existing
                            ? ArticlesChangedEvent.ChangeType.UNSPECIFIED
                            : ArticlesChangedEvent.ChangeType.ADDED;

                    event.setChangedByFeedType(feedType);
                    event.addChangedArticle(article, changeType);
                }

                articleDao.insertOrReplace(article);
            }
        }
    }

    // TODO: check: do we still need it?
    private static String cleanString(String s) {
        if(s == null || s.isEmpty()) return s;

        s = s.replace("&Atilde;&copy;", "&eacute;");
        s = s.replace("&Atilde;&uml;", "&egrave;");
        s = s.replace("&Atilde;&ordf;", "&ecirc;");
        s = s.replace("&Atilde;&laquo;", "&euml;");
        s = s.replace("&Atilde;&nbsp;", "&agrave;");
        s = s.replace("&Atilde;&curren;", "&auml;");
        s = s.replace("&Atilde;&cent;", "&acirc;");
        s = s.replace("&Atilde;&sup1;", "&ugrave;");
        s = s.replace("&Atilde;&raquo;", "&ucirc;");
        s = s.replace("&Atilde;&frac14;", "&uuml;");
        s = s.replace("&Atilde;&acute;", "&ocirc;");
        s = s.replace("&Atilde;&para;", "&ouml;");
        s = s.replace("&Atilde;&reg;", "&icirc;");
        s = s.replace("&Atilde;&macr;", "&iuml;");
        s = s.replace("&Atilde;&sect;", "&ccedil;");

        s = s.trim();

        // Replace multiple whitespaces with single space
        s = s.replaceAll("\\s+", " ");

        return s;
    }

}
