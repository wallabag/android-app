package fr.gaulupeau.apps.Poche;

import android.content.ContentValues;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_DATE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_SYNC;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;

/**
 * Created by kevinmeyer on 13/12/14.
 */

interface FeedUpdaterInterface {
    void feedUpdaterFinishedWithError(String errorMessage);
    void feedUpdatedFinishedSuccessfully();
}

public class FeedUpdater extends AsyncTask<Void, Void, Void> {

    private SQLiteDatabase database;
    private String wallabagUrl;
    private String apiUserId;
    private String apiToken;
    private FeedUpdaterInterface callback;
    private String errorMessage;

    public FeedUpdater(String wallabagUrl, String apiUserId, String apiToken, SQLiteDatabase writableDatabase, FeedUpdaterInterface callback) {
        this.wallabagUrl = wallabagUrl;
        this.apiUserId = apiUserId;
        this.apiToken = apiToken;
        this.database = writableDatabase;
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

    private void trustEveryone() {
        try {
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(
                    context.getSocketFactory());
        } catch (Exception e) { // should never happen
            e.printStackTrace();
        }
    }


    public void parseRSS() {

        URL url;
        try {
            // Set the url (you will need to change this to your RSS URL
            url = new URL(wallabagUrl + "/?feed&type=home&user_id=" + apiUserId + "&token=" + apiToken);
            if (wallabagUrl.startsWith("https")) {
                trustEveryone();
            }

            // Setup the connection
            HttpURLConnection urlConnection;
            urlConnection = (HttpURLConnection) url.openConnection();

            if ((urlConnection != null) && (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK)) {

                // Retreive the XML from the URL
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = null;

                InputSource is;

                try {
                    is = new InputSource(
                            new InputStreamReader(
                                    urlConnection.getInputStream()));
                    doc = db.parse(is);
                    doc.getDocumentElement().normalize();
                } catch (SAXException e) {
                    e.printStackTrace();

                    InputStream inputStream = url.openStream();
                    int ch;
                    StringBuffer stringBuffer = new StringBuffer();
                    while ((ch = inputStream.read()) != -1) {
                        stringBuffer.append((char) ch);
                    }
                    errorMessage = ":\nGot invalid response:\n\"" + stringBuffer.toString() + "\"";
                    return;
                }

                // This is the root node of each section you want to parse
                NodeList itemLst = doc.getElementsByTagName("item");

                // This sets up some arrays to hold the data parsed
                arrays.PodcastTitle = new String[itemLst.getLength()];
                arrays.PodcastURL = new String[itemLst.getLength()];
                arrays.PodcastContent = new String[itemLst.getLength()];
                arrays.PodcastMedia = new String[itemLst.getLength()];
                arrays.PodcastDate = new String[itemLst.getLength()];

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

                        ContentValues values = new ContentValues();
                        values.put(ARTICLE_TITLE, arrays.PodcastTitle[i]);
                        values.put(ARTICLE_CONTENT, arrays.PodcastContent[i]);
                        //values.put(ARTICLE_ID, Html.fromHtml(article.getString("id")).toString());
                        values.put(ARTICLE_URL, arrays.PodcastURL[i]);
                        values.put(ARTICLE_DATE, arrays.PodcastDate[i]);
                        values.put(ARCHIVE, 0);
                        values.put(ARTICLE_SYNC, 0);
                        try {
                            database.insertOrThrow(ARTICLE_TABLE, null, values);
                        } catch (SQLiteConstraintException e) {
                            continue;
                        } catch (SQLiteException e) {
                            database.execSQL("ALTER TABLE " + ARTICLE_TABLE + " ADD COLUMN " + ARTICLE_DATE + " datetime;");
                            database.insertOrThrow(ARTICLE_TABLE, null, values);
                        }
                    }
                }

            } else {
                // HTTP Connection not successful
                if (urlConnection == null) {
                    errorMessage = "";
                } else {
                    errorMessage = ":\n" + urlConnection.getResponseCode() + " " + urlConnection.getResponseMessage();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}
