package fr.gaulupeau.apps.Poche.ui;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.ConsoleMessage;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import fr.gaulupeau.apps.Poche.network.tasks.DownloadPdfTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.network.tasks.AddLinkTask;
import fr.gaulupeau.apps.Poche.network.tasks.DeleteArticleTask;
import fr.gaulupeau.apps.Poche.network.tasks.ToggleArchiveTask;
import fr.gaulupeau.apps.Poche.network.tasks.ToggleFavoriteTask;
import fr.gaulupeau.apps.Poche.tts.TtsFragment;

public class ReadArticleActivity extends BaseActionBarActivity {

    public static final String EXTRA_ID = "ReadArticleActivity.id";
    public static final String EXTRA_LIST_ARCHIVED = "ReadArticleActivity.archived";
    public static final String EXTRA_LIST_FAVORITES = "ReadArticleActivity.favorites";

    private static final String TAG = ReadArticleActivity.class.getSimpleName();

    private ScrollView scrollView;
    private WebView webViewContent;
    private TextView loadingPlaceholder;
    private LinearLayout bottomTools;
    private View hrBar;
    private TtsFragment ttsFragment;
    private MenuItem menuTTS;

    private Article mArticle;
    private ArticleDao mArticleDao;

    private Boolean contextFavorites;
    private Boolean contextArchived;

    private boolean acceptAllSSLCerts;

    private String titleText;
    private String originalUrlText;
    private String domainText;
    private Double positionToRestore;
    private int webViewHeightBeforeUpdate;
    private Runnable positionRestorationRunnable;

    private Long previousArticleID;
    private Long nextArticleID;

    private boolean isResumed;
    private boolean onPageFinishedCallPostponedUntilResume;
    private boolean loadingFinished;

    private Settings settings;

    private int fontSize = 100;

    public void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.article);

        Intent intent = getIntent();
        long articleId = intent.getLongExtra(EXTRA_ID, -1);
        Log.d(TAG, "onCreate() articleId=" + articleId);
        if(intent.hasExtra(EXTRA_LIST_FAVORITES)) {
            contextFavorites = intent.getBooleanExtra(EXTRA_LIST_FAVORITES, false);
        }
        if(intent.hasExtra(EXTRA_LIST_ARCHIVED)) {
            contextArchived = intent.getBooleanExtra(EXTRA_LIST_ARCHIVED, false);
        }

        DaoSession session = DbConnection.getSession();
        mArticleDao = session.getArticleDao();
        mArticle = mArticleDao.queryBuilder()
                .where(ArticleDao.Properties.Id.eq(articleId)).build().unique();

        if(mArticle == null) {
            Log.e(TAG, "onCreate() Did not find article with articleId=" + articleId + ". Thus we" +
                    " are not able to create this activity. Finish.");
            finish();
            return;
        }
        titleText = mArticle.getTitle();
        Log.d(TAG, "onCreate: titleText=" + titleText);
        originalUrlText = mArticle.getUrl();
        Log.d(TAG, "onCreate: originalUrlText=" + originalUrlText);
        String htmlContent = mArticle.getContent();
        Log.d(TAG, "onCreate: htmlContent=" + htmlContent);
        positionToRestore = mArticle.getArticleProgress();
        Log.d(TAG, "onCreate: positionToRestore=" + positionToRestore);

        setTitle(titleText);

        settings = App.getInstance().getSettings();

        acceptAllSSLCerts = settings.getBoolean(Settings.ALL_CERTS, false);

        String cssName;
        boolean highContrast = false;
        switch(Themes.getCurrentTheme()) {
            case LightContrast:
                highContrast = true;
            case Light:
            default:
                cssName = "main";
                break;

            case DarkContrast:
                highContrast = true;
            case Dark:
                cssName = "dark";
                break;

            case Solarized:
                cssName = "solarized";
                highContrast = false;
                break;
        }

        fontSize = settings.getInt(Settings.FONT_SIZE, fontSize);
        boolean serifFont = settings.getBoolean(Settings.SERIF_FONT, false);

        if(fontSize < 5) fontSize = 100; // TODO: remove: temp hack for compatibility

        List<String> additionalClasses = new ArrayList<>(1);
        if(highContrast) additionalClasses.add("high-contrast");
        if(serifFont) additionalClasses.add("serif-font");

        String classAttr;
        if(!additionalClasses.isEmpty()) {
            StringBuilder sb = new StringBuilder();

            sb.append(" class=\"");
            for(String cl: additionalClasses) {
                sb.append(cl).append(' ');
            }
            sb.append('"');

            classAttr = sb.toString();
        } else {
            classAttr = "";
        }

        try {
            URL url = new URL(originalUrlText);
            domainText = url.getHost();
        } catch (Exception ignored) {}

        String htmlBase;
        try {
            htmlBase = readRawString(R.raw.webview_htmlbase);
        } catch(Exception ignored) {
            // TODO: show error message
            finish();
            return;
        }

        String htmlPage = String.format(htmlBase, cssName, classAttr, titleText,
                originalUrlText, domainText, htmlContent);

        String httpAuthHostTemp = settings.getUrl();
        try {
            httpAuthHostTemp = new URL(httpAuthHostTemp).getHost();
        } catch (Exception ignored) {}
        final String httpAuthHost = httpAuthHostTemp;
        final String httpAuthUsername = settings.getString(Settings.HTTP_AUTH_USERNAME, null);
        final String httpAuthPassword = settings.getString(Settings.HTTP_AUTH_PASSWORD, null);

        scrollView = (ScrollView) findViewById(R.id.scroll);
        webViewContent = (WebView) findViewById(R.id.webViewContent);
        webViewContent.getSettings().setJavaScriptEnabled(true); // TODO: make optional?
        webViewContent.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                boolean result = false;
                if (ttsFragment != null) {
                    result = ttsFragment.onWebviewConsoleMessage(cm);
                }
                if ( ! result) {
                    Log.d("WebView.onCM", String.format("%s @ %d: %s", cm.message(),
                            cm.lineNumber(), cm.sourceId()));
                }
                return true;
            }
        });
        webViewContent.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                ReadArticleActivity.this.onPageFinished();

                super.onPageFinished(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                if (!url.equals(originalUrlText)) {
                    return openUrl(url);
                } else { // If we try to open current URL, do not propose to save it, directly open browser
                    Intent launchBrowserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(launchBrowserIntent);
                    return true;
                }
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                  String host, String realm) {
                Log.d(TAG, "onReceivedHttpAuthRequest() host: " + host + ", realm: " + realm);
                if(host != null && host.contains(httpAuthHost)) {
                    Log.d(TAG, "onReceivedHttpAuthRequest() host match");
                    handler.proceed(httpAuthUsername, httpAuthPassword);
                } else {
                    Log.d(TAG, "onReceivedHttpAuthRequest() host mismatch");
                    super.onReceivedHttpAuthRequest(view, handler, host, realm);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if(acceptAllSSLCerts) {
                    handler.proceed();
                } else {
                    super.onReceivedSslError(view, handler, error);
                }
            }

        });

        if(fontSize != 100) setFontSize(webViewContent, fontSize);


        if (ttsFragment != null) {
                ttsFragment.onDocumentLoadStart(domainText, titleText);
        }

        webViewContent.loadDataWithBaseURL("file:///android_asset/", htmlPage,
                "text/html", "utf-8", null);

        // TODO: remove logging after calibrated
        GestureDetector.SimpleOnGestureListener gestureListener
                = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // note: e1 - previous event, e2 - current event
                // velocity* - velocity in pixels per second

                if(e1 == null || e2 == null) return false;
                if(e1.getPointerCount() > 1 || e2.getPointerCount() > 1) return false;

//                if(Math.abs(e1.getY() - e2.getY()) > 150) {
//                    Log.d("FLING", "not a horizontal fling (distance)");
//                    return false; // not a horizontal move (distance)
//                }

                if(Math.abs(velocityX) < 80) {
                    Log.d("FLING", "too slow");
                    return false; // too slow
                }

                if(Math.abs(velocityX / velocityY) < 3) {
                    Log.d("FLING", "not a horizontal fling");
                    return false; // not a horizontal fling
                }

                float diff = e1.getX() - e2.getX();

                if(Math.abs(diff) < 80) { // configurable
                    Log.d("FLING", "too small distance");
                    return false; // too small distance
                }

                if(diff > 0) { // right-to-left: next
                    Log.d("FLING", "right-to-left: next");
                    openNextArticle();
                } else { // left-to-right: prev
                    Log.d("FLING", "left-to-right: prev");
                    openPreviousArticle();
                }
                return true;
            }
        };

        final GestureDetector gestureDetector = new GestureDetector(this, gestureListener);

        webViewContent.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });

        loadingPlaceholder = (TextView) findViewById(R.id.tv_loading_article);
        bottomTools = (LinearLayout) findViewById(R.id.bottomTools);
        hrBar = findViewById(R.id.view1);

        previousArticleID = getAdjacentArticle(true);
        nextArticleID = getAdjacentArticle(false);

        Button btnMarkRead = (Button) findViewById(R.id.btnMarkRead);
        if(mArticle.getArchive()) {
            btnMarkRead.setText(R.string.btnMarkUnread);
        }
        btnMarkRead.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                markAsReadAndClose();
            }
        });

        ImageButton btnGoPrevious;
        ImageButton btnGoNext;
        btnGoPrevious = (ImageButton) findViewById(R.id.btnGoPrevious);
        if(previousArticleID == null) {
            btnGoPrevious.setVisibility(View.GONE);
        }
        btnGoNext = (ImageButton) findViewById(R.id.btnGoNext);
        if(nextArticleID == null) {
            btnGoNext.setVisibility(View.GONE);
        }
        btnGoPrevious.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openPreviousArticle();
            }
        });
        btnGoNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openNextArticle();
            }
        });

        if (settings.getBoolean(Settings.TTS_VISIBLE, false) && (ttsFragment == null)) {
            ttsFragment = (TtsFragment)getSupportFragmentManager().findFragmentByTag("ttsFragment");
            if (ttsFragment == null) {
                toggleTTS(false);
            }
        }
    }

    private void loadingFinished() {
        loadingFinished = true;

        loadingPlaceholder.setVisibility(View.GONE);
        bottomTools.setVisibility(View.VISIBLE);
        hrBar.setVisibility(View.VISIBLE);

        // should there be a pause between visibility change and position restoration?

        restoreReadingPosition();
        if (ttsFragment != null) {
            ttsFragment.onDocumentLoadFinished(webViewContent, scrollView);
        }
    }

    private boolean openUrl(final String url) {
        if(url == null) return true;

        // TODO: fancy dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View v = getLayoutInflater().inflate(R.layout.dialog_title_url, null);
        TextView tv = (TextView) v.findViewById(R.id.tv_dialog_title_url);
        tv.setText(url);
        builder.setCustomTitle(v);

        builder.setItems(
                new CharSequence[]{
                        getString(R.string.d_urlAction_openInBrowser),
                        getString(R.string.d_urlAction_addToWallabag)
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                Intent launchBrowserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                startActivity(launchBrowserIntent);
                                break;
                            case 1:
                                new AddLinkTask(url, getApplicationContext()).execute();
                                break;
                        }
                    }
                });
        builder.show();

        return true;
    }

    private void markAsReadAndClose() {
        new ToggleArchiveTask(getApplicationContext(),
                mArticle.getArticleId(), mArticleDao, mArticle).execute();

        finish();
    }

    private boolean toggleFavorite() {
        new ToggleFavoriteTask(getApplicationContext(),
                mArticle.getArticleId(), mArticleDao, mArticle).execute();

        return true;
    }

    private boolean shareArticle() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, titleText);
        send.putExtra(Intent.EXTRA_TEXT, originalUrlText + getString(R.string.share_text_extra));
        startActivity(Intent.createChooser(send, getString(R.string.share_article_title)));
        return true;
    }

    private boolean deleteArticle() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.d_deleteArticle_title);
        b.setMessage(R.string.d_deleteArticle_message);
        b.setPositiveButton(R.string.positive_answer, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new DeleteArticleTask(getApplicationContext(),
                        mArticle.getArticleId(), mArticleDao, mArticle).execute();

                finish();
            }
        });
        b.setNegativeButton(R.string.negative_answer, null);
        b.create().show();

        return true;
    }

    private boolean openOriginal() {
        Intent launchBrowserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrlText));
        startActivity(launchBrowserIntent);

        return true;
    }

    private boolean downloadPdf() {
        Log.d(TAG, "downloadPdf()");

        File exportDir = getExternalFilesDir(null);
        if(exportDir != null) {
            new DownloadPdfTask(getApplicationContext(), mArticle.getArticleId(),
                    mArticleDao, mArticle, exportDir.getAbsolutePath()).execute();
        } else {
            Log.w(TAG, "downloadPdf() exportDir is null");
        }

        return true;
    }

    private void openArticle(Long id) {
        if (ttsFragment != null) {
            ttsFragment.onOpenNewArticle();
        }
        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(ReadArticleActivity.EXTRA_ID, id);
        if(contextFavorites != null) intent.putExtra(EXTRA_LIST_FAVORITES, contextFavorites);
        if(contextArchived != null) intent.putExtra(EXTRA_LIST_ARCHIVED, contextArchived);
        startActivity(intent);
    }

    public boolean openPreviousArticle() {
        if(previousArticleID != null) {
            openArticle(previousArticleID);
            return true;
        }

        Toast.makeText(this, R.string.noPreviousArticle, Toast.LENGTH_SHORT).show();
        return false;
    }

    public boolean openNextArticle() {
        if(nextArticleID != null) {
            openArticle(nextArticleID);
            return true;
        }

        Toast.makeText(this, R.string.noNextArticle, Toast.LENGTH_SHORT).show();
        return false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_article, menu);

        boolean unread = mArticle.getArchive() != null && !mArticle.getArchive();

        MenuItem markReadItem = menu.findItem(R.id.menuArticleMarkAsRead);
        markReadItem.setTitle(unread ? R.string.btnMarkRead : R.string.btnMarkUnread);

        boolean favorite = mArticle.getFavorite() != null && mArticle.getFavorite();

        MenuItem toggleFavoriteItem = menu.findItem(R.id.menuArticleToggleFavorite);
        toggleFavoriteItem.setTitle(
                favorite ? R.string.remove_from_favorites : R.string.add_to_favorites);
        // TODO: replace star icon
        toggleFavoriteItem.setIcon(getIcon(favorite
                        ? R.drawable.abc_btn_rating_star_on_mtrl_alpha
                        : R.drawable.abc_btn_rating_star_off_mtrl_alpha, null)
        );

        menuTTS = menu.findItem(R.id.menuTTS);
        menuTTS.setChecked(ttsFragment != null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuArticleMarkAsRead:
                markAsReadAndClose();
                return true;
            case R.id.menuArticleToggleFavorite:
                toggleFavorite();
                invalidateOptionsMenu();
                return true;
            case R.id.menuShare:
                return shareArticle();
            case R.id.menuDelete:
                return deleteArticle();
            case R.id.menuOpenOriginal:
                return openOriginal();
            case R.id.menuDownloadPdf:
                return downloadPdf();
            case R.id.menuIncreaseFontSize:
                changeFontSize(true);
                return true;
            case R.id.menuDecreaseFontSize:
                changeFontSize(false);
                return true;
            case R.id.menuTTS:
                toggleTTS(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStop() {
        if(loadingFinished && mArticle != null) {
            cancelPositionRestoration();

            mArticle.setArticleProgress(getReadingPosition());
            mArticleDao.update(mArticle);
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        isResumed = false;
    }
    @Override
    public void onResume() {
        super.onResume();
        isResumed = true;
        if (onPageFinishedCallPostponedUntilResume) {
            onPageFinishedCallPostponedUntilResume = false;
            onPageFinished();
        }
    }


    private double getReadingPosition() {
        String t = "ReadArticle.getPos";

        int yOffset = scrollView.getScrollY();
        int viewHeight = scrollView.getHeight();
        int totalHeight = scrollView.getChildAt(0).getHeight();
        // id/btnMarkRead height; not necessary; insignificantly increases accuracy
//        int appendixHeight = ((LinearLayout)view.getChildAt(0)).getChildAt(1).getHeight();
        Log.d(t, "yOffset: " + yOffset + ", viewHeight: " + viewHeight + ", totalHeight: " + totalHeight);

//        totalHeight -= appendixHeight;
        totalHeight -= viewHeight;

        double progress = totalHeight >= 0 ? yOffset * 1. / totalHeight : 0;
        Log.d(t, "progress: " + progress);

        return progress;
    }

    private void restoreReadingPosition() {
        String t = "ReadArticle.restorePos";

        Log.d(t, "positionToRestore: " + positionToRestore);
        if(positionToRestore != null) {
            int viewHeight = scrollView.getHeight();
//            int appendixHeight = ((LinearLayout)view.getChildAt(0)).getChildAt(1).getHeight();
            int totalHeight = scrollView.getChildAt(0).getHeight();
            Log.d(t, "viewHeight: " + viewHeight + ", totalHeight: " + totalHeight);

//            totalHeight -= appendixHeight;
            totalHeight -= viewHeight;

            int yOffset = totalHeight > 0 ? ((int)Math.round(positionToRestore * totalHeight)) : 0;
            Log.d(t, "yOffset: " + yOffset);

            scrollView.scrollTo(scrollView.getScrollX(), yOffset);
        }
    }

    public boolean toggleTTS(boolean autoPlay) {
        boolean result;
        if (ttsFragment == null) {
            ttsFragment = TtsFragment.newInstance(autoPlay);
            getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.viewMain, ttsFragment, "ttsFragment")
                .commit();
            settings.setBoolean(Settings.TTS_VISIBLE, true);
            ttsFragment.onDocumentLoadStart(domainText, titleText);
            if (loadingFinished) {
                ttsFragment.onDocumentLoadFinished(webViewContent, scrollView);
            }
            result = true;
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(ttsFragment)
                    .commit();
            ttsFragment = null;
            settings.setBoolean(Settings.TTS_VISIBLE, false);
            result = false;
        }
        if (menuTTS != null) {
            menuTTS.setChecked(ttsFragment != null);
        }
        return result;
    }


    private Long getAdjacentArticle(boolean previous) {
        QueryBuilder<Article> qb = mArticleDao.queryBuilder();

        if(previous) qb.where(ArticleDao.Properties.ArticleId.gt(mArticle.getArticleId()));
        else qb.where(ArticleDao.Properties.ArticleId.lt(mArticle.getArticleId()));

        if(contextFavorites != null) qb.where(ArticleDao.Properties.Favorite.eq(contextFavorites));
        if(contextArchived != null) qb.where(ArticleDao.Properties.Archive.eq(contextArchived));

        if(previous) qb.orderAsc(ArticleDao.Properties.ArticleId);
        else qb.orderDesc(ArticleDao.Properties.ArticleId);

        List<Article> l = qb.limit(1).list();
        if(!l.isEmpty()) {
            return l.get(0).getId();
        }

        return null;
    }

    private String readRawString(int id) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(getResources().openRawResource(id)));

            StringBuilder sb = new StringBuilder();
            String s;
            while((s = reader.readLine()) != null) {
                sb.append(s).append('\n');
            }

            return sb.toString();
        } finally {
            if(reader != null) {
                reader.close();
            }
        }
    }

    private void onPageFinished() {
        if ( ! isResumed) {
            onPageFinishedCallPostponedUntilResume = true;
            if (ttsFragment != null) {
                ttsFragment.onDocumentLoadFinished(webViewContent, scrollView);
            }
            return;
        }
        // dirty. Looks like there is no good solution
        webViewContent.postDelayed(new Runnable() {
            int counter;

            @Override
            public void run() {
                // "< 50" is workaround for https://github.com/wallabag/android-app/issues/178
                if(webViewContent.getHeight() < 50) {
                    if(++counter > 1000) {
                        Log.d(TAG, "onPageFinished() exiting by counter" +
                                "; calling loadingFinished() anyway");
                        loadingFinished();
                        return;
                    }

                    Log.v(TAG, "onPageFinished() scheduling another postDelay; counter: " + counter);
                    webViewContent.postDelayed(this, 10);
                } else {
                    Log.d(TAG, "onPageFinished() calling loadingFinished()");
                    loadingFinished();
                }
            }
        }, 10);
    }

    private void prepareToRestorePosition(boolean savePosition) {
        if(savePosition) positionToRestore = getReadingPosition();

        webViewHeightBeforeUpdate = webViewContent.getHeight();
    }

    private void restorePositionAfterUpdate() {
        cancelPositionRestoration();

        webViewContent.postDelayed(positionRestorationRunnable = new Runnable() {
            int counter;

            @Override
            public void run() {
                if(webViewContent.getHeight() == webViewHeightBeforeUpdate) {
                    if(++counter > 1000) {
                        Log.d(TAG, "restorePositionAfterUpdate() giving up");
                        return;
                    }

                    Log.v(TAG, "restorePositionAfterUpdate() scheduling another postDelay" +
                            "; counter: " + counter);
                    webViewContent.postDelayed(this, 10);
                } else {
                    Log.d(TAG, "restorePositionAfterUpdate() restoring position");
                    restoreReadingPosition();
                }
            }
        }, 10);
    }

    private void cancelPositionRestoration() {
        if(positionRestorationRunnable != null) {
            Log.d(TAG, "cancelPositionRestoration() trying to cancel previous task");
            if(webViewContent != null) webViewContent.removeCallbacks(positionRestorationRunnable);
            positionRestorationRunnable = null;
        }
    }

    private void changeFontSize(boolean increase) {
        prepareToRestorePosition(true);

        int step = 5;
        fontSize += step * (increase ? 1 : -1);
        if(!increase && fontSize < 5) fontSize = 5;

        setFontSize(webViewContent, fontSize);

        settings.setInt(Settings.FONT_SIZE, fontSize);

        restorePositionAfterUpdate();
    }

    private void setFontSize(WebView view, int size) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            setFontSizeNew(view, size);
        } else {
            setFontSizeOld(view, size);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setFontSizeNew(WebView view, int size) {
        view.getSettings().setTextZoom(size);
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private void setFontSizeOld(WebView view, int size) {
        view.getSettings().setDefaultFontSize(size);
    }

    private Drawable getIcon(int id, Resources.Theme theme) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getIconNew(id, theme);
        }

        return getIconOld(id);
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private Drawable getIconOld(int id) {
        return getResources().getDrawable(id);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Drawable getIconNew(int id, Resources.Theme theme) {
        return getResources().getDrawable(id, theme);
    }

}
