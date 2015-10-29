package fr.gaulupeau.apps.Poche.data;

import android.os.AsyncTask;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import de.greenrobot.dao.DaoException;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.util.arrays;

public class FeedUpdater extends AsyncTask<Void, Void, Void> {

    private String wallabagUrl;
    private String apiUserId;
    private String apiToken;
    private FeedUpdaterInterface callback;
    private String errorMessage;

    public FeedUpdater(String wallabagUrl, String apiUserId, String apiToken, FeedUpdaterInterface callback) {
        this.wallabagUrl = wallabagUrl;
        this.apiUserId = apiUserId;
        this.apiToken = apiToken;
        this.callback = callback;
    }

    @Override
    protected Void doInBackground(Void... params) {
        parseRSS();
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (callback == null)
            return;

        if (errorMessage == null) {
            callback.feedUpdatedFinishedSuccessfully();
        } else {
            callback.feedUpdaterFinishedWithError(errorMessage);
        }
    }

    public void parseRSS() {
        URL url;
        // Set the url (you will need to change this to your RSS URL
        try {
            url = new URL(wallabagUrl + "/?feed&type=home&user_id=" + apiUserId + "&token=" + apiToken);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        OkHttpClient client = WallabagConnection.getClient();

        String requestUrl = wallabagUrl + "/?feed&type=home&user_id=" + apiUserId + "&token=" + apiToken;

        Request request = new Request.Builder()
                .url(requestUrl)
                .build();

        Response response = null;
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }


        Document document = null;
        try {
            document = createDocument(response);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }

        parseFeed(document);
    }

    private void parseFeed(Document document) {
        DaoSession session = DbConnection.getSession();
        ArticleDao articleDao = session.getArticleDao();

        // This is the root node of each section you want to parse
        NodeList itemLst = document.getElementsByTagName("item");

        // This sets up some arrays to hold the data parsed
        arrays.PodcastTitle = new String[itemLst.getLength()];
        arrays.PodcastURL = new String[itemLst.getLength()];
        arrays.PodcastContent = new String[itemLst.getLength()];
        arrays.PodcastMedia = new String[itemLst.getLength()];
        arrays.PodcastDate = new String[itemLst.getLength()];
        arrays.PodcastId = new String[itemLst.getLength()];

        // Loop through the XML passing the data to the arrays
        for (int i = 0; i < itemLst.getLength(); i++) {

            Node item = itemLst.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                Element ielem = (Element) item;

                // This section gets the elements from the XML
                // that we want to use you will need to add
                // and remove elements that you want / don't want
                NodeList title = ielem.getElementsByTagName("title");
                NodeList link = ielem.getElementsByTagName("link");
                NodeList date = ielem.getElementsByTagName("pubDate");
                NodeList content = ielem
                        .getElementsByTagName("description");
                NodeList source = ielem.getElementsByTagName("source");

                //NodeList media = ielem
                //		.getElementsByTagName("media:content");

                // This is an attribute of an element so I create
                // a string to make it easier to use
                //String mediaurl = media.item(0).getAttributes()
                //		.getNamedItem("url").getNodeValue();

                // This section adds an entry to the arrays with the
                // data retrieved from above. I have surrounded each
                // with try/catch just incase the element does not
                // exist
                try {
                    arrays.PodcastTitle[i] = cleanString(title.item(0).getChildNodes().item(0).getNodeValue());
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    arrays.PodcastTitle[i] = "Echec";
                }
                try {
                    arrays.PodcastDate[i] = date.item(0).getChildNodes().item(0).getNodeValue();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    arrays.PodcastDate[i] = null;
                }
                try {
                    arrays.PodcastURL[i] = link.item(0).getChildNodes()
                            .item(0).getNodeValue();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    arrays.PodcastURL[i] = "Echec";
                }
                try {
                    arrays.PodcastContent[i] = content.item(0)
                            .getChildNodes().item(0).getNodeValue();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    arrays.PodcastContent[i] = "Echec";
                }

                NamedNodeMap sourceAttrs = source.item(0).getAttributes();
                Node item1 = sourceAttrs.item(0);
                String sourceUrl = item1.getNodeValue();
                Map<String, String> urlQueryParams = getUrlQueryParams(sourceUrl);
                String id = urlQueryParams.get("id");
                arrays.PodcastId[i] = id;


                fr.gaulupeau.apps.Poche.entity.Article article = new Article(null);
                article.setTitle(arrays.PodcastTitle[i]);
                article.setContent(arrays.PodcastContent[i]);
                article.setUrl(arrays.PodcastURL[i]);
                article.setArticleId(Integer.parseInt(arrays.PodcastId[i]));
                DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
                Date date1 = null;
                try {
                    date1 = dateFormat.parse(arrays.PodcastDate[i]);
                    article.setUpdateDate(date1);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                article.setArchive(false);
                article.setSync(false);

                try {
                    Article existing = articleDao.queryBuilder()
                            .where(ArticleDao.Properties.ArticleId.eq(article.getArticleId()))
                            .build().uniqueOrThrow();
                    if (existing == null) {
                        articleDao.insert(article);
                    }
                } catch (DaoException e) {
                    articleDao.insert(article);
                }
//                Log.d("foo", "insert " + article.getArticleId());
            }
        }
        Log.d("foo", "articles "+ articleDao.count());
    }

    private Document createDocument(Response response) throws IOException, ParserConfigurationException, SAXException {
        InputStream inputStream = response.body().byteStream();

        // Retreive the XML from the URL
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        return documentBuilder.parse(inputStream);
    }

    public String cleanString(String s) {
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
        s = s.replace("&amp;", "&amp;");

        // Replace multiple whitespaces with single space
        s = s.replaceAll("\\s+", " ");
        s = s.trim();

        return s;
    }

    /**
     * Split up URL params a la http://stackoverflow.com/a/13592567/1592572
     */
    private Map<String, String> getUrlQueryParams(String surl) {
        URL url = null;
        try {
            url = (new URI(surl)).toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String query = url.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return query_pairs;
    }

}
