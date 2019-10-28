package fr.gaulupeau.apps.Poche.network;

import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import wallabag.apiwrapper.ArticlesPageIterator;
import wallabag.apiwrapper.ArticlesQueryBuilder;
import wallabag.apiwrapper.BatchExistQueryBuilder;
import wallabag.apiwrapper.exceptions.NotFoundException;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import wallabag.apiwrapper.models.Articles;

import org.greenrobot.greendao.query.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;

import static fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent.ChangeType;

public class Updater {

    public enum UpdateType { FULL, FAST }

    public interface ProgressListener {
        void onProgress(int current, int total);
    }

    public interface UpdateListener extends ProgressListener {
        void onSuccess(long latestUpdatedItemTimestamp);
    }

    private static final String TAG = Updater.class.getSimpleName();

    private final DaoSession daoSession;
    private final WallabagServiceWrapper wallabagServiceWrapper;

    public Updater(DaoSession daoSession, WallabagServiceWrapper wallabagServiceWrapper) {
        this.daoSession = daoSession;
        this.wallabagServiceWrapper = wallabagServiceWrapper;
    }

    public ArticlesChangedEvent update(UpdateType updateType, long latestUpdatedItemTimestamp,
                                       UpdateListener updateListener)
            throws UnsuccessfulResponseException, IOException {
        boolean clean = updateType != UpdateType.FAST;

        Log.i(TAG, "update() started; clean: " + clean);

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        SQLiteDatabase sqliteDatabase = (SQLiteDatabase)daoSession.getDatabase().getRawDatabase();
        sqliteDatabase.beginTransactionNonExclusive();
        try {
            if(clean) {
                Log.d(TAG, "update() deleting old DB entries");
                daoSession.getArticleTagsJoinDao().deleteAll();
                daoSession.getArticleDao().deleteAll();
                daoSession.getTagDao().deleteAll();

                event.invalidateAll(ChangeType.DELETED);
            }

            Log.v(TAG, "update() latestUpdatedItemTimestamp: " + latestUpdatedItemTimestamp);

            Log.d(TAG, "update() updating articles");
            latestUpdatedItemTimestamp = performUpdate(
                    event, clean, latestUpdatedItemTimestamp, updateListener);
            Log.d(TAG, "update() articles updated");
            Log.v(TAG, "update() latestUpdatedItemTimestamp: " + latestUpdatedItemTimestamp);

            sqliteDatabase.setTransactionSuccessful();
        } finally {
            sqliteDatabase.endTransaction();
        }

        if(updateListener != null) updateListener.onSuccess(latestUpdatedItemTimestamp);

        Log.i(TAG, "update() finished");

        return event;
    }

    public ArticlesChangedEvent sweepDeletedArticles(ProgressListener progressListener)
            throws UnsuccessfulResponseException, IOException {
        Log.i(TAG, "sweepDeletedArticles() started");

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        performSweep(event, progressListener, false);

        Log.i(TAG, "sweepDeletedArticles() finished");

        return event;
    }

    private long performUpdate(ArticlesChangedEvent event, boolean full,
                               long latestUpdatedItemTimestamp, UpdateListener updateListener)
            throws UnsuccessfulResponseException, IOException {
        Log.d(TAG, String.format("performUpdate(full: %s, latestUpdatedItemTimestamp: %d) started",
                full, latestUpdatedItemTimestamp));

        ArticleDao articleDao = daoSession.getArticleDao();
        TagDao tagDao = daoSession.getTagDao();
        ArticleTagsJoinDao articleTagsJoinDao = daoSession.getArticleTagsJoinDao();

        List<Tag> tags;
        if(full) {
            List<wallabag.apiwrapper.models.Tag> apiTags
                    = wallabagServiceWrapper.getWallabagService().getTags();

            tags = new ArrayList<>(apiTags.size());

            for(wallabag.apiwrapper.models.Tag apiTag: apiTags) {
                tags.add(new Tag(null, apiTag.id, apiTag.label));
            }

            tagDao.insertInTx(tags);
        } else {
            tags = tagDao.queryBuilder().list();
        }

        Map<Integer, Tag> tagIdMap = new HashMap<>(tags.size());
        Map<String, Tag> tagLabelMap = new HashMap<>(tags.size());
        for(Tag tag: tags) {
            if(tag.getTagId() != null) {
                tagIdMap.put(tag.getTagId(), tag);
            } else {
                tagLabelMap.put(tag.getLabel(), tag);
            }
        }

        int perPage = 30;

        ArticlesQueryBuilder queryBuilder
                = wallabagServiceWrapper.getWallabagService()
                .getArticlesBuilder()
                .perPage(perPage);

        if(full) {
            queryBuilder
                    .sortCriterion(ArticlesQueryBuilder.SortCriterion.CREATED)
                    .sortOrder(ArticlesQueryBuilder.SortOrder.ASCENDING);

            latestUpdatedItemTimestamp = 0;
        } else {
            queryBuilder
                    .sortCriterion(ArticlesQueryBuilder.SortCriterion.UPDATED)
                    .sortOrder(ArticlesQueryBuilder.SortOrder.ASCENDING)
                    .since(latestUpdatedItemTimestamp);
        }

        List<Article> articlesToUpdate = new ArrayList<>();
        List<Article> articlesToInsert = new ArrayList<>();
        Set<Tag> tagsToUpdate = new HashSet<>();
        List<Tag> tagsToInsert = new ArrayList<>();
        Map<Article, List<Tag>> articleTagJoinsToRemove = new HashMap<>();
        Map<Article, List<Tag>> articleTagJoinsToInsert = new HashMap<>();

        Log.d(TAG, "performUpdate() starting to iterate though pages");
        for(ArticlesPageIterator pageIterator = queryBuilder.pageIterator(); pageIterator.hasNext();) {
            Articles articles = pageIterator.next();

            Log.d(TAG, String.format("performUpdate() page: %d/%d, total articles: %d",
                    articles.page, articles.pages, articles.total));

            if(updateListener != null) {
                updateListener.onProgress((articles.page - 1) * perPage, articles.total);
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

            for(wallabag.apiwrapper.models.Article apiArticle: articles.embedded.items) {
                int id = apiArticle.id;

                Article article = null;

                EnumSet<ChangeType> articleChanges = EnumSet.noneOf(ChangeType.class);

                if(!full) {
                    article = articleDao.queryBuilder()
                            .where(ArticleDao.Properties.ArticleId.eq(id)).build().unique();
                }

                boolean existing = true;
                if(article == null) {
                    existing = false;

                    article = new Article(null);
                    article.setArticleId(id);
                    article.setTitle(apiArticle.title);
                    article.setContent(apiArticle.content);
                    article.setDomain(apiArticle.domainName);
                    article.setUrl(apiArticle.url);
                    article.setOriginUrl(apiArticle.originUrl);
                    article.setEstimatedReadingTime(apiArticle.readingTime);
                    article.setLanguage(apiArticle.language);
                    article.setPreviewPictureURL(apiArticle.previewPicture);
                    article.setAuthors(formatAuthors(apiArticle.authors));
                    article.setCreationDate(apiArticle.createdAt);
                    article.setUpdateDate(apiArticle.updatedAt);
                    article.setPublishedAt(apiArticle.publishedAt);
                    article.setStarredAt(apiArticle.starredAt);
                    article.setIsPublic(apiArticle.isPublic);
                    article.setPublicUid(apiArticle.publicUid);
                    article.setArchive(apiArticle.archived);
                    article.setFavorite(apiArticle.starred);
                    article.setImagesDownloaded(false);

                    articleChanges.add(ChangeType.ADDED);
                }

                if(existing) {
                    if(!equalOrEmpty(article.getContent(), apiArticle.content)) {
                        article.setContent(apiArticle.content);
                        articleChanges.add(ChangeType.CONTENT_CHANGED);
                    }
                    if(!equalOrEmpty(article.getTitle(), apiArticle.title)) {
                        article.setTitle(apiArticle.title);
                        articleChanges.add(ChangeType.TITLE_CHANGED);
                    }
                    if(!equalOrEmpty(article.getDomain(), apiArticle.domainName)) {
                        article.setDomain(apiArticle.domainName);
                        articleChanges.add(ChangeType.DOMAIN_CHANGED);
                    }
                    if(!equalOrEmpty(article.getUrl(), apiArticle.url)) {
                        article.setUrl(apiArticle.url);
                        articleChanges.add(ChangeType.URL_CHANGED);
                    }
                    if(!equalOrEmpty(article.getOriginUrl(), apiArticle.originUrl)) {
                        article.setOriginUrl(apiArticle.originUrl);
                        articleChanges.add(ChangeType.ORIGIN_URL_CHANGED);
                    }
                    if(article.getEstimatedReadingTime() != apiArticle.readingTime) {
                        article.setEstimatedReadingTime(apiArticle.readingTime);
                        articleChanges.add(ChangeType.ESTIMATED_READING_TIME_CHANGED);
                    }
                    if(!equalOrEmpty(article.getLanguage(), apiArticle.language)) {
                        article.setLanguage(apiArticle.language);
                        articleChanges.add(ChangeType.LANGUAGE_CHANGED);
                    }
                    if(!equalOrEmpty(article.getPreviewPictureURL(), apiArticle.previewPicture)) {
                        article.setPreviewPictureURL(apiArticle.previewPicture);
                        articleChanges.add(ChangeType.PREVIEW_PICTURE_URL_CHANGED);
                    }
                    if(!equalOrEmpty(article.getAuthors(), formatAuthors(apiArticle.authors))) {
                        article.setAuthors(formatAuthors(apiArticle.authors));
                        articleChanges.add(ChangeType.AUTHORS_CHANGED);
                    }
                    if(article.getCreationDate().getTime() != apiArticle.createdAt.getTime()) {
                        article.setCreationDate(apiArticle.createdAt);
                        articleChanges.add(ChangeType.CREATED_DATE_CHANGED);
                    }
                    if(article.getUpdateDate().getTime() != apiArticle.updatedAt.getTime()) {
                        article.setUpdateDate(apiArticle.updatedAt);
                        articleChanges.add(ChangeType.UPDATED_DATE_CHANGED);
                    }
                    if(!Objects.equals(article.getPublishedAt(), apiArticle.publishedAt)) {
                        article.setPublishedAt(apiArticle.publishedAt);
                        articleChanges.add(ChangeType.PUBLISHED_AT_CHANGED);
                    }
                    if(!Objects.equals(article.getStarredAt(), apiArticle.starredAt)) {
                        article.setStarredAt(apiArticle.starredAt);
                        articleChanges.add(ChangeType.STARRED_AT_CHANGED);
                    }
                    if(!Objects.equals(article.getIsPublic(), apiArticle.isPublic)) {
                        article.setIsPublic(apiArticle.isPublic);
                        articleChanges.add(ChangeType.IS_PUBLIC_CHANGED);
                    }
                    if(!equalOrEmpty(article.getPublicUid(), apiArticle.publicUid)) {
                        article.setPublicUid(apiArticle.publicUid);
                        articleChanges.add(ChangeType.PUBLIC_UID_CHANGED);
                    }
                    if(article.getArchive() != apiArticle.archived) {
                        article.setArchive(apiArticle.archived);
                        articleChanges.add(apiArticle.archived
                                ? ChangeType.ARCHIVED
                                : ChangeType.UNARCHIVED);
                    }
                    if(article.getFavorite() != apiArticle.starred) {
                        article.setFavorite(apiArticle.starred);
                        articleChanges.add(apiArticle.starred
                                ? ChangeType.FAVORITED
                                : ChangeType.UNFAVORITED);
                    }

                    if(articleChanges.contains(ChangeType.CONTENT_CHANGED)
                            || articleChanges.contains(ChangeType.PREVIEW_PICTURE_URL_CHANGED)) {

                        if(article.getImagesDownloaded() != null && article.getImagesDownloaded()) {
                            articleChanges.add(ChangeType.FETCHED_IMAGES_CHANGED);
                        }

                        article.setImagesDownloaded(false);
                    }
                }

                fixArticleNullValues(article);

                List<Tag> articleTags;
                if(existing) {
                    articleTags = article.getTags();
                    List<Tag> tagJoinsToRemove = null;

                    for(Tag tag: articleTags) {
                        boolean found;
                        if(tag.getTagId() != null) {
                            found = findApiTagByID(tag.getTagId(), apiArticle.tags) != null;
                        } else {
                            found = findApiTagByLabel(tag.getLabel(), apiArticle.tags) != null;
                        }

                        if(!found) {
                            if(tagJoinsToRemove == null) tagJoinsToRemove = new ArrayList<>();

                            tagJoinsToRemove.add(tag);
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

                    for(wallabag.apiwrapper.models.Tag apiTag: apiArticle.tags) {
                        Tag tag = tagIdMap.get(apiTag.id);

                        if(tag == null) {
                            tag = tagLabelMap.get(apiTag.label);

                            if(tag == null) {
                                tag = new Tag(null, apiTag.id, apiTag.label);

                                tagIdMap.put(tag.getTagId(), tag);

                                tagsToInsert.add(tag);
                            } else {
                                tag.setTagId(apiTag.id);

                                tagIdMap.put(tag.getTagId(), tag);
                                tagLabelMap.remove(tag.getLabel());

                                tagsToUpdate.add(tag);
                            }
                        } else if(!TextUtils.equals(tag.getLabel(), apiTag.label)) {
                            Log.w(TAG, String.format("performUpdate() tag label mismatch: " +
                                    "tag ID: %s, local label: %s, remote label: %s",
                                    tag.getId(), tag.getLabel(), apiTag.label));

                            tag.setLabel(apiTag.label);

                            tagsToUpdate.add(tag);
                        }

                        if(!articleTags.contains(tag)) {
                            articleTags.add(tag);
                            tagJoinsToInsert.add(tag);
                        }
                    }

                    if(!tagJoinsToInsert.isEmpty()) {
                        articleTagJoinsToInsert.put(article, tagJoinsToInsert);
                    }
                }

                if(apiArticle.updatedAt.getTime() > latestUpdatedItemTimestamp) {
                    latestUpdatedItemTimestamp = apiArticle.updatedAt.getTime();
                }

                if(!articleChanges.isEmpty()) {
                    (existing ? articlesToUpdate : articlesToInsert).add(article);
                } else {
                    Log.d(TAG, "performUpdate() article wasn't changed");
                }

                if(!tagsToUpdate.isEmpty() || !tagsToInsert.isEmpty()
                        || !articleTagJoinsToRemove.isEmpty()
                        || !articleTagJoinsToInsert.isEmpty()) {
                    articleChanges.add(ChangeType.TAGS_CHANGED);
                }

                if(!articleChanges.isEmpty()) {
                    Log.d(TAG, "performUpdate() articleChanges: " + articleChanges);

                    if(event != null) {
                        event.addArticleChangeWithoutObject(article, articleChanges);
                    }
                }
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

            if(updateListener != null) {
                updateListener.onProgress(articles.page * perPage, articles.total);
            }
        }

        return latestUpdatedItemTimestamp;
    }

    private String formatAuthors(List<String> authorsList) {
        if (authorsList == null || authorsList.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (String author : authorsList) {
            if (sb.length() != 0) sb.append(", ");
            sb.append(author);
        }
        return sb.toString();
    }

    /**
     * Returns true if both arguments are equal or empty.
     * {@code null} and empty sequences are considered equal.
     *
     * @param s1 first char sequence
     * @param s2 second char sequence
     * @return true if arguments are considered equal
     */
    private boolean equalOrEmpty(CharSequence s1, CharSequence s2) {
        return (TextUtils.isEmpty(s1) && TextUtils.isEmpty(s2))
                || TextUtils.equals(s1, s2);
    }

    private void fixArticleNullValues(Article article) {
        if(article.getTitle() == null) article.setTitle("");
        if(article.getContent() == null) article.setContent("");
        if(article.getDomain() == null) article.setDomain("");
        if(article.getUrl() == null) article.setUrl("");
        if(article.getLanguage() == null) article.setLanguage("");
        if(article.getPreviewPictureURL() == null) article.setPreviewPictureURL("");
    }

    private wallabag.apiwrapper.models.Tag findApiTagByID(
            Integer id, List<wallabag.apiwrapper.models.Tag> tags) {
        for(wallabag.apiwrapper.models.Tag tag: tags) {
            if(id.equals(tag.id)) return tag;
        }

        return null;
    }

    private wallabag.apiwrapper.models.Tag findApiTagByLabel(
            String label, List<wallabag.apiwrapper.models.Tag> tags) {
        for(wallabag.apiwrapper.models.Tag tag: tags) {
            if(TextUtils.equals(tag.label, label)) return tag;
        }

        return null;
    }

    private void performSweep(ArticlesChangedEvent event, ProgressListener progressListener,
                              boolean force)
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
            Log.d(TAG, "performSweep() local number is not greater than remote");

            if(!force) {
                Log.d(TAG, "performSweep() aborting sweep");
                return;
            }
        }

        int dbQuerySize = 50;

        QueryBuilder<Article> queryBuilder = articleDao.queryBuilder()
                .orderDesc(ArticleDao.Properties.ArticleId).limit(dbQuerySize);

        List<Long> articlesToDelete = new ArrayList<>();

        LinkedList<Article> articleQueue = new LinkedList<>();
        List<Article> addedArticles = new ArrayList<>();
        BatchExistQueryBuilder existQueryBuilder = null;

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

                            event.addArticleChangeWithoutObject(a, ChangeType.DELETED);
                        }
                    }
                }

                addedArticles.clear();

                if(articlesToDelete.size() >= totalNumber - remoteTotal) {
                    Log.d(TAG, "performSweep() number of found deleted articles >= expected number");

                    if(!force) {
                        Log.d(TAG, "performSweep() finishing sweep");
                        break;
                    }
                }
            }
        }

        if(!articlesToDelete.isEmpty()) {
            Log.d(TAG, String.format("performSweep() deleting %d articles", articlesToDelete.size()));
            articleDao.deleteByKeyInTx(articlesToDelete);
            Log.d(TAG, "performSweep() articles deleted");
        }
    }

}
