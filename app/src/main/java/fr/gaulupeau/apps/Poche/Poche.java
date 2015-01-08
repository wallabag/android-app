/**
 * Android to Poche
 * A simple app to make the full save bookmark to Poche
 * web page available via the Share menu on Android tablets
 * @author GAULUPEAU Jonathan
 * August 2013
 */

package fr.gaulupeau.apps.Poche;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;


/**
 * Main activity class
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class Poche extends Activity {
	private static SQLiteDatabase database;
	Button btnGetPost;
	Button btnSync;
	Button btnSettings;
	SharedPreferences settings;
	static String apiUsername;
	static String apiToken;
	static String pocheUrl;
	String action;


	/**
	 * Called when the activity is first created.
	 * Will act differently depending on whether sharing or
	 * displaying information page.
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		action = intent.getAction();

		getSettings();
		// Find out if Sharing or if app has been launched from icon
		if (action.equals(Intent.ACTION_SEND) && !pocheUrl.equals("http://")) {
			setContentView(R.layout.main);
			findViewById(R.id.btnSync).setVisibility(View.GONE);
			findViewById(R.id.btnGetPost).setVisibility(View.GONE);
			findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);



			final String extraText = extras.getString("android.intent.extra.TEXT");
			final String pageUrl;

			// Parsing string for urls.
			Matcher matcher = Patterns.WEB_URL.matcher(extraText);
			if (matcher.find()) {
				pageUrl = matcher.group();
			} else {
				showErrorMessage("Couldn't find a URL in share string:\n"+extraText);
				return;
			}


			// Vérification de la connectivité Internet
			final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
			if (activeNetwork != null && activeNetwork.isConnected()) {
				// Start to build the poche URL
				Uri.Builder pocheSaveUrl = Uri.parse(pocheUrl).buildUpon();
				// Add the parameters from the call
				pocheSaveUrl.appendQueryParameter("action", "add");
				byte[] data = null;
				try {
					data = pageUrl.getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String base64 = Base64.encodeToString(data, Base64.DEFAULT);
				pocheSaveUrl.appendQueryParameter("url", base64);
				System.out.println("base64 : " + base64);
				System.out.println("pageurl : " + pageUrl);

				// Load the constructed URL in the browser
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(pocheSaveUrl.build());
				i.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
				// If user has more then one browser installed give them a chance to
				// select which one they want to use

				startActivity(i);
				// That is all this app needs to do, so call finish()
				this.finish();
			} else {
				// Afficher alerte connectivité
				showToast(getString(R.string.txtNetOffline));
			}
		} else {
			setContentView(R.layout.main);
			checkAndHandleAfterUpdate();

			btnSync = (Button) findViewById(R.id.btnSync);
			btnSync.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					// Vérification de la connectivité Internet
					final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
					final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
					if (pocheUrl.equals("http://")) {
						showToast(getString(R.string.txtConfigNotSet));
					} else if (activeNetwork != null && activeNetwork.isConnected()) {
						// Exécution de la synchro en arrière-plan
						findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);
						new Thread(new Runnable() {
							@Override
							public void run() {
								//pushRead();
								parseRSS();
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										findViewById(R.id.progressBar1).setVisibility(View.GONE);
									}
								});
							}
						}).start();
					} else {
						// Afficher alerte connectivité
						showToast(getString(R.string.txtNetOffline));
					}

				}
			});

			btnGetPost = (Button) findViewById(R.id.btnGetPost);
			//updateUnread();

			btnGetPost.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(getBaseContext(), ListArticles.class));
				}
			});

			btnSettings = (Button) findViewById(R.id.btnSettings);
			btnSettings.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					startActivity(new Intent(getBaseContext(), Settings.class));
				}
			});
		}
	}

	private void checkAndHandleAfterUpdate() {
		SharedPreferences pref = getSharedPreferences(PREFS_NAME, 0);

		if (pref.getInt("update_checker", 0) < 9) {
			// Wipe Database, because we now save HTML content instead of plain text
			ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
			getDatabase();
			helper.truncateTables(database);
			showToast("Update: Wiped Database. Please synchronize.");
		}

		int versionCode;
		try {
			versionCode = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionCode;
		} catch (Exception e) {
			versionCode = 0;
		}

		pref.edit().putInt("update_checker", versionCode).commit();
	}

	private void getSettings() {
		settings = getSharedPreferences(PREFS_NAME, 0);
		pocheUrl = settings.getString("pocheUrl", "http://");
		apiUsername = settings.getString("APIUsername", "");
		apiToken = settings.getString("APIToken", "");
	}

	private void getDatabase() {
		if (database == null) {
			ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
			database = helper.getReadableDatabase();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		getSettings();
		if (!action.equals(Intent.ACTION_SEND)) {
			updateUnread();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (database != null) {
			database.close();
		}
	}

	private void updateUnread() {
		runOnUiThread(new Runnable() {
			public void run() {
				getDatabase();
				int news = database.query(ARTICLE_TABLE, null, ARCHIVE + "=0", null, null, null, null).getCount();
				btnGetPost.setText(String.format(getString(R.string.btnGetPost), news));
			}
		});
	}

	public void showToast(final String toast) {
		runOnUiThread(new Runnable() {
			public void run() {
				Toast.makeText(Poche.this, toast, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void showErrorMessage(final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder messageBox = new AlertDialog.Builder(Poche.this);
				messageBox.setMessage(message);
				messageBox.setTitle(getString(R.string.error));
//				messageBox.setIconAttribute(android.R.attr.alertDialogIcon);
				messageBox.setPositiveButton("OK", null);
				messageBox.setCancelable(false);
				messageBox.create().show();
			}
		});
	}

//    public void pocheIt(String url){
//    	String id ="req-001";
//    	JSONRPC2Request reqOut = null;
//    	try{
//    		reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.add\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, \"" + url + "\", true]}");
//    		System.err.println(reqOut.toString());
//    		JSONRPC2Response response = sendRequest(reqOut);
//    		if (response.indicatesSuccess()) {
//    			showToast(getString(R.string.txtSyncDone));
//    		}
//    	} catch (JSONRPC2ParseException e2) {
//    		e2.printStackTrace();
//    		showToast(getString(R.string.txtSyncFailed));
//    	}
//    	finish();
//    }


//    public void pushRead(){
//    	JSONRPC2Request reqOut = null;
//    	String filter = ARCHIVE + "=1 AND " + ARTICLE_SYNC + "=0";
//    	String[] getStrColumns = new String[] {ARTICLE_ID};
//		Cursor ac = database.query(
//				ARTICLE_TABLE,
//				getStrColumns,
//				filter, null, null, null, null);
//		ac.moveToFirst();
//		if(!ac.isAfterLast()) {
//			do {
//				String article_id = ac.getString(0);
//				String id ="req-001";
//				try{
//		    		reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.mark_as_read\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, " + article_id + "]}");
//		    		System.err.println(reqOut.toString());
//		    		JSONRPC2Response response = sendRequest(reqOut);
//		    		if (response.indicatesSuccess()) {
//		    			ContentValues values = new ContentValues();
//		    			values.put(ARTICLE_SYNC, 1);
//						database.update(ARTICLE_TABLE, values, ARTICLE_ID + "=" + article_id, null);
//					}
//		    	} catch (JSONRPC2ParseException e2) {
//		    		e2.printStackTrace();
//		    	}
//			} while (ac.moveToNext());
//		}
//		ac.close();
//    	
//    }


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
			url = new URL(pocheUrl + "/?feed&type=home&user_id=" + apiUsername + "&token=" + apiToken);
			if (pocheUrl.startsWith("https")) {
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
					showErrorMessage("Got invalid response:\n\"" + stringBuffer.toString() + "\"");
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

				showToast(getString(R.string.txtSyncDone));
			} else {
				// HTTP Connection not successful
				if (urlConnection == null) {
					showErrorMessage(getString(R.string.error_feed));
				} else {
					showErrorMessage(getString(R.string.error_feed) + ":\n" + urlConnection.getResponseCode() + " " + urlConnection.getResponseMessage());
				}
			}
			updateUnread();
		} catch (Exception e) {
			e.printStackTrace();
		}


	}

//    public void fetchUnread(){
//    	String id = "req-001";
//    	JSONRPC2Request reqOut = null;
//    	try {
//    		// POCHE A LINK
//    		//reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.add\",\"id\":\"req-001\",\"params\":[{\"username\":\"poche\",\"api_token\":\"cPG2urVgA+ToMXY\"},\"http://cdetc.fr\",true]}");
//    		// GET A LINK
//    		//reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.info\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, 1]}");
//    		// GET ALL UNREAD
//    		reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.list_unread\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, null, null]}");
//    		System.err.println(reqOut.toString());
//    	} catch (JSONRPC2ParseException e2) {
//    		e2.printStackTrace();
//    	}
//    	System.out.println(reqOut.toString());
//    	URL url = null;
//    	try {
//    		final String rpcuser ="api_user";
//    		final String rpcpassword = globalToken;
//
//    		Authenticator.setDefault(new Authenticator() {
//    			protected PasswordAuthentication getPasswordAuthentication() {
//    				return new PasswordAuthentication (rpcuser, rpcpassword.toCharArray());
//    			}});
//    		url = new URL(pocheUrl + "/jsonrpc.php");
//    	} catch (MalformedURLException e1) {
//    		e1.printStackTrace();
//    	}
//    	JSONRPC2Session session = new JSONRPC2Session(url);
//    	JSONRPC2Response response = null;
//    	try{
//    		response = session.send(reqOut);
//    	} catch (JSONRPC2SessionException e) {
//
//    		System.err.println(e.getMessage());
//    	}
//    	if (response.indicatesSuccess()){
//    		JSONObject article = null;
//    		ContentValues values = new ContentValues();
//    		try {
//    			JSONArray ret = new JSONArray(response.getResult().toString());
//    			for (int i = 0; i < ret.length(); i++) {
//    				article = ret.getJSONObject(i);
//    				values.put(ARTICLE_TITLE, Html.fromHtml(article.getString("title")).toString());
//    				values.put(ARTICLE_CONTENT, Html.fromHtml(article.getString("content")).toString());
//    				values.put(ARTICLE_ID, Html.fromHtml(article.getString("id")).toString());
//    				values.put(ARTICLE_URL, Html.fromHtml(article.getString("url")).toString());
//    				values.put(ARCHIVE, 0);
//    				values.put(ARTICLE_SYNC, 0);
//    				try {
//    					database.insertOrThrow(ARTICLE_TABLE, null, values);
//    				} catch (SQLiteConstraintException e) {
//    					continue;
//    				}
//    			}
//    		} catch (JSONException e) {
//    			e.printStackTrace();
//    			showToast(getString(R.string.txtSyncFailed));
//    		}
//
//    		showToast(getString(R.string.txtSyncDone));
//    		updateUnread();
//    	}else{
//    		System.out.println(response.getError().getMessage( ));
//    		showToast(getString(R.string.txtSyncFailed));
//    	}
//    }
//    
//    public JSONRPC2Response sendRequest(JSONRPC2Request reqOut){
//    	URL url = null;
//    	try {
//    		final String rpcuser ="api_user";
//    		final String rpcpassword = globalToken;
//
//    		Authenticator.setDefault(new Authenticator() {
//    			protected PasswordAuthentication getPasswordAuthentication() {
//    				return new PasswordAuthentication (rpcuser, rpcpassword.toCharArray());
//    			}});
//    		url = new URL(pocheUrl + "/jsonrpc.php");
//    	} catch (MalformedURLException e1) {
//    		e1.printStackTrace();
//    	}
//    	JSONRPC2Session session = new JSONRPC2Session(url);
//    	JSONRPC2Response response = null;
//    	try{
//    		response = session.send(reqOut);
//    	} catch (JSONRPC2SessionException e) {
//
//    		System.err.println(e.getMessage());
//    	}
//    	return response;
//    }

}

