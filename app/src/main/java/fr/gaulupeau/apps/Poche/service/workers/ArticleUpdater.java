package fr.gaulupeau.apps.Poche.service.workers;

import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.core.util.ObjectsCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.DbUtils;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationRangeDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleContentDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.FtsDao;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleContent;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import wallabag.apiwrapper.ArticlesPageIterator;
import wallabag.apiwrapper.ArticlesQueryBuilder;
import wallabag.apiwrapper.WallabagService;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import wallabag.apiwrapper.models.Articles;

import static fr.gaulupeau.apps.Poche.events.FeedsChangedEvent.ChangeType;
import static fr.gaulupeau.apps.Poche.utils.TextTools.equalOrEmpty;
import static fr.gaulupeau.apps.Poche.utils.TextTools.unescapeHtml;

public class ArticleUpdater {

    public enum UpdateType {FULL, FAST}

    public interface ProgressListener {
        void onProgress(int current, int total);
    }

    public interface UpdateListener extends ProgressListener {
        void onSuccess(long latestUpdatedItemTimestamp);
    }

    private static final String TAG = ArticleUpdater.class.getSimpleName();

    private final DaoSession daoSession;
    private final WallabagService wallabagService;

    private final ArticleDao articleDao;
    private final ArticleContentDao articleContentDao;
    private final TagDao tagDao;
    private final ArticleTagsJoinDao articleTagsJoinDao;
    private final AnnotationDao annotationDao;
    private final AnnotationRangeDao annotationRangeDao;

    private boolean tagMapsInitiated;
    private Map<Integer, Tag> tagIdMap;
    private Map<String, Tag> tagLabelMap;

    private List<Article> articlesToUpdate;
    private List<Article> articlesToInsert;
    private List<ArticleContent> articleContentToUpdate;
    private List<ArticleContent> articleContentToInsert;
    private Set<Tag> tagsToUpdate;
    private List<Tag> tagsToInsert;
    private Map<Article, List<Tag>> articleTagJoinsToRemove;
    private Map<Article, List<Tag>> articleTagJoinsToInsert;
    private List<Annotation> annotationsToUpdate;
    private List<Pair<Article, Annotation>> annotationsToInsert;
    private List<Annotation> annotationsToRemove;
    private List<Pair<Annotation, AnnotationRange>> annotationRangesToInsert;
    private List<AnnotationRange> annotationRangesToRemove;

    public ArticleUpdater(DaoSession daoSession, WallabagService wallabagService) {
        this.daoSession = daoSession;
        this.wallabagService = wallabagService;
        articleDao = this.daoSession.getArticleDao();
        articleContentDao = this.daoSession.getArticleContentDao();
        tagDao = this.daoSession.getTagDao();
        articleTagsJoinDao = this.daoSession.getArticleTagsJoinDao();
        annotationDao = this.daoSession.getAnnotationDao();
        annotationRangeDao = this.daoSession.getAnnotationRangeDao();
    }

    public ArticlesChangedEvent update(UpdateType updateType, long latestUpdatedItemTimestamp,
                                       UpdateListener updateListener)
            throws UnsuccessfulResponseException, IOException {
        boolean clean = updateType != UpdateType.FAST;

        Log.i(TAG, "update() started; clean: " + clean);

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        SQLiteDatabase sqliteDatabase = (SQLiteDatabase) daoSession.getDatabase().getRawDatabase();
        sqliteDatabase.beginTransactionNonExclusive();
        try {
            if (clean) {
                Log.d(TAG, "update() deleting old DB entries");
                FtsDao.deleteAllArticles(daoSession.getDatabase());
                daoSession.getAnnotationRangeDao().deleteAll();
                daoSession.getAnnotationDao().deleteAll();
                daoSession.getArticleTagsJoinDao().deleteAll();
                daoSession.getArticleContentDao().deleteAll();
                daoSession.getArticleDao().deleteAll();
                daoSession.getTagDao().deleteAll();

                event.invalidateAll(ChangeType.DELETED);
                event.invalidateAll(ChangeType.UNSPECIFIED);
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

        if (updateListener != null) updateListener.onSuccess(latestUpdatedItemTimestamp);

        Log.i(TAG, "update() finished");

        return event;
    }

    public void updateArticles(
            ArticlesChangedEvent event,
            Collection<wallabag.apiwrapper.models.Article> articles) {
        Log.i(TAG, "updateArticles() started");

        DbUtils.runInNonExclusiveTx(daoSession, (session)
                -> processArticles(event, false, articles));

        Log.i(TAG, "updateArticles() finished");
    }

    private long performUpdate(ArticlesChangedEvent event, boolean full,
                               long latestUpdatedItemTimestamp, UpdateListener updateListener)
            throws UnsuccessfulResponseException, IOException {
        Log.d(TAG, String.format("performUpdate(full: %s, latestUpdatedItemTimestamp: %d) started",
                full, latestUpdatedItemTimestamp));

        if (full) initTagMapsFromServer();

        int perPage = 30;

        ArticlesQueryBuilder queryBuilder = wallabagService
                .getArticlesBuilder()
                .perPage(perPage);

        if (full) {
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

        Log.d(TAG, "performUpdate() starting to iterate though pages");
        for (ArticlesPageIterator pageIterator = queryBuilder.pageIterator(); pageIterator.hasNext(); ) {
            Articles articles = pageIterator.next();

            Log.d(TAG, String.format("performUpdate() page: %d/%d, total articles: %d",
                    articles.page, articles.pages, articles.total));

            if (updateListener != null) {
                updateListener.onProgress((articles.page - 1) * perPage, articles.total);
            }

            if (articles.embedded.items.isEmpty()) {
                Log.d(TAG, "performUpdate() no items; skipping");
                continue;
            }

            long newTimestamp = processArticles(event, full, articles.embedded.items);

            if (newTimestamp > latestUpdatedItemTimestamp) {
                latestUpdatedItemTimestamp = newTimestamp;
            }

            if (updateListener != null) {
                updateListener.onProgress(articles.page * perPage, articles.total);
            }
        }

        return latestUpdatedItemTimestamp;
    }

    private void initTagMapsFromServer() throws IOException, UnsuccessfulResponseException {
        tagMapsInitiated = true;

        List<Tag> tags;
        List<wallabag.apiwrapper.models.Tag> apiTags = wallabagService.getTags();

        tags = new ArrayList<>(apiTags.size());

        for (wallabag.apiwrapper.models.Tag apiTag : apiTags) {
            tags.add(new Tag(null, apiTag.id, apiTag.label));
        }

        tagDao.insertInTx(tags);

        initTagMaps(tags);
    }

    private void initTagMapsLocally() {
        if (tagMapsInitiated) return;
        tagMapsInitiated = true;

        initTagMaps(tagDao.queryBuilder().list());
    }

    private void initTagMaps(List<Tag> tags) {
        tagIdMap = new HashMap<>(tags.size());
        tagLabelMap = new HashMap<>(tags.size());
        for (Tag tag : tags) {
            if (tag.getTagId() != null) {
                tagIdMap.put(tag.getTagId(), tag);
            } else {
                tagLabelMap.put(tag.getLabel(), tag);
            }
        }
    }

    private long processArticles(ArticlesChangedEvent event, boolean full,
                                 Iterable<wallabag.apiwrapper.models.Article> articles) {
        initCollections();

        long latestTimestamp = 0;

        for (wallabag.apiwrapper.models.Article apiArticle : articles) {
            processArticle(event, full, apiArticle);

            if (apiArticle.updatedAt.getTime() > latestTimestamp) {
                latestTimestamp = apiArticle.updatedAt.getTime();
            }
        }

        persistCollections();

        return latestTimestamp;
    }

    private void processArticle(ArticlesChangedEvent event, boolean full,
                                wallabag.apiwrapper.models.Article apiArticle) {
        int id = apiArticle.id;

        EnumSet<ChangeType> articleChanges = EnumSet.noneOf(ChangeType.class);

        Article article = null;

        if (!full) {
            article = articleDao.queryBuilder()
                    .where(ArticleDao.Properties.ArticleId.eq(id)).build().unique();
        }

        boolean existing = true;
        if (article == null) {
            existing = false;

            article = new Article(null);
            article.setArticleId(id);
            article.setTitle(unescapeHtml(apiArticle.title));
            article.setArticleContent(new ArticleContent(null, apiArticle.content));
            article.setDomain(apiArticle.domainName);
            article.setUrl(apiArticle.url);
            article.setGivenUrl(apiArticle.givenUrl);
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

        if (existing) {
            if (!equalOrEmpty(article.getContent(), apiArticle.content)
                    && !article.isContentTooBig()) {
                article.setContent(apiArticle.content);
                articleContentToUpdate.add(article.getArticleContent());
                articleChanges.add(ChangeType.CONTENT_CHANGED);
            }
            if (!equalOrEmpty(article.getTitle(), unescapeHtml(apiArticle.title))) {
                article.setTitle(unescapeHtml(apiArticle.title));
                articleChanges.add(ChangeType.TITLE_CHANGED);
            }
            if (!equalOrEmpty(article.getDomain(), apiArticle.domainName)) {
                article.setDomain(apiArticle.domainName);
                articleChanges.add(ChangeType.DOMAIN_CHANGED);
            }
            if (!equalOrEmpty(article.getUrl(), apiArticle.url)) {
                article.setUrl(apiArticle.url);
                articleChanges.add(ChangeType.URL_CHANGED);
            }
            if (!equalOrEmpty(article.getGivenUrl(), apiArticle.givenUrl)
                    && !TextUtils.isEmpty(apiArticle.givenUrl)) {
                article.setGivenUrl(apiArticle.givenUrl);
            }
            if (!equalOrEmpty(article.getOriginUrl(), apiArticle.originUrl)) {
                article.setOriginUrl(apiArticle.originUrl);
                articleChanges.add(ChangeType.ORIGIN_URL_CHANGED);
            }
            if (article.getEstimatedReadingTime() != apiArticle.readingTime) {
                article.setEstimatedReadingTime(apiArticle.readingTime);
                articleChanges.add(ChangeType.ESTIMATED_READING_TIME_CHANGED);
            }
            if (!equalOrEmpty(article.getLanguage(), apiArticle.language)) {
                article.setLanguage(apiArticle.language);
                articleChanges.add(ChangeType.LANGUAGE_CHANGED);
            }
            if (!equalOrEmpty(article.getPreviewPictureURL(), apiArticle.previewPicture)) {
                article.setPreviewPictureURL(apiArticle.previewPicture);
                articleChanges.add(ChangeType.PREVIEW_PICTURE_URL_CHANGED);
            }
            if (!equalOrEmpty(article.getAuthors(), formatAuthors(apiArticle.authors))) {
                article.setAuthors(formatAuthors(apiArticle.authors));
                articleChanges.add(ChangeType.AUTHORS_CHANGED);
            }
            if (article.getCreationDate().getTime() != apiArticle.createdAt.getTime()) {
                article.setCreationDate(apiArticle.createdAt);
                articleChanges.add(ChangeType.CREATED_DATE_CHANGED);
            }
            if (article.getUpdateDate().getTime() != apiArticle.updatedAt.getTime()) {
                article.setUpdateDate(apiArticle.updatedAt);
                articleChanges.add(ChangeType.UPDATED_DATE_CHANGED);
            }
            if (!ObjectsCompat.equals(article.getPublishedAt(), apiArticle.publishedAt)) {
                article.setPublishedAt(apiArticle.publishedAt);
                articleChanges.add(ChangeType.PUBLISHED_AT_CHANGED);
            }
            if (!ObjectsCompat.equals(article.getStarredAt(), apiArticle.starredAt)) {
                article.setStarredAt(apiArticle.starredAt);
                articleChanges.add(ChangeType.STARRED_AT_CHANGED);
            }
            if (!ObjectsCompat.equals(article.getIsPublic(), apiArticle.isPublic)) {
                article.setIsPublic(apiArticle.isPublic);
                articleChanges.add(ChangeType.IS_PUBLIC_CHANGED);
            }
            if (!equalOrEmpty(article.getPublicUid(), apiArticle.publicUid)) {
                article.setPublicUid(apiArticle.publicUid);
                articleChanges.add(ChangeType.PUBLIC_UID_CHANGED);
            }
            if (article.getArchive() != apiArticle.archived) {
                article.setArchive(apiArticle.archived);
                articleChanges.add(apiArticle.archived
                        ? ChangeType.ARCHIVED
                        : ChangeType.UNARCHIVED);
            }
            if (article.getFavorite() != apiArticle.starred) {
                article.setFavorite(apiArticle.starred);
                articleChanges.add(apiArticle.starred
                        ? ChangeType.FAVORITED
                        : ChangeType.UNFAVORITED);
            }

            if (articleChanges.contains(ChangeType.CONTENT_CHANGED)
                    || articleChanges.contains(ChangeType.PREVIEW_PICTURE_URL_CHANGED)) {

                if (article.getImagesDownloaded() != null && article.getImagesDownloaded()) {
                    articleChanges.add(ChangeType.FETCHED_IMAGES_CHANGED);
                }

                article.setImagesDownloaded(false);
            }
        }

        fixArticleNullValues(article);

        Pair<Boolean, Boolean> tagsChanges = processTags(apiArticle, article, existing);
        boolean tagsChangedGlobally = tagsChanges.first;

        if (tagsChanges.second) {
            articleChanges.add(ChangeType.TAG_SET_CHANGED);
        }

        if (apiArticle.annotations != null && processAnnotations(apiArticle, article, existing)) {
            articleChanges.add(ChangeType.ANNOTATIONS_CHANGED);
        }

        if (!articleChanges.isEmpty()) {
            (existing ? articlesToUpdate : articlesToInsert).add(article);
        } else {
            Log.d(TAG, "processArticle() article wasn't changed");
        }

        if (tagsChangedGlobally) {
            event.invalidateAll(ChangeType.TAGS_CHANGED_GLOBALLY);
        }

        if (!articleChanges.isEmpty()) {
            Log.d(TAG, "processArticle() articleChanges: " + articleChanges);

            if (event != null) {
                event.addArticleChangeWithoutObject(article, articleChanges);
            }
        }
    }

    private Pair<Boolean, Boolean> processTags(wallabag.apiwrapper.models.Article apiArticle,
                                               Article article, boolean existing) {
        initTagMapsLocally();

        boolean tagsChangedGlobally = false;
        boolean articleTagsChanged = false;

        List<Tag> articleTags;
        if (existing) {
            articleTags = article.getTags();
            List<Tag> notFoundTags = null;

            for (Tag tag : articleTags) {
                boolean found;
                if (tag.getTagId() != null) {
                    found = findApiTagByID(tag.getTagId(), apiArticle.tags) != null;
                } else {
                    found = findApiTagByLabel(tag.getLabel(), apiArticle.tags) != null;
                }

                if (!found) {
                    if (notFoundTags == null) notFoundTags = new ArrayList<>();

                    notFoundTags.add(tag);
                }
            }

            if (notFoundTags != null && !notFoundTags.isEmpty()) {
                articleTags.removeAll(notFoundTags);
                articleTagJoinsToRemove.put(article, notFoundTags);
                articleTagsChanged = true;
            }
        } else {
            articleTags = new ArrayList<>(apiArticle.tags.size());
            article.setTags(articleTags);
        }

        if (!apiArticle.tags.isEmpty()) {
            List<Tag> tagJoinsToInsert = new ArrayList<>(apiArticle.tags.size());

            for (wallabag.apiwrapper.models.Tag apiTag : apiArticle.tags) {
                Tag tag = tagIdMap.get(apiTag.id);

                if (tag != null) {
                    if (!TextUtils.equals(tag.getLabel(), apiTag.label)) {
                        Log.w(TAG, String.format("processTags() tag label mismatch: " +
                                        "tag ID: %s, local label: %s, remote label: %s",
                                tag.getId(), tag.getLabel(), apiTag.label));

                        tag.setLabel(apiTag.label);
                        tagsChangedGlobally = true;

                        tagsToUpdate.add(tag);
                    }
                } else {
                    tag = tagLabelMap.get(apiTag.label);

                    if (tag != null) {
                        tag.setTagId(apiTag.id);
                        tagsChangedGlobally = true;

                        tagIdMap.put(tag.getTagId(), tag);
                        tagLabelMap.remove(tag.getLabel());

                        tagsToUpdate.add(tag);
                    } else {
                        tag = new Tag(null, apiTag.id, apiTag.label);
                        tagsChangedGlobally = true;

                        tagIdMap.put(tag.getTagId(), tag);

                        tagsToInsert.add(tag);
                    }
                }

                if (!articleTags.contains(tag)) {
                    articleTags.add(tag);
                    tagJoinsToInsert.add(tag);
                    articleTagsChanged = true;
                }
            }

            if (!tagJoinsToInsert.isEmpty()) {
                articleTagJoinsToInsert.put(article, tagJoinsToInsert);
            }
        }

        if (tagsChangedGlobally) articleTagsChanged = true;

        return new Pair<>(tagsChangedGlobally, articleTagsChanged);
    }

    private boolean processAnnotations(wallabag.apiwrapper.models.Article apiArticle,
                                       Article article, boolean existing) {
        boolean annotationsChanged = false;

        Set<wallabag.apiwrapper.models.Annotation> processedApiAnnotations = new HashSet<>();
        Set<wallabag.apiwrapper.models.Annotation.Range> presentApiAnnotationRanges = new HashSet<>();

        if (existing) {
            List<Annotation> annotations = article.getAnnotations();
            List<Annotation> aToRemove = null;

            for (Annotation annotation : annotations) {
                wallabag.apiwrapper.models.Annotation apiAnnotation
                        = findApiAnnotation(annotation.getAnnotationId(), apiArticle.annotations);

                if (apiAnnotation == null) {
                    if (aToRemove == null) aToRemove = new ArrayList<>();
                    aToRemove.add(annotation);
                } else {
                    processedApiAnnotations.add(apiAnnotation);

                    boolean annotationChanged = false;

                    if (!equalOrEmpty(annotation.getText(), apiAnnotation.text)) {
                        annotation.setText(apiAnnotation.text);
                        annotationChanged = true;
                    }
                    if (!equalOrEmpty(annotation.getQuote(), apiAnnotation.quote)) {
                        annotation.setQuote(apiAnnotation.quote);
                        annotationChanged = true;
                    }
                    if (!ObjectsCompat.equals(annotation.getCreatedAt(), apiAnnotation.createdAt)) {
                        annotation.setCreatedAt(apiAnnotation.createdAt);
                        annotationChanged = true;
                    }
                    if (!ObjectsCompat.equals(annotation.getUpdatedAt(), apiAnnotation.updatedAt)) {
                        annotation.setUpdatedAt(apiAnnotation.updatedAt);
                        annotationChanged = true;
                    }
                    if (!equalOrEmpty(annotation.getAnnotatorSchemaVersion(), apiAnnotation.annotatorSchemaVersion)) {
                        annotation.setAnnotatorSchemaVersion(apiAnnotation.annotatorSchemaVersion);
                        annotationChanged = true;
                    }

                    if (annotationChanged) {
                        annotationsToUpdate.add(annotation);
                        annotationsChanged = true;
                    }

                    presentApiAnnotationRanges.clear();

                    List<AnnotationRange> rToRemove = null;
                    for (AnnotationRange range : annotation.getRanges()) {
                        wallabag.apiwrapper.models.Annotation.Range apiRange
                                = findApiAnnotationRange(range, apiAnnotation.ranges);
                        if (apiRange == null) {
                            if (rToRemove == null)
                                rToRemove = new ArrayList<>(annotation.getRanges().size());
                            rToRemove.add(range);
                        } else {
                            presentApiAnnotationRanges.add(apiRange);
                        }
                    }
                    for (wallabag.apiwrapper.models.Annotation.Range apiRange : apiAnnotation.ranges) {
                        if (presentApiAnnotationRanges.contains(apiRange)) continue;

                        AnnotationRange range = new AnnotationRange(null, annotation.getId(),
                                apiRange.start, apiRange.end, apiRange.startOffset, apiRange.endOffset);
                        annotation.getRanges().add(range);
                        annotationRangesToInsert.add(new Pair<>(null, range));
                        annotationsChanged = true;
                    }

                    if (rToRemove != null) {
                        annotationRangesToRemove.addAll(rToRemove);
                        annotation.getRanges().removeAll(rToRemove);
                        annotationsChanged = true;
                    }
                }
            }

            if (aToRemove != null) {
                for (Annotation annotation : aToRemove) {
                    annotationRangesToRemove.addAll(annotation.getRanges());
                }
                annotationsToRemove.addAll(aToRemove);
                annotations.removeAll(aToRemove);
                annotationsChanged = true;
            }
        } else {
            article.setAnnotations(new ArrayList<>(apiArticle.annotations.size()));
        }

        for (wallabag.apiwrapper.models.Annotation apiAnnotation : apiArticle.annotations) {
            if (processedApiAnnotations.contains(apiAnnotation)) continue;

            Annotation annotation = new Annotation(null, apiAnnotation.id,
                    article.getId(), apiAnnotation.text, apiAnnotation.quote,
                    apiAnnotation.createdAt, apiAnnotation.updatedAt,
                    apiAnnotation.annotatorSchemaVersion);

            annotationsToInsert.add(new Pair<>(existing ? null : article, annotation));
            article.getAnnotations().add(annotation);

            for (wallabag.apiwrapper.models.Annotation.Range apiRange : apiAnnotation.ranges) {
                AnnotationRange range = new AnnotationRange(null, null,
                        apiRange.start, apiRange.end, apiRange.startOffset, apiRange.endOffset);
                annotationRangesToInsert.add(new Pair<>(annotation, range));
            }

            annotationsChanged = true;
        }

        return annotationsChanged;
    }

    private void initCollections() {
        if (articlesToUpdate == null) {
            articlesToUpdate = new ArrayList<>();
            articlesToInsert = new ArrayList<>();
            articleContentToUpdate = new ArrayList<>();
            articleContentToInsert = new ArrayList<>();
            tagsToUpdate = new HashSet<>();
            tagsToInsert = new ArrayList<>();
            articleTagJoinsToRemove = new HashMap<>();
            articleTagJoinsToInsert = new HashMap<>();
            annotationsToUpdate = new ArrayList<>();
            annotationsToInsert = new ArrayList<>();
            annotationsToRemove = new ArrayList<>();
            annotationRangesToInsert = new ArrayList<>();
            annotationRangesToRemove = new ArrayList<>();
        } else {
            articlesToUpdate.clear();
            articlesToInsert.clear();
            articleContentToUpdate.clear();
            articleContentToInsert.clear();
            tagsToUpdate.clear();
            tagsToInsert.clear();
            articleTagJoinsToRemove.clear();
            articleTagJoinsToInsert.clear();
            annotationsToUpdate.clear();
            annotationsToInsert.clear();
            annotationsToRemove.clear();
            annotationRangesToInsert.clear();
            annotationRangesToRemove.clear();
        }
    }

    private void persistCollections() {
        if (!articlesToUpdate.isEmpty()) {
            Log.v(TAG, "persistCollections() performing articleDao.updateInTx()");
            articleDao.updateInTx(articlesToUpdate);
            Log.v(TAG, "persistCollections() done articleDao.updateInTx()");

            articlesToUpdate.clear();
        }

        if (!articleContentToUpdate.isEmpty()) {
            Log.v(TAG, "persistCollections() performing articleContentDao.updateInTx()");
            articleContentDao.updateInTx(articleContentToUpdate);
            Log.v(TAG, "persistCollections() done articleContentDao.updateInTx()");

            articleContentToUpdate.clear();
        }

        if (!articlesToInsert.isEmpty()) {
            Log.v(TAG, "persistCollections() performing articleDao.insertInTx()");
            articleDao.insertInTx(articlesToInsert);
            Log.v(TAG, "persistCollections() done articleDao.insertInTx()");

            for (Article article : articlesToInsert) {
                ArticleContent content = article.getArticleContent();
                content.setId(article.getId());
                articleContentToInsert.add(content);
            }

            articlesToInsert.clear();
        }

        if (!articleContentToInsert.isEmpty()) {
            Log.v(TAG, "persistCollections() performing articleContentDao.insertInTx()");
            articleContentDao.insertInTx(articleContentToInsert);
            Log.v(TAG, "persistCollections() done articleContentDao.insertInTx()");

            articleContentToInsert.clear();
        }

        if (!tagsToUpdate.isEmpty()) {
            Log.v(TAG, "persistCollections() performing tagDao.updateInTx()");
            tagDao.updateInTx(tagsToUpdate);
            Log.v(TAG, "persistCollections() done tagDao.updateInTx()");

            tagsToUpdate.clear();
        }

        if (!tagsToInsert.isEmpty()) {
            Log.v(TAG, "persistCollections() performing tagDao.insertInTx()");
            tagDao.insertInTx(tagsToInsert);
            Log.v(TAG, "persistCollections() done tagDao.insertInTx()");

            tagsToInsert.clear();
        }

        if (!articleTagJoinsToRemove.isEmpty()) {
            List<ArticleTagsJoin> joins = new ArrayList<>();

            for (Map.Entry<Article, List<Tag>> entry : articleTagJoinsToRemove.entrySet()) {
                List<Long> tagIDsToRemove = new ArrayList<>(entry.getValue().size());
                for (Tag tag : entry.getValue()) tagIDsToRemove.add(tag.getId());

                joins.addAll(articleTagsJoinDao.queryBuilder().where(
                        ArticleTagsJoinDao.Properties.ArticleId.eq(entry.getKey().getId()),
                        ArticleTagsJoinDao.Properties.TagId.in(tagIDsToRemove)).list());
            }

            articleTagJoinsToRemove.clear();

            Log.v(TAG, "persistCollections() performing articleTagsJoinDao.deleteInTx()");
            articleTagsJoinDao.deleteInTx(joins);
            Log.v(TAG, "persistCollections() done articleTagsJoinDao.deleteInTx()");
        }

        if (!articleTagJoinsToInsert.isEmpty()) {
            List<ArticleTagsJoin> joins = new ArrayList<>();

            for (Map.Entry<Article, List<Tag>> entry : articleTagJoinsToInsert.entrySet()) {
                for (Tag tag : entry.getValue()) {
                    joins.add(new ArticleTagsJoin(null, entry.getKey().getId(), tag.getId()));
                }
            }

            articleTagJoinsToInsert.clear();

            Log.v(TAG, "persistCollections() performing articleTagsJoinDao.insertInTx()");
            articleTagsJoinDao.insertInTx(joins);
            Log.v(TAG, "persistCollections() done articleTagsJoinDao.insertInTx()");
        }

        if (!annotationRangesToRemove.isEmpty()) {
            Log.v(TAG, "persistCollections() performing annotationRangeDao.deleteInTx()");
            annotationRangeDao.deleteInTx(annotationRangesToRemove);
            Log.v(TAG, "persistCollections() done annotationRangeDao.deleteInTx()");

            annotationRangesToRemove.clear();
        }

        if (!annotationsToRemove.isEmpty()) {
            Log.v(TAG, "persistCollections() performing annotationDao.deleteInTx()");
            annotationDao.deleteInTx(annotationsToRemove);
            Log.v(TAG, "persistCollections() done annotationDao.deleteInTx()");

            annotationsToRemove.clear();
        }

        if (!annotationsToUpdate.isEmpty()) {
            Log.v(TAG, "persistCollections() performing annotationDao.updateInTx()");
            annotationDao.updateInTx(annotationsToUpdate);
            Log.v(TAG, "persistCollections() done annotationDao.updateInTx()");

            annotationsToUpdate.clear();
        }

        if (!annotationsToInsert.isEmpty()) {
            List<Annotation> annotations = new ArrayList<>(annotationsToInsert.size());

            for (Pair<Article, Annotation> entry : annotationsToInsert) {
                Article article = entry.first;
                Annotation annotation = entry.second;
                if (article != null) annotation.setArticleId(article.getId());
                annotations.add(annotation);
            }

            Log.v(TAG, "persistCollections() performing annotationDao.insertInTx()");
            annotationDao.insertInTx(annotations);
            Log.v(TAG, "persistCollections() done annotationDao.insertInTx()");

            annotationsToInsert.clear();
        }

        if (!annotationRangesToInsert.isEmpty()) {
            List<AnnotationRange> ranges = new ArrayList<>(annotationRangesToInsert.size());

            for (Pair<Annotation, AnnotationRange> entry : annotationRangesToInsert) {
                Annotation annotation = entry.first;
                AnnotationRange range = entry.second;
                if (annotation != null) range.setAnnotationId(annotation.getId());
                ranges.add(range);
            }

            Log.v(TAG, "persistCollections() performing annotationRangeDao.insertInTx()");
            annotationRangeDao.insertInTx(ranges);
            Log.v(TAG, "persistCollections() done annotationRangeDao.insertInTx()");

            annotationRangesToInsert.clear();
        }
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

    private void fixArticleNullValues(Article article) {
        if (article.getTitle() == null) article.setTitle("");
        if (article.isArticleContentLoaded() && article.getContent() == null
                && !article.isContentTooBig()) {
            article.setContent("");
        }
        if (article.getDomain() == null) article.setDomain("");
        if (article.getUrl() == null) article.setUrl("");
        if (article.getLanguage() == null) article.setLanguage("");
        if (article.getPreviewPictureURL() == null) article.setPreviewPictureURL("");
    }

    private wallabag.apiwrapper.models.Tag findApiTagByID(
            Integer id, List<wallabag.apiwrapper.models.Tag> tags) {
        for (wallabag.apiwrapper.models.Tag tag : tags) {
            if (id.equals(tag.id)) return tag;
        }

        return null;
    }

    private wallabag.apiwrapper.models.Tag findApiTagByLabel(
            String label, List<wallabag.apiwrapper.models.Tag> tags) {
        for (wallabag.apiwrapper.models.Tag tag : tags) {
            if (TextUtils.equals(tag.label, label)) return tag;
        }

        return null;
    }

    private wallabag.apiwrapper.models.Annotation findApiAnnotation(
            Integer annotationId, List<wallabag.apiwrapper.models.Annotation> annotations) {
        if (annotationId == null) return null;

        for (wallabag.apiwrapper.models.Annotation annotation : annotations) {
            if (annotation.id == annotationId) return annotation;
        }

        return null;
    }

    private wallabag.apiwrapper.models.Annotation.Range findApiAnnotationRange(
            AnnotationRange range, List<wallabag.apiwrapper.models.Annotation.Range> ranges) {
        for (wallabag.apiwrapper.models.Annotation.Range apiRange : ranges) {
            if (equalOrEmpty(apiRange.start, range.getStart())
                    && equalOrEmpty(apiRange.end, range.getEnd())
                    && apiRange.startOffset == range.getStartOffset()
                    && apiRange.endOffset == range.getEndOffset()) {
                return apiRange;
            }
        }

        return null;
    }

}
