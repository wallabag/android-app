package fr.gaulupeau.apps.Poche.network.tasks;

import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.text.Html;
import android.util.Log;
import android.util.Xml;

import okhttp3.Request;
import okhttp3.Response;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import de.greenrobot.dao.query.WhereCondition;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.ui.IconUnreadWidget;

public class UpdateFeedTask extends AsyncTask<Void, Void, Void> {

    public enum UpdateType { Full, Fast }

    public enum FeedType {
        Main("home", "unread.xml"),
        Favorite("fav", "starred.xml"),
        Archive("archive", "archive.xml");

        String urlPartV1, urlPartV2;

        FeedType(String urlPartV1, String urlPartV2) {
            this.urlPartV1 = urlPartV1;
            this.urlPartV2 = urlPartV2;
        }

        public String getUrlPart(int wallabagVersion) {
            switch(wallabagVersion) {
                case 1: return urlPartV1;
                case 2: return urlPartV2;
            }
            return "";
        }
    }

    private static final String TAG = UpdateFeedTask.class.getSimpleName();

    private String baseURL;
    private String apiUserId;
    private String apiToken;
    private int wallabagVersion;
    private CallbackInterface callback;
    private FeedType feedType;
    private UpdateType updateType;

    private String errorMessage;

    public UpdateFeedTask(String baseURL, String apiUserId, String apiToken, int wallabagVersion,
                          CallbackInterface callback,
                          FeedType feedType, UpdateType updateType) {
        this.baseURL = baseURL;
        this.apiUserId = apiUserId;
        this.apiToken = apiToken;
        this.wallabagVersion = wallabagVersion;
        this.callback = callback;
        this.feedType = feedType;
        this.updateType = updateType;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if(feedType == null && updateType == null) {
            updateAllFeeds();
        } else {
            if(feedType == null) {
                throw new IllegalArgumentException("If updateType is set, feedType must be set too");
            }
            if(updateType == null) {
                updateType = UpdateType.Full;
            }

            update(feedType, updateType);
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (callback == null)
            return;

        if (errorMessage == null) {
            callback.feedUpdateFinishedSuccessfully();
        } else {
            callback.feedUpdateFinishedWithError(errorMessage);
        }
    }

    private void updateAllFeeds() {
        Log.i(TAG, "updateAllFeeds() started");

        ArticleDao articleDao = DbConnection.getSession().getArticleDao();
        SQLiteDatabase db = articleDao.getDatabase();

        Log.d(TAG, "updateAllFeeds() beginning transaction");
        db.beginTransaction();
        try {
            Log.d(TAG, "updateAllFeeds() deleting old articles");
            articleDao.deleteAll();

            Log.d(TAG, "updateAllFeeds() updating Main feed");
            if(!updateByFeed(articleDao, FeedType.Main, UpdateType.Full, 0)) {
                Log.w(TAG, "updateAllFeeds() Main feed update failed; exiting");
                return;
            }

            Log.d(TAG, "updateAllFeeds() updating Archive feed");
            if(!updateByFeed(articleDao, FeedType.Archive, UpdateType.Full, 0)) {
                Log.w(TAG, "updateAllFeeds() Archive feed update failed; exiting");
                return;
            }

            Log.d(TAG, "updateAllFeeds() updating Favorite feed");
            if(!updateByFeed(articleDao, FeedType.Favorite, UpdateType.Fast, 0)) {
                Log.w(TAG, "updateAllFeeds() Favorite feed update failed; exiting");
                return;
            }

            Log.d(TAG, "updateAllFeeds() setting transaction successful");
            db.setTransactionSuccessful();
        } finally {
            Log.d(TAG, "updateAllFeeds() ending transaction");
            db.endTransaction();
        }

        Log.d(TAG, "updateAllFeeds() finished");
    }

    private void update(FeedType feedType, UpdateType updateType) {
        ArticleDao articleDao = DbConnection.getSession().getArticleDao();
        SQLiteDatabase db = articleDao.getDatabase();

        db.beginTransaction();
        try {

            Integer latestID = null;
            if(feedType == FeedType.Main || feedType == FeedType.Archive) {
                WhereCondition cond = feedType == FeedType.Main
                        ? ArticleDao.Properties.Archive.notEq(true)
                        : ArticleDao.Properties.Archive.eq(true);
                List<Article> l = articleDao.queryBuilder().where(cond)
                        .orderDesc(ArticleDao.Properties.ArticleId).limit(1).list();

                if(!l.isEmpty()) {
                    latestID = l.get(0).getArticleId();
                }
            }

            if(!updateByFeed(articleDao, feedType, updateType, latestID)) {
                return;
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean updateByFeed(ArticleDao articleDao, FeedType feedType, UpdateType updateType,
                                 Integer id) {
        Log.d(TAG, "updateByFeed() started");

        InputStream is = null;
        try {
            // TODO: rewrite?
            try {
                is = getInputStream(getFeedUrl(feedType));
            } catch (IOException e) {
                Log.e("FeedUpdater.updateByF", "IOException on " + feedType, e);
                errorMessage = App.getInstance().getString(R.string.feedUpdater_IOException);
                return false;
            } catch (RuntimeException e) {
                Log.e("FeedUpdater.updateByF", "RuntimeException on " + feedType, e);
                errorMessage = e.getMessage();
                return false;
            }

            Log.d(TAG, "updateByFeed() got input stream; processing feed");
            try {
                processFeed(articleDao, is, feedType, updateType, id);
            } catch (IOException e) {
                Log.e("FeedUpdater.updateByF", "IOException on " + feedType, e);
                errorMessage = App.getInstance()
                        .getString(R.string.feedUpdater_IOExceptionOnProcessingFeed);
                return false;
            } catch (XmlPullParserException e) {
                Log.e("FeedUpdater.updateByF", "XmlPullParserException on " + feedType, e);
                errorMessage = App.getInstance().getString(R.string.feedUpdater_feedProcessingError);
                return false;
            }

            IconUnreadWidget.triggerWidgetUpdate(App.getInstance().getApplicationContext());

            Log.d(TAG, "updateByFeed() finished successfully");

            return true;
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private String getFeedUrl(FeedType feedType) {
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

    private InputStream getInputStream(String urlStr) throws IOException {
        Request request = WallabagConnection.getRequest(WallabagConnection.getHttpURL(urlStr));

        Response response = WallabagConnection.getClient().newCall(request).execute();

        if(response.isSuccessful()) {
            return response.body().byteStream();
        } else {
            // TODO: fix
            throw new RuntimeException(String.format(
                    App.getInstance().getString(R.string.unsuccessfulRequest_errorMessage),
                    response.code(), response.message()
            ));
        }
    }

    private void processFeed(ArticleDao articleDao, InputStream is,
                             FeedType feedType, UpdateType updateType, Integer latestID)
            throws XmlPullParserException, IOException {
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
                if(feedType == FeedType.Main || feedType == FeedType.Archive
                        || (feedType == FeedType.Favorite && updateType == UpdateType.Full)) {
                    // Main: Full, Fast
                    // Archive: Full, Fast
                    // Favorite: Full

                    Item item = parseItem(parser);

                    Integer id = getIDFromURL(item.sourceUrl);
                    if(id == null) {
                        Log.w(TAG, "processFeed(): id is null, skipping; url: " + item.sourceUrl);
                        continue;
                    }

                    if(updateType == UpdateType.Fast && latestID != null && latestID >= id) break;

                    Article article = articleDao.queryBuilder()
                            .where(ArticleDao.Properties.ArticleId.eq(id)).build().unique();

                    boolean existing = true;
                    if(article == null) {
                        article = new Article(null);
                        existing = false;
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
                        if(feedType == FeedType.Archive) {
                            article.setArchive(true);
                        } else if(feedType == FeedType.Favorite) {
                            article.setFavorite(true);
                        }
                    } else {
                        article.setArchive(feedType == FeedType.Archive);
                        article.setFavorite(feedType == FeedType.Favorite);
                    }

                    articleDao.insertOrReplace(article);
                } else if(feedType == FeedType.Favorite) {
                    // Favorite: Fast (ONLY applicable if Main and Archive feeds are up to date)
                    // probably a bit faster then "Favorite: Full"

                    Integer id = parseItemID(parser);
                    if(id == null) continue;

                    Article article = articleDao.queryBuilder()
                            .where(ArticleDao.Properties.ArticleId.eq(id))
                            .build().unique();
                    if(article == null) {
                        Log.w(TAG, "processFeed() Favorite: Fast; couldn't find article with ID: "
                                + id);
                        continue;
                    }

                    if(article.getFavorite() != null && article.getFavorite()) continue;

                    article.setFavorite(true);

                    articleDao.update(article);
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
            throw new IllegalStateException();
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

    private static Item parseItem(XmlPullParser parser) throws XmlPullParserException, IOException {
        Item item = new Item();

        while(parser.next() != XmlPullParser.END_TAG) {
            if(parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
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
        if(s == null || s.length() == 0) return s;

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

    private static class Item {
        String title;
        String sourceUrl;
        String link;
        String pubDate;
        String description;
    }

    public interface CallbackInterface {
        void feedUpdateFinishedWithError(String errorMessage);
        void feedUpdateFinishedSuccessfully();
    }

}
