package fr.gaulupeau.apps.Poche.network;

import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.greenrobot.greendao.query.WhereCondition;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectFeedException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectFeedItemException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FeedUpdater {

    public enum UpdateType { FULL, FAST }

    public enum FeedType {
        MAIN("home", "unread.xml", R.string.feedName_unread),
        FAVORITE("fav", "starred.xml", R.string.feedName_favorites),
        ARCHIVE("archive", "archive.xml", R.string.feedName_archived);

        String urlPartV1, urlPartV2;
        int nameResID;

        FeedType(String urlPartV1, String urlPartV2, int nameResID) {
            this.urlPartV1 = urlPartV1;
            this.urlPartV2 = urlPartV2;
            this.nameResID = nameResID;
        }

        public String getUrlPart(int wallabagVersion) {
            switch(wallabagVersion) {
                case 1: return urlPartV1;
                case 2: return urlPartV2;
            }
            return "";
        }

        public int getLocalizedResourceID() {
            return nameResID;
        }
    }

    private static final String TAG = FeedUpdater.class.getSimpleName();

    private String baseURL;
    private String apiUserId;
    private String apiToken;
    private RequestCreator requestCreator;
    private int wallabagVersion;

    private OkHttpClient httpClient;

    public FeedUpdater(String baseURL, String apiUserId, String apiToken,
                       String httpAuthUsername, String httpAuthPassword,
                       int wallabagVersion) {
        this(baseURL, apiUserId, apiToken,
                httpAuthUsername, httpAuthPassword,
                wallabagVersion, null);
    }

    public FeedUpdater(String baseURL, String apiUserId, String apiToken,
                       String httpAuthUsername, String httpAuthPassword,
                       int wallabagVersion, OkHttpClient httpClient) {
        this.baseURL = baseURL;
        this.apiUserId = apiUserId;
        this.apiToken = apiToken;
        requestCreator = new RequestCreator(httpAuthUsername, httpAuthPassword);
        this.wallabagVersion = wallabagVersion;
        this.httpClient = httpClient;
    }

    public ArticlesChangedEvent update(FeedType feedType, UpdateType updateType)
            throws IncorrectFeedException, RequestException, IOException {
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

    private void updateAllFeeds() throws IncorrectFeedException, RequestException, IOException {
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
            throws IncorrectFeedException, RequestException, IOException {
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
            throws IncorrectFeedException, RequestException, IOException {
        Log.d(TAG, "updateByFeed() started");

        InputStream is = null;
        try {
            is = getInputStream(getFeedUrl(feedType));

            Log.d(TAG, "updateByFeed() got input stream; processing feed");
            try {
                processFeed(articleDao, is, feedType, updateType, id, event);
            } catch(XmlPullParserException e) {
                String feedInfo = "feedType: " + feedType + ", updateType: " + updateType;
                Log.e(TAG, "XmlPullParserException on " + feedInfo);
                throw new IncorrectFeedException("Unknown error on " + feedInfo, e);
            }

            Log.d(TAG, "updateByFeed() finished successfully");
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public String getFeedUrl(FeedType feedType) {
        if(wallabagVersion == 1) {
            return baseURL + "/?feed"
                    + "&type=" + feedType.getUrlPart(wallabagVersion)
                    + "&user_id=" + apiUserId
                    + "&token=" + apiToken;
        } else if (wallabagVersion == 2) {
            return baseURL + "/"
                    + apiUserId + "/"
                    + apiToken + "/"
                    + feedType.getUrlPart(wallabagVersion);
        }

        return "";
    }

    private InputStream getInputStream(String url)
            throws IncorrectConfigurationException, IOException {
        Response response = getResponse(url);
        if(response.isSuccessful()) {
            return response.body().byteStream();
        } else {
            // TODO: check
            throw new IncorrectConfigurationException(String.format(
                    App.getInstance().getString(R.string.unsuccessfulRequest_errorMessage),
                    response.code(), response.message()));
        }
    }

    public Response getResponse(String url) throws IncorrectConfigurationException, IOException {
        Request request = requestCreator.getRequest(WallabagConnection.getHttpURL(url));

        return getHttpClient().newCall(request).execute();
    }

    private OkHttpClient getHttpClient() {
        if(httpClient == null) {
            httpClient = WallabagConnection.getClient();
        }

        return httpClient;
    }

    private void processFeed(ArticleDao articleDao, InputStream is,
                             FeedType feedType, UpdateType updateType, Integer latestID,
                             ArticlesChangedEvent event)
            throws IncorrectFeedItemException, XmlPullParserException, IOException {
        Log.d(TAG, "processFeed() latestID=" + latestID);
        // TODO: use parser.require() all over the place?

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(is, null);

        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, null, "rss");

        goToElement(parser, "channel", true);
        parser.next();

        DateFormat dateFormat;
        if(wallabagVersion == 1) {
            // <pubDate>Thu, 14 Apr 2016 18:35:11 +0000</pubDate>
            dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        }
        else { // v2
            // <pubDate>Thu, 14 Apr 2016 16:28:06</pubDate>
            dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        }

        Log.d(TAG, "processFeed() starting to loop through all elements");
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            Log.v(TAG, "processFeed() parser.getName()=" + parser.getName());
            if ("item".equals(parser.getName())) {
                if(feedType == FeedType.MAIN || feedType == FeedType.ARCHIVE
                        || (feedType == FeedType.FAVORITE && updateType == UpdateType.FULL)) {
                    // MAIN: FULL, FAST
                    // ARCHIVE: FULL, FAST
                    // FAVORITE: FULL

                    Item item = parseItem(parser);

                    Integer id = getIDFromURL(item.sourceUrl);
                    if(id == null) {
                        Log.w(TAG, "processFeed(): id is null, skipping; url: " + item.sourceUrl);
                        continue;
                    }

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
                            && !TextUtils.equals(article.getContent(), item.description))) {
                        article.setImagesDownloaded(false);
                    }

                    article.setTitle(Html.fromHtml(item.title).toString());
                    article.setContent(item.description);
                    article.setUrl(item.link);
                    article.setArticleId(id);
                    try {
                        article.setUpdateDate(dateFormat.parse(item.pubDate));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    if(existing) {
                        if(feedType == FeedType.ARCHIVE) {
                            article.setArchive(true);
                        } else if(feedType == FeedType.FAVORITE) {
                            article.setFavorite(true);
                        }
                    } else {
                        article.setArchive(feedType == FeedType.ARCHIVE);
                        article.setFavorite(feedType == FeedType.FAVORITE);
                    }

                    if(event != null) {
                        ArticlesChangedEvent.ChangeType changeType = existing
                                ? ArticlesChangedEvent.ChangeType.UNSPECIFIED
                                : ArticlesChangedEvent.ChangeType.ADDED;

                        event.setChangedByFeedType(feedType);
                        event.addChangedArticle(article, changeType);
                    }

                    articleDao.insertOrReplace(article);
                } else if(feedType == FeedType.FAVORITE) {
                    // FAVORITE: FAST (ONLY applicable if MAIN and ARCHIVE feeds are up to date)
                    // probably a bit faster then "FAVORITE: FULL"

                    Integer id = parseItemID(parser);
                    if(id == null) continue;

                    Article article = articleDao.queryBuilder()
                            .where(ArticleDao.Properties.ArticleId.eq(id))
                            .build().unique();
                    if(article == null) {
                        Log.w(TAG, "processFeed() FAVORITE: FAST; couldn't find article with ID: "
                                + id);
                        continue;
                    }

                    if(article.getFavorite() == null || !article.getFavorite()) {
                        article.setFavorite(true);

                        articleDao.update(article);

                        if(event != null) {
                            event.setFavoriteFeedChanged(true);
                            if(article.getArchive()) {
                                event.setArchiveFeedChanged(true);
                            } else {
                                event.setMainFeedChanged(true);
                            }

                            event.addChangedArticle(article,
                                    ArticlesChangedEvent.ChangeType.FAVORITED);
                        }
                    }
                }
            } else {
                skipElement(parser);
            }
        }
    }

    private static void goToElement(XmlPullParser parser, String elementName, boolean hierarchically)
            throws XmlPullParserException, IOException {
        do {
            if(parser.getEventType() == XmlPullParser.START_TAG) {
                if(elementName.equals(parser.getName())) return;
                else if(!hierarchically) skipElement(parser);
            }
        } while(parser.next() != XmlPullParser.END_DOCUMENT);
    }

    private static void skipElement(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if(parser.getEventType() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("Unexpected state");
        }

        int depth = 1;
        while(depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    private static Item parseItem(XmlPullParser parser)
            throws IncorrectFeedItemException, IOException {
        Item item = new Item();

        try {
            while(parser.next() != XmlPullParser.END_TAG) {
                if(parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }

                switch(parser.getName()) {
                    case "title":
                        item.title = cleanString(parseNextText(parser));
                        break;
                    case "source":
                        String sourceUrl = parser.getAttributeValue(null, "url");
                        parseNextText(parser); // ignore "empty" element
                        item.sourceUrl = sourceUrl;
                        break;
                    case "link":
                        item.link = parseNextText(parser);
                        break;
                    case "pubDate":
                        item.pubDate = parseNextText(parser);
                        break;
                    case "description":
                        item.description = parseNextText(parser);
                        break;

                    default:
                        skipElement(parser);
                        break;
                }
            }
        } catch(XmlPullParserException e) {
            throw new IncorrectFeedItemException(item, e);
        }

        return item;
    }

    private static String parseNextText(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        String result = parser.nextText();

        // workaround for Android 2.3.3 XmlPullParser.nextText() bug
        if(parser.getEventType() != XmlPullParser.END_TAG) parser.next();

        return result;
    }

    private static Integer parseItemID(XmlPullParser parser) throws XmlPullParserException, IOException {
        while(parser.next() != XmlPullParser.END_TAG) {
            if(parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
                case "source":
                    return getIDFromURL(parser.getAttributeValue(null, "url"));

                default:
                    skipElement(parser);
                    break;
            }
        }

        return null;
    }

    private static Integer getIDFromURL(String url) {
        if(url != null) {
            String[] markers = {"id=", "view/"};
            for (String marker : markers) {
                int index = url.indexOf(marker);
                if(index >= 0) {
                    String idStr = url.substring(index + marker.length());
                    try {
                        return Integer.parseInt(idStr);
                    } catch (NumberFormatException nfe) {
                        Log.w(TAG, "getIDFromURL() NumberFormatException; str: " + idStr, nfe);
                    }
                }
            }
        }
        return null;
    }

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

    public static class Item {
        public String title;
        public String sourceUrl;
        public String link;
        public String pubDate;
        public String description;
    }

}
