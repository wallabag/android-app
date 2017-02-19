package fr.gaulupeau.apps.Poche.network;

import android.text.TextUtils;
import android.util.Log;

import com.di72nn.stuff.wallabag.apiwrapper.WallabagService;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.NotFoundException;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import com.di72nn.stuff.wallabag.apiwrapper.models.Articles;

import org.greenrobot.greendao.query.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;

public class Updater {

    public enum UpdateType { FULL, FAST }

    public interface ProgressListener {
        void onProgress(int current, int total);
    }

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

    public ArticlesChangedEvent update(UpdateType updateType, ProgressListener progressListener)
            throws UnsuccessfulResponseException, IOException {
        boolean clean = updateType != UpdateType.FAST;

        Log.i(TAG, "update() started; clean: " + clean);

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        long latestUpdatedItemTimestamp = 0;

        daoSession.getDatabase().beginTransaction();
        try {
            if(clean) {
                Log.d(TAG, "update() deleting old DB entries");
                daoSession.getArticleDao().deleteAll();
                daoSession.getTagDao().deleteAll();
                daoSession.getArticleTagsJoinDao().deleteAll();

                event.setInvalidateAll(true);
            }

            latestUpdatedItemTimestamp = settings.getLatestUpdatedItemTimestamp();
            Log.v(TAG, "update() latestUpdatedItemTimestamp: " + latestUpdatedItemTimestamp);

            Log.d(TAG, "update() updating articles");
            latestUpdatedItemTimestamp = performUpdate(
                    event, clean, latestUpdatedItemTimestamp, progressListener);
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

    public ArticlesChangedEvent sweepDeletedArticles(ProgressListener progressListener)
            throws UnsuccessfulResponseException, IOException {
        Log.i(TAG, "sweepDeletedArticles() started");

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        performSweep(event, progressListener);

        Log.i(TAG, "sweepDeletedArticles() finished");

        return event;
    }

    private long performUpdate(ArticlesChangedEvent event, boolean full,
                               long latestUpdatedItemTimestamp, ProgressListener progressListener)
            throws UnsuccessfulResponseException, IOException {
        Log.d(TAG, String.format("performUpdate(full: %s, latestUpdatedItemTimestamp: %d) started",
                full, latestUpdatedItemTimestamp));

        ArticleDao articleDao = daoSession.getArticleDao();
        TagDao tagDao = daoSession.getTagDao();
        ArticleTagsJoinDao articleTagsJoinDao = daoSession.getArticleTagsJoinDao();

        List<Tag> tags;
        if(full) {
            List<com.di72nn.stuff.wallabag.apiwrapper.models.Tag> apiTags
                    = wallabagServiceWrapper.getWallabagService().getTags();

            tags = new ArrayList<>(apiTags.size());

            for(com.di72nn.stuff.wallabag.apiwrapper.models.Tag apiTag: apiTags) {
                tags.add(new Tag(null, apiTag.id, apiTag.label));
            }

            tagDao.insertInTx(tags);
        } else {
            tags = tagDao.queryBuilder().list();
        }

        Map<String, Tag> tagMap = new HashMap<>(tags.size());
        for(Tag tag: tags) tagMap.put(tag.getLabel(), tag);

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

        int perPage = 30;

        WallabagService.ArticlesPageIterator pageIterator = articlesQueryBuilder
                .perPage(perPage).pageIterator();

        List<Article> articlesToUpdate = new ArrayList<>();
        List<Article> articlesToInsert = new ArrayList<>();
        Set<Tag> tagsToUpdate = new HashSet<>();
        List<Tag> tagsToInsert = new ArrayList<>();
        Map<Article, List<Tag>> articleTagJoinsToRemove = new HashMap<>();
        Map<Article, List<Tag>> articleTagJoinsToInsert = new HashMap<>();

        Log.d(TAG, "performUpdate() starting to iterate though pages");
        while(pageIterator.hasNext()) {
            Articles articles = pageIterator.next();

            Log.d(TAG, String.format("performUpdate() page: %d/%d, total articles: %d",
                    articles.page, articles.pages, articles.total));

            if(progressListener != null) {
                progressListener.onProgress((articles.page - 1) * perPage, articles.total);
            }

            if(articles.embedded.items.isEmpty()) {
                Log.d(TAG, "performUpdate() no items; skipping");
                continue;
            }

            articlesToUpdate.clear();
            articlesToInsert.clear();
            tagsToUpdate.clear();
            tagsToInsert.clear();
            articleTagJoinsToRemove.clear();
            articleTagJoinsToInsert.clear();

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
                article.setDomain(apiArticle.domainName);
                article.setUrl(apiArticle.url);
                article.setEstimatedReadingTime(apiArticle.readingTime);
                article.setLanguage(apiArticle.language);
                article.setPreviewPictureURL(apiArticle.previewPicture);
                article.setArticleId(id);
                article.setCreationDate(apiArticle.createdAt);
                article.setUpdateDate(apiArticle.updatedAt);
                article.setArchive(apiArticle.archived);
                article.setFavorite(apiArticle.starred);

                List<Tag> articleTags;
                if(existing) {
                    articleTags = article.getTags();
                    List<Tag> tagJoinsToRemove = null;

                    for(Tag tag: articleTags) {
                        com.di72nn.stuff.wallabag.apiwrapper.models.Tag apiTag
                                = findApiTagByLabel(tag.getLabel(), apiArticle.tags);
                        apiArticle.tags.remove(apiTag);

                        if(apiTag == null) {
                            if(tagJoinsToRemove == null) tagJoinsToRemove = new ArrayList<>();

                            tagJoinsToRemove.add(tag);
                        } else if(tag.getTagId() == null || tag.getTagId() != apiTag.id) {
                            tag.setTagId(apiTag.id);

                            tagsToUpdate.add(tag);
                        }
                    }

                    if(tagJoinsToRemove != null && !tagJoinsToRemove.isEmpty()) {
                        articleTags.removeAll(tagJoinsToRemove);
                        articleTagJoinsToRemove.put(article, tagJoinsToRemove);
                    }
                } else {
                    articleTags = new ArrayList<>(apiArticle.tags.size());
                    article.setTags(articleTags);
                }

                if(!apiArticle.tags.isEmpty()) {
                    List<Tag> tagJoinsToInsert = new ArrayList<>(apiArticle.tags.size());

                    for(com.di72nn.stuff.wallabag.apiwrapper.models.Tag apiTag: apiArticle.tags) {
                        Tag tag = tagMap.get(apiTag.label);

                        if(tag == null) {
                            tag = new Tag(null, apiTag.id, apiTag.label);
                            tagMap.put(tag.getLabel(), tag);

                            tagsToInsert.add(tag);
                        } else if(tag.getTagId() == null || tag.getTagId() != apiTag.id) {
                            tag.setTagId(apiTag.id);

                            tagsToUpdate.add(tag);
                        }

                        articleTags.add(tag);
                        tagJoinsToInsert.add(tag);
                    }

                    if(!tagJoinsToInsert.isEmpty()) {
                        articleTagJoinsToInsert.put(article, tagJoinsToInsert);
                    }
                }

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

                (existing ? articlesToUpdate : articlesToInsert).add(article);
            }

            if(!articlesToUpdate.isEmpty()) {
                Log.v(TAG, "performUpdate() performing articleDao.updateInTx()");
                articleDao.updateInTx(articlesToUpdate);
                Log.v(TAG, "performUpdate() done articleDao.updateInTx()");

                articlesToUpdate.clear();
            }

            if(!articlesToInsert.isEmpty()) {
                Log.v(TAG, "performUpdate() performing articleDao.insertInTx()");
                articleDao.insertInTx(articlesToInsert);
                Log.v(TAG, "performUpdate() done articleDao.insertInTx()");

                articlesToInsert.clear();
            }

            if(!tagsToUpdate.isEmpty()) {
                Log.v(TAG, "performUpdate() performing tagDao.updateInTx()");
                tagDao.updateInTx(tagsToUpdate);
                Log.v(TAG, "performUpdate() done tagDao.updateInTx()");

                tagsToUpdate.clear();
            }

            if(!tagsToInsert.isEmpty()) {
                Log.v(TAG, "performUpdate() performing tagDao.insertInTx()");
                tagDao.insertInTx(tagsToInsert);
                Log.v(TAG, "performUpdate() done tagDao.insertInTx()");

                tagsToInsert.clear();
            }

            if(!articleTagJoinsToRemove.isEmpty()) {
                List<ArticleTagsJoin> joins = new ArrayList<>();

                for(Map.Entry<Article, List<Tag>> entry: articleTagJoinsToRemove.entrySet()) {
                    List<Long> tagIDsToRemove = new ArrayList<>(entry.getValue().size());
                    for(Tag tag: entry.getValue()) tagIDsToRemove.add(tag.getId());

                    joins.addAll(articleTagsJoinDao.queryBuilder().where(
                            ArticleTagsJoinDao.Properties.ArticleId.eq(entry.getKey().getId()),
                            ArticleTagsJoinDao.Properties.TagId.in(tagIDsToRemove)).list());
                }

                articleTagJoinsToRemove.clear();

                Log.v(TAG, "performUpdate() performing articleTagsJoinDao.deleteInTx()");
                articleTagsJoinDao.deleteInTx(joins);
                Log.v(TAG, "performUpdate() done articleTagsJoinDao.deleteInTx()");
            }

            if(!articleTagJoinsToInsert.isEmpty()) {
                List<ArticleTagsJoin> joins = new ArrayList<>();

                for(Map.Entry<Article, List<Tag>> entry: articleTagJoinsToInsert.entrySet()) {
                    for(Tag tag: entry.getValue()) {
                        joins.add(new ArticleTagsJoin(null, entry.getKey().getId(), tag.getId()));
                    }
                }

                articleTagJoinsToInsert.clear();

                Log.v(TAG, "performUpdate() performing articleTagsJoinDao.insertInTx()");
                articleTagsJoinDao.insertInTx(joins);
                Log.v(TAG, "performUpdate() done articleTagsJoinDao.insertInTx()");
            }
        }

        return latestUpdatedItemTimestamp;
    }

    private com.di72nn.stuff.wallabag.apiwrapper.models.Tag findApiTagByLabel(
            String label, List<com.di72nn.stuff.wallabag.apiwrapper.models.Tag> tags) {
        for(com.di72nn.stuff.wallabag.apiwrapper.models.Tag tag: tags) {
            if(TextUtils.equals(tag.label, label)) return tag;
        }

        return null;
    }

    private void performSweep(ArticlesChangedEvent event, ProgressListener progressListener)
            throws UnsuccessfulResponseException, IOException {
        Log.d(TAG, "performSweep() started");

        ArticleDao articleDao = daoSession.getArticleDao();

        int totalNumber = (int)articleDao.queryBuilder().count();

        if(totalNumber == 0) {
            Log.d(TAG, "performSweep() no articles");
            return;
        }

        int remoteTotal = wallabagServiceWrapper.getWallabagService()
                .getArticlesBuilder().perPage(1).execute().total;

        Log.d(TAG, String.format("performSweep() local total: %d, remote total: %d",
                totalNumber, remoteTotal));

        if(totalNumber <= remoteTotal) {
            Log.d(TAG, "performSweep() local number is not greater than remote; aborting sweep");
            return;
        }

        int dbQuerySize = 50;

        QueryBuilder<Article> queryBuilder = articleDao.queryBuilder()
                .orderAsc(ArticleDao.Properties.ArticleId).limit(dbQuerySize);

        List<Long> articlesToDelete = new ArrayList<>();

        LinkedList<Article> articleQueue = new LinkedList<>();
        List<Article> addedArticles = new ArrayList<>();
        WallabagService.BatchExistQueryBuilder existQueryBuilder = null;

        int offset = 0;

        while(true) {
            if(articleQueue.isEmpty()) {
                Log.d(TAG, String.format("performSweep() %d/%d", offset, totalNumber));

                if(progressListener != null) {
                    progressListener.onProgress(offset, totalNumber);
                }

                articleQueue.addAll(queryBuilder.list());

                offset += dbQuerySize;
                queryBuilder.offset(offset);
            }

            if(articleQueue.isEmpty() && addedArticles.isEmpty()) break;

            boolean runQuery = true;

            while(!articleQueue.isEmpty()) {
                runQuery = false;

                Article article = articleQueue.element();

                String url = article.getUrl();
                if(TextUtils.isEmpty(url)) {
                    Log.w(TAG, "performSweep() empty or null URL on article with ArticleID: "
                            + article.getArticleId());

                    articleQueue.remove();
                    continue;
                }

                if(existQueryBuilder == null) {
                    existQueryBuilder = wallabagServiceWrapper.getWallabagService()
                            .getArticlesExistQueryBuilder(7950);
                }

                if(existQueryBuilder.addUrl(url)) {
                    addedArticles.add(article);
                    articleQueue.remove();
                } else if(addedArticles.isEmpty()) {
                    Log.e(TAG, "performSweep() can't check article with ArticleID: "
                            + article.getArticleId());

                    articleQueue.remove();
                } else {
                    Log.d(TAG, "performSweep() can't add more articles to query");

                    runQuery = true;
                    break;
                }
            }

            if(runQuery && existQueryBuilder != null) {
                Log.d(TAG, "performSweep() checking articles; number of articles: "
                        + addedArticles.size());

                Map<String, Boolean> articlesMap = existQueryBuilder.execute();
                existQueryBuilder.reset();

                for(Article a: addedArticles) {
                    Boolean value = articlesMap.get(a.getUrl());
                    Log.v(TAG, String.format("performSweep() articleID: %d, exists: %s",
                            a.getArticleId(), value));

                    if(value != null && !value) {
                        Log.v(TAG, String.format("performSweep() article not found remotely" +
                                "; articleID: %d, article URL: %s", a.getArticleId(), a.getUrl()));

                        Log.v(TAG, "performSweep() trying to find article by ID");
                        try {
                            // we could use `getArticle(int)`, but `getTags()` is lighter
                            wallabagServiceWrapper.getWallabagService().getTags(a.getArticleId());
                            Log.v(TAG, "performSweep() article found by ID");
                        } catch(NotFoundException nfe) {
                            Log.v(TAG, "performSweep() article not found by ID");
                            articlesToDelete.add(a.getId());
                        }
                    }
                }

                addedArticles.clear();
            }
        }

        if(!articlesToDelete.isEmpty()) {
            event.setInvalidateAll(true);

            Log.d(TAG, String.format("performSweep() deleting %d articles", articlesToDelete.size()));
            articleDao.deleteByKeyInTx(articlesToDelete);
            Log.d(TAG, "performSweep() articles deleted");
        }
    }

}
