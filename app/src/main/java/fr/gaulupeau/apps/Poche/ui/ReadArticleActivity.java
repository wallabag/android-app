package fr.gaulupeau.apps.Poche.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import wallabag.apiwrapper.WallabagService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.greendao.query.QueryBuilder;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.tts.TtsFragment;

import static android.text.Html.escapeHtml;

public class ReadArticleActivity extends BaseActionBarActivity {

    public static final String EXTRA_ID = "ReadArticleActivity.id";
    public static final String EXTRA_LIST_ARCHIVED = "ReadArticleActivity.archived";
    public static final String EXTRA_LIST_FAVORITES = "ReadArticleActivity.favorites";

    private static final String TAG = ReadArticleActivity.class.getSimpleName();

    private static final String TAG_TTS_FRAGMENT = "ttsFragment";

    private static final EnumSet<ArticlesChangedEvent.ChangeType> CHANGE_SET_ACTIONS = EnumSet.of(
            ArticlesChangedEvent.ChangeType.FAVORITED,
            ArticlesChangedEvent.ChangeType.UNFAVORITED,
            ArticlesChangedEvent.ChangeType.ARCHIVED,
            ArticlesChangedEvent.ChangeType.UNARCHIVED);

    private static final EnumSet<ArticlesChangedEvent.ChangeType> CHANGE_SET_CONTENT = EnumSet.of(
            ArticlesChangedEvent.ChangeType.CONTENT_CHANGED,
            ArticlesChangedEvent.ChangeType.TITLE_CHANGED,
            ArticlesChangedEvent.ChangeType.DOMAIN_CHANGED,
            ArticlesChangedEvent.ChangeType.PUBLISHED_AT_CHANGED,
            ArticlesChangedEvent.ChangeType.AUTHORS_CHANGED,
            ArticlesChangedEvent.ChangeType.URL_CHANGED,
            ArticlesChangedEvent.ChangeType.ESTIMATED_READING_TIME_CHANGED,
//            ArticlesChangedEvent.ChangeType.ANNOTATIONS_CHANGED, TODO: fix: own changes will cause reload
            ArticlesChangedEvent.ChangeType.FETCHED_IMAGES_CHANGED);

    private static final EnumSet<ArticlesChangedEvent.ChangeType> CHANGE_SET_PREV_NEXT = EnumSet.of(
            ArticlesChangedEvent.ChangeType.UNSPECIFIED,
            ArticlesChangedEvent.ChangeType.ADDED,
            ArticlesChangedEvent.ChangeType.DELETED,
            ArticlesChangedEvent.ChangeType.ARCHIVED,
            ArticlesChangedEvent.ChangeType.UNARCHIVED,
            ArticlesChangedEvent.ChangeType.FAVORITED,
            ArticlesChangedEvent.ChangeType.UNFAVORITED,
            ArticlesChangedEvent.ChangeType.CREATED_DATE_CHANGED);

    private Boolean contextFavorites;
    private Boolean contextArchived;

    private Settings settings;

    private ArticleDao articleDao;

    private boolean fullscreenArticleView;
    private int fontSize;
    private boolean volumeButtonsScrolling;
    private boolean tapToScroll;
    private boolean disableTouchOptionEnabled;
    private boolean disableTouch;
    private int disableTouchKeyCode;
    private float screenScrollingPercent;
    private boolean smoothScrolling;
    private int scrolledOverBottom;
    private boolean swipeArticles;
    private boolean annotationsEnabled;

    private ScrollView scrollView;
    private View scrollViewLastChild;
    private WebView webViewContent;
    private TextView loadingPlaceholder;
    private LinearLayout bottomTools;
    private View hrBar;
    private TtsFragment ttsFragment;

    private Article article;
    private String articleTitle;
    private String articleDomain;
    private String articleUrl;
    private String articleLanguage;
    private Double articleProgress;

    private Long previousArticleID;
    private Long nextArticleID;

    private int webViewHeightBeforeUpdate;
    private Runnable positionRestorationRunnable;

    private boolean isResumed;
    private boolean onPageFinishedCallPostponedUntilResume;
    private boolean loadingFinished;

    // TODO: remove after updating to ~"androidx.appcompat:appcompat:1.2.0"
    // https://issuetracker.google.com/issues/141132133
    // https://stackoverflow.com/q/58028821
    // workaround: https://github.com/ankidroid/Anki-Android/issues/5507#issuecomment-543501300
    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT <= 25) {
            return;
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    public void onCreate(Bundle savedInstanceState) {
        settings = App.getInstance().getSettings();

        fullscreenArticleView = settings.isFullscreenArticleView();
        if(fullscreenArticleView) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        if(settings.isKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.article);

        if(fullscreenArticleView) {
            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null) actionBar.hide();
        }

        Intent intent = getIntent();
        long articleID = intent.getLongExtra(EXTRA_ID, -1);
        Log.d(TAG, "onCreate() articleId: " + articleID);
        if(intent.hasExtra(EXTRA_LIST_FAVORITES)) {
            contextFavorites = intent.getBooleanExtra(EXTRA_LIST_FAVORITES, false);
        }
        if(intent.hasExtra(EXTRA_LIST_ARCHIVED)) {
            contextArchived = intent.getBooleanExtra(EXTRA_LIST_ARCHIVED, false);
        }

        DaoSession session = DbConnection.getSession();
        articleDao = session.getArticleDao();

        if(!loadArticle(articleID)) {
            Log.e(TAG, "onCreate: Did not find article with ID: " + articleID);
            finish();
            return;
        }

        fontSize = settings.getArticleFontSize();
        volumeButtonsScrolling = settings.isVolumeButtonsScrollingEnabled();
        tapToScroll = settings.isTapToScrollEnabled();
        disableTouchOptionEnabled = settings.isDisableTouchEnabled();
        disableTouch = settings.isDisableTouchLastState();
        disableTouchKeyCode = settings.getDisableTouchKeyCode();
        screenScrollingPercent = settings.getScreenScrollingPercent();
        smoothScrolling = settings.isScreenScrollingSmooth();
        scrolledOverBottom = settings.getScrolledOverBottom();
        swipeArticles = settings.getSwipeArticles();
        annotationsEnabled = settings.isAnnotationsEnabled();

        setTitle(articleTitle);

        // article is loaded - update menu
        invalidateOptionsMenu();

        scrollView = (ScrollView)findViewById(R.id.scroll);
        scrollViewLastChild = scrollView.getChildAt(scrollView.getChildCount() - 1);
        webViewContent = (WebView)findViewById(R.id.webViewContent);
        loadingPlaceholder = (TextView)findViewById(R.id.tv_loading_article);
        bottomTools = (LinearLayout)findViewById(R.id.bottomTools);
        hrBar = findViewById(R.id.view1);

        initWebView();

        if(ttsFragment != null) {
            // is it ever executed?
            ttsFragment.onDocumentLoadStart(articleDomain, articleTitle, articleLanguage);
        }

        loadArticleToWebView();

        initButtons();

        if(settings.isTtsVisible() && ttsFragment == null) {
            ttsFragment = (TtsFragment)getSupportFragmentManager()
                    .findFragmentByTag(TAG_TTS_FRAGMENT);

            if(ttsFragment == null) {
                toggleTTS(false);
            }
        }

        if(disableTouch) {
            showDisableTouchToast();
        }

        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        isResumed = true;
        if(onPageFinishedCallPostponedUntilResume) {
            onPageFinishedCallPostponedUntilResume = false;

            onPageFinished();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        isResumed = false;
    }

    @Override
    public void onStop() {
        if(loadingFinished && article != null) {
            cancelPositionRestoration();

            OperationsHelper.setArticleProgress(this, article.getArticleId(), getReadingPosition());
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        Log.d(TAG, "onCreateOptionsMenu() started");

        getMenuInflater().inflate(R.menu.option_article, menu);

        if(article != null) {
            boolean unread = article.getArchive() != null && !article.getArchive();
            menu.findItem(R.id.menuArticleMarkAsRead).setVisible(unread);
            menu.findItem(R.id.menuArticleMarkAsUnread).setVisible(!unread);

            boolean favorite = article.getFavorite() != null && article.getFavorite();
            menu.findItem(R.id.menuArticleFavorite).setVisible(!favorite);
            menu.findItem(R.id.menuArticleUnfavorite).setVisible(favorite);
        }

        menu.findItem(R.id.menuTTS).setChecked(ttsFragment != null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menuArticleMarkAsRead:
            case R.id.menuArticleMarkAsUnread:
                markAsReadAndClose();
                break;

            case R.id.menuArticleFavorite:
            case R.id.menuArticleUnfavorite:
                toggleFavorite();
                break;

            case R.id.menuShare:
                shareArticle();
                break;

            case R.id.menuChangeTitle:
                showChangeTitleDialog();
                break;

            case R.id.menuManageTags:
                manageTags();
                break;

            case R.id.menuDelete:
                deleteArticle();
                break;

            case R.id.menuOpenOriginal:
                openOriginal();
                break;

            case R.id.menuCopyOriginalURL:
                copyURLToClipboard();
                break;

            case R.id.menuDownloadAsFile:
                showDownloadFileDialog();
                break;

            case R.id.menuIncreaseFontSize:
                changeFontSize(true);
                break;

            case R.id.menuDecreaseFontSize:
                changeFontSize(false);
                break;

            case R.id.menuTTS:
                toggleTTS(true);
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean triggerAction;
        if (code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_PAGE_DOWN) {
            triggerAction = (event.getAction() == KeyEvent.ACTION_UP);
        } else {
            triggerAction = (event.getAction() == KeyEvent.ACTION_DOWN);
        }

        if (triggerAction) {
            if(code == disableTouchKeyCode && (disableTouch || disableTouchOptionEnabled)) {
                disableTouch = !disableTouch;
                settings.setDisableTouchLastState(disableTouch);

                Log.d(TAG, "toggling touch screen, now disableTouch is " + disableTouch);
                showDisableTouchToast();
                return true;
            }

            boolean scroll = false;
            boolean up = false;

            switch (code) {
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    scroll = true;
                    up = code == KeyEvent.KEYCODE_PAGE_UP;
                    break;

                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    scroll = true;
                    up = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT;
                    break;

                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (volumeButtonsScrolling) {
                        scroll = true;
                        up = code == KeyEvent.KEYCODE_VOLUME_UP;
                    }
                    break;
            }

            if (scroll) {
                scroll(up, screenScrollingPercent, smoothScrolling, true);
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return disableTouch || super.dispatchTouchEvent(ev);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (annotationsEnabled) {
            Menu menu = mode.getMenu();

            mode.getMenuInflater().inflate(R.menu.read_article_activity_annotate, menu);

            MenuItem item = menu.findItem(R.id.menu_annotate);
            item.setOnMenuItemClickListener(i -> {
                webViewContent.evaluateJavascript("invokeAnnotator();", null);
//                mode.finish(); // seems to reset selection too early (not on emulator though)
                return true;
            });
        }

        super.onActionModeStarted(mode);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onArticlesChangedEvent(ArticlesChangedEvent event) {
        Log.d(TAG, "onArticlesChangedEvent() started");

        boolean updatePrevNext = false;
        if (event.containsAny(CHANGE_SET_PREV_NEXT)) {
            updatePrevNext = true;
        } else {
            EnumSet<ArticlesChangedEvent.ChangeType> changes;
            if(contextArchived != null) {
                changes = contextArchived ? event.getArchiveFeedChanges() : event.getMainFeedChanges();
            } else if(contextFavorites != null && contextFavorites) {
                changes = event.getFavoriteFeedChanges();
            } else {
                changes = EnumSet.copyOf(event.getMainFeedChanges());
                changes.addAll(event.getArchiveFeedChanges());
                changes.addAll(event.getFavoriteFeedChanges());
            }

            if (ArticlesChangedEvent.containsAny(changes, CHANGE_SET_PREV_NEXT)) {
                updatePrevNext = true;
            }
        }

        if(updatePrevNext) {
            Log.d(TAG, "onArticleChangedEvent() prev/next buttons changed");

            updatePrevNextButtons();
        }

        EnumSet<ArticlesChangedEvent.ChangeType> changes = event.getArticleChanges(article);
        if(changes == null) return;

        Log.d(TAG, "onArticlesChangedEvent() changes: " + changes);

        boolean updateActions;
        boolean updateContent;
        boolean updateTitle;
        boolean updateURL;

        if(changes.contains(FeedsChangedEvent.ChangeType.UNSPECIFIED)) {
            updateActions = true;
            updateContent = true;
            updateTitle = true;
            updateURL = true;
        } else {
            updateActions = ArticlesChangedEvent.containsAny(changes, CHANGE_SET_ACTIONS);
            updateContent = ArticlesChangedEvent.containsAny(changes, CHANGE_SET_CONTENT);
            updateTitle = changes.contains(FeedsChangedEvent.ChangeType.TITLE_CHANGED);
            updateURL = changes.contains(FeedsChangedEvent.ChangeType.URL_CHANGED);
        }

        if(updateActions) {
            Log.d(TAG, "onArticleChangedEvent() actions changed");

            updateMarkAsReadButtonView();

            invalidateOptionsMenu();
        }

        if(updateTitle) {
            Log.d(TAG, "onArticleChangedEvent() title changed");

            articleTitle = article.getTitle();
            setTitle(articleTitle);
        }

        if(updateURL) {
            Log.d(TAG, "onArticleChangedEvent() URL changed");

            articleUrl = article.getUrl();
        }

        if(updateContent) {
            Log.d(TAG, "onArticleChangedEvent() content changed");

//            prepareToRestorePosition(true);

            loadArticleToWebView();

//            restorePositionAfterUpdate();
        }
    }

    private void showDisableTouchToast() {
        Toast.makeText(this, disableTouch
                        ? R.string.message_disableTouch_inputDisabled
                        : R.string.message_disableTouch_inputEnabled,
                Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        webViewContent.getSettings().setJavaScriptEnabled(true);

        JsAnnotationController annotationController = new JsAnnotationController(
                new JsAnnotationController.Callback() {
                    @Override
                    public List<Annotation> getAnnotations() {
                        return article.getAnnotations();
                    }

                    @Override
                    public Annotation createAnnotation(Annotation annotation) {
                        OperationsHelper.addAnnotation(
                                ReadArticleActivity.this, article.getArticleId(), annotation);
                        return annotation;
                    }

                    @Override
                    public Annotation updateAnnotation(Annotation annotation) {
                        OperationsHelper.updateAnnotation(
                                ReadArticleActivity.this, article.getArticleId(), annotation);
                        return annotation;
                    }

                    @Override
                    public Annotation deleteAnnotation(Annotation annotation) {
                        OperationsHelper.deleteAnnotation(
                                ReadArticleActivity.this, article.getArticleId(), annotation);
                        return annotation;
                    }
                });

        webViewContent.addJavascriptInterface(annotationController, "hostAnnotationController");

        webViewContent.setWebChromeClient(new WebChromeClient() {
            private View customView;

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                boolean result = false;
                if(ttsFragment != null) {
                    result = ttsFragment.onWebViewConsoleMessage(cm);
                }
                if(!result) {
                    Log.d("WebView.onCM", String.format("%s @ %d: %s", cm.message(),
                            cm.lineNumber(), cm.sourceId()));
                }
                return true;
            }

            //Necessary for enabling watching youtube videos in fullscreen
            public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback) {
                customView = paramView;

                //Hide action bar and bottom buttons while in fullscreen
                hideUi(true);

                FrameLayout.LayoutParams fullscreen = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                ((FrameLayout) ReadArticleActivity.this.getWindow().getDecorView())
                        .addView(customView, fullscreen);
            }

            //Necessary for enabling watching youtube videos in fullscreen
            public void onHideCustomView() {
                //Show action bar and bottom buttons when leaving fullscreen
                hideUi(false);

                ((FrameLayout) ReadArticleActivity.this.getWindow().getDecorView())
                        .removeView(customView);
            }

            private void hideUi(boolean hide) {
                if(!fullscreenArticleView) {
                    ActionBar actionBar = getSupportActionBar();
                    if(actionBar != null) {
                        if(hide) actionBar.hide();
                        else actionBar.show();
                    }
                }
                LinearLayout bottom = findViewById(R.id.bottomTools);
                if(bottom != null) bottom.setVisibility(hide ? View.GONE : View.VISIBLE);
            }
        });

        webViewContent.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                ReadArticleActivity.this.onPageFinished();

                super.onPageFinished(view, url);
            }

            @SuppressWarnings("deprecation") // can't use newer method until API 21
            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                // If we try to open current URL, do not propose to save it, directly open browser
                if(url.equals(articleUrl)) {
                    openURL(url);
                } else {
                    handleUrlClicked(url);
                }

                return true; // always override URL loading
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                  String host, String realm) {
                Log.d(TAG, "onReceivedHttpAuthRequest() host: " + host + ", realm: " + realm);

                if(!TextUtils.isEmpty(host)) {
                    String httpAuthHost = settings.getUrl();
                    try {
                        httpAuthHost = new URL(httpAuthHost).getHost();
                    } catch(Exception ignored) {}

                    if(host.contains(httpAuthHost)) {
                        Log.d(TAG, "onReceivedHttpAuthRequest() host match");
                        handler.proceed(settings.getHttpAuthUsername(), settings.getHttpAuthPassword());
                        return;
                    }
                }

                super.onReceivedHttpAuthRequest(view, handler, host, realm);
            }

        });

        if(fontSize != 100) setFontSize(webViewContent, fontSize);

        GestureDetector.SimpleOnGestureListener gestureListener
                = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // note: e1 - previous event, e2 - current event
                // velocity* - velocity in pixels per second

                if(!swipeArticles) return false;
                if(e1 == null || e2 == null) return false;
                if(e1.getPointerCount() > 1 || e2.getPointerCount() > 1) return false;

//                if(Math.abs(e1.getY() - e2.getY()) > 150) {
//                    Log.d("FLING", "not a horizontal fling (distance)");
//                    return false; // not a horizontal move (distance)
//                }

                if(Math.abs(velocityX) < 80) {
                    Log.v("FLING", "too slow");
                    return false; // too slow
                }

                if(Math.abs(velocityX / velocityY) < 3) {
                    Log.v("FLING", "not a horizontal fling");
                    return false; // not a horizontal fling
                }

                float diff = e1.getX() - e2.getX();

                if(Math.abs(diff) < 80) { // configurable
                    Log.v("FLING", "too small distance");
                    return false; // too small distance
                }

                if(diff > 0) { // right-to-left: next
                    Log.v("FLING", "right-to-left: next");
                    openNextArticle();
                } else { // left-to-right: prev
                    Log.v("FLING", "left-to-right: prev");
                    openPreviousArticle();
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if(!tapToScroll) return false;

                if(e.getPointerCount() > 1) return false;

                int viewHeight = scrollView.getHeight();
                float y = e.getY() - scrollView.getScrollY();

                if(y > viewHeight * 0.25 && y < viewHeight * 0.75) {
                    int viewWidth = scrollView.getWidth();
                    float x = e.getX();

                    if(x < viewWidth * 0.3) { // left part
                        scroll(true, screenScrollingPercent, smoothScrolling, false);
                    } else if(x > viewWidth * 0.7) { // right part
                        scroll(false, screenScrollingPercent, smoothScrolling, false);
                    }
                }

                return false;
            }
        };

        final GestureDetector gestureDetector = new GestureDetector(this, gestureListener);

        webViewContent.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
    }

    private void loadArticleToWebView() {
        webViewContent.loadDataWithBaseURL("file:///android_asset/", getHtmlPage(),
                "text/html", "utf-8", null);
    }

    private String getHtmlPage() {
        String cssName;
        boolean highContrast = false;
        boolean weightedFont = false;
        switch(Themes.getCurrentTheme()) {
            case E_INK:
                weightedFont = true;
            case LIGHT_CONTRAST:
                highContrast = true;
            case LIGHT:
            default:
                cssName = "main";
                break;

            case DARK_CONTRAST:
                highContrast = true;
            case DARK:
                cssName = "dark";
                break;

            case SOLARIZED:
                cssName = "solarized";
                highContrast = false;
                break;
        }

        List<String> additionalClasses = new ArrayList<>(1);
        if(highContrast) additionalClasses.add("high-contrast");
        if(weightedFont) additionalClasses.add("weighted-font");
        if(settings.isArticleFontSerif()) additionalClasses.add("serif-font");
        if(settings.isArticleTextAlignmentJustify()) additionalClasses.add("text-align-justify");
        additionalClasses.add(settings.getHandlePreformattedTextOption());

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

        String htmlBase;
        try {
            htmlBase = readRawString(R.raw.webview_htmlbase);
        } catch(Exception e) {
            // should not happen
            throw new RuntimeException("Couldn't load raw resource", e);
        }

        String extraHead = getExtraHead();
        String header = getHeader();
        String htmlContent = getHtmlContent();

        return String.format(htmlBase, cssName, classAttr, escapeHtml(articleTitle),
                escapeHtml(articleUrl), escapeHtml(articleDomain), header, htmlContent, extraHead);
    }

    private String getExtraHead() {
        String extra = "";

        if (annotationsEnabled) {
            extra += "\n" +
                    "\t\t<script src=\"annotator.min.js\"></script>" +
                    "\n" +
                    "\t\t<script src=\"annotations-android-app.js\"></script>";
        }

        return extra;
    }

    private String getHeader() {
        StringBuilder header = new StringBuilder();

        Date publishedAt = article.getPublishedAt();
        if (publishedAt != null) {
            header.append(android.text.format.DateFormat.getDateFormat(this).format(publishedAt))
                    .append(' ')
                    .append(android.text.format.DateFormat.getTimeFormat(this).format(publishedAt));
        }

        if (!TextUtils.isEmpty(article.getAuthors())) {
            header.append(' ');
            header.append(escapeHtml(article.getAuthors()));
        }

        header.append("<br>\n");

        int estimatedReadingTime = article.getEstimatedReadingTime(settings.getReadingSpeed());
        header.append(escapeHtml(getString(R.string.content_estimatedReadingTime,
                estimatedReadingTime > 0 ? estimatedReadingTime : "< 1")));

        if (settings.isPreviewImageEnabled() && !TextUtils.isEmpty(article.getPreviewPictureURL())) {
            header.append("<br>\n");
            header.append("<img src=\"")
                    .append(escapeHtml(article.getPreviewPictureURL()))
                    .append("\"/>");
        }

        String headerString = header.toString();

        if (BuildConfig.DEBUG) Log.d(TAG, "getHeader() headerString: " + headerString);

        return doImageUrlReplacements(headerString);
    }

    private String getHtmlContent() {
        String htmlContent = article.getContent();

        if (BuildConfig.DEBUG) Log.d(TAG, "getHtmlContent() htmlContent: " + htmlContent);

        return doImageUrlReplacements(htmlContent);
    }

    private String doImageUrlReplacements(String content) {
        if (settings.isImageCacheEnabled()) {
            Log.d(TAG, "doImageUrlReplacements() replacing image links to cached versions");
            content = ImageCacheUtils.replaceImagesInHtmlContent(
                    content, article.getArticleId().longValue());
        }

        return ImageCacheUtils.replaceWallabagRelativeImgUrls(content);
    }

    private void initButtons() {
        updateMarkAsReadButtonView();
        updatePrevNextButtons();
    }

    private void updateMarkAsReadButtonView() {
        Button buttonMarkRead = (Button)findViewById(R.id.btnMarkRead);
        Button buttonMarkUnread = (Button)findViewById(R.id.btnMarkUnread);

        boolean archived = article.getArchive();
        buttonMarkRead.setVisibility(!archived ? View.VISIBLE: View.GONE);
        buttonMarkUnread.setVisibility(archived ? View.VISIBLE: View.GONE);

        OnClickListener onClickListener =
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        markAsReadAndClose();
                    }
                };

        buttonMarkRead.setOnClickListener(onClickListener);
        buttonMarkUnread.setOnClickListener(onClickListener);
    }

    private void updatePrevNextButtons() {
        previousArticleID = getAdjacentArticle(true);
        nextArticleID = getAdjacentArticle(false);

        updatePrevNextButtonViews();
    }

    private void updatePrevNextButtonViews() {
        ImageButton buttonGoPrevious = (ImageButton)findViewById(R.id.btnGoPrevious);
        ImageButton buttonGoNext = (ImageButton)findViewById(R.id.btnGoNext);

        buttonGoPrevious.setVisibility(previousArticleID == null ? View.GONE : View.VISIBLE);
        buttonGoNext.setVisibility(nextArticleID == null ? View.GONE : View.VISIBLE);

        buttonGoPrevious.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openPreviousArticle();
            }
        });
        buttonGoNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openNextArticle();
            }
        });
    }

    private void loadingFinished() {
        loadingFinished = true;

        loadingPlaceholder.setVisibility(View.GONE);
        bottomTools.setVisibility(View.VISIBLE);
        hrBar.setVisibility(View.VISIBLE);

        // should there be a pause between visibility change and position restoration?

        restoreReadingPosition();

        if(ttsFragment != null) {
            ttsFragment.onDocumentLoadFinished(webViewContent, scrollView);
        }
    }

    private void handleUrlClicked(final String url) {
        Log.d(TAG, "handleUrlClicked() url: " + url);
        if(TextUtils.isEmpty(url)) return;

        // TODO: fancy dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        @SuppressLint("InflateParams") // it's ok to inflate with null for AlertDialog
        View v = getLayoutInflater().inflate(R.layout.dialog_title_url, null);

        TextView tv = (TextView)v.findViewById(R.id.tv_dialog_title_url);
        tv.setText(url);

        builder.setCustomTitle(v);

        builder.setItems(
                new CharSequence[]{
                        getString(R.string.d_urlAction_openInBrowser),
                        getString(R.string.d_urlAction_addToWallabag),
                        getString(R.string.d_urlAction_copyToClipboard),
                        getString(R.string.menuShare)
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                openURL(url);
                                break;
                            case 1:
                                OperationsHelper.addArticleWithUI(
                                        ReadArticleActivity.this, url, articleUrl);
                                break;
                            case 2:
                                copyURLToClipboard(url);
                                break;
                            case 3:
                                shareArticle(null, url);
                                break;
                        }
                    }
                });

        builder.show();
    }

    private void openURL(String url) {
        Log.d(TAG, "openURL() url: " + url);
        if(TextUtils.isEmpty(url)) return;

        Uri uri = Uri.parse(url);
        if(uri.getScheme() == null) {
            Log.i(TAG, "openURL() scheme is null, appending default scheme");
            uri = Uri.parse("http://" + url);
        }
        Log.d(TAG, "openURL() uri: " + uri);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch(ActivityNotFoundException e) {
            Log.w(TAG, "openURL() failed to open URL", e);
            Toast.makeText(this, R.string.message_couldNotOpenUrl, Toast.LENGTH_SHORT).show();
        }
    }

    private void markAsReadAndClose() {
        OperationsHelper.archiveArticle(this, article.getArticleId(), !article.getArchive());

        finish();
    }

    private void toggleFavorite() {
        OperationsHelper.favoriteArticle(this, article.getArticleId(), !article.getFavorite());
    }

    private void shareArticle() {
       shareArticle(articleTitle, articleUrl);
    }

    private void shareArticle(String articleTitle , String articleUrl) {
        String shareText = articleUrl;
        if(!TextUtils.isEmpty(articleTitle)) shareText = articleTitle + " " + shareText;


        if(settings.isAppendWallabagMentionEnabled()) {
            shareText += getString(R.string.share_text_extra);
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        if(!TextUtils.isEmpty(articleTitle)) send.putExtra(Intent.EXTRA_SUBJECT, articleTitle);
        send.putExtra(Intent.EXTRA_TEXT, shareText);

        startActivity(Intent.createChooser(send, getString(R.string.share_article_title)));
    }

    private void deleteArticle() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);

        b.setTitle(R.string.d_deleteArticle_title);
        b.setMessage(R.string.d_deleteArticle_message);

        b.setPositiveButton(R.string.positive_answer, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                OperationsHelper.deleteArticle(ReadArticleActivity.this, article.getArticleId());

                finish();
            }
        });
        b.setNegativeButton(R.string.negative_answer, null);

        b.show();
    }

    private void showChangeTitleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        @SuppressLint("InflateParams") // ok for dialogs
        final View view = getLayoutInflater().inflate(R.layout.dialog_change_title, null);

        ((TextView)view.findViewById(R.id.editText_title)).setText(articleTitle);

        builder.setView(view);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TextView textView = (TextView)view.findViewById(R.id.editText_title);
                changeTitle(textView.getText().toString());
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        builder.show();
    }

    private void changeTitle(String title) {
        OperationsHelper.changeArticleTitle(this, article.getArticleId(), title);
    }

    private void manageTags() {
        Intent manageTagsIntent = new Intent(this, ManageArticleTagsActivity.class);
        manageTagsIntent.putExtra(ManageArticleTagsActivity.PARAM_ARTICLE_ID, article.getArticleId());

        startActivity(manageTagsIntent);
    }

    private void openOriginal() {
        openURL(articleUrl);
    }

    private void copyURLToClipboard() {
       copyURLToClipboard(articleUrl);
    }

    private void copyURLToClipboard(String url) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData urlClipData = ClipData.newPlainText("article URL", url);
        clipboardManager.setPrimaryClip(urlClipData);
        Toast.makeText(this, R.string.txtUrlCopied, Toast.LENGTH_SHORT).show();
    }

    private void showDownloadFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_downloadFileFormat)
                .setItems(R.array.options_downloadFormat_values,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedFormat = getResources()
                                .getStringArray(R.array.options_downloadFormat_values)[which];

                        WallabagService.ResponseFormat format;
                        try {
                            format = WallabagService.ResponseFormat.valueOf(selectedFormat);
                        } catch(IllegalArgumentException e) {
                            Log.e(TAG, "showDownloadFileDialog() unknown selected format: "
                                    + selectedFormat);
                            format = WallabagService.ResponseFormat.PDF;
                        }

                        OperationsHelper.downloadArticleAsFile(getApplicationContext(),
                                article.getArticleId(), format, null);
                    }
                });
        builder.show();
    }

    private void changeFontSize(boolean increase) {
        prepareToRestorePosition(true);

        int step = 5;
        fontSize += step * (increase ? 1 : -1);
        if(!increase && fontSize < 5) fontSize = 5;

        setFontSize(webViewContent, fontSize);

        settings.setArticleFontSize(fontSize);

        restorePositionAfterUpdate();
    }

    private void openArticle(Long id) {
        if(ttsFragment != null) {
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

    private void scroll(boolean up, float percent, boolean smooth, boolean keyUsed) {
        if(scrollView == null) return;

        int viewHeight = scrollView.getHeight();
        int yOffset = scrollView.getScrollY();

        int newYOffset = yOffset;
        int step = (int)(viewHeight * percent / 100);
        if(up) {
            newYOffset -= step;
        } else {
            newYOffset += step;
        }

        if(newYOffset != yOffset) {
            if(smooth) {
                scrollView.smoothScrollTo(scrollView.getScrollX(), newYOffset);
            } else {
                scrollView.scrollTo(scrollView.getScrollX(), newYOffset);
            }
        }

        if(!up && keyUsed && newYOffset + viewHeight > scrollViewLastChild.getBottom()) {
            if(scrolledOverBottom > 1) {
                scrolledOverBottom--;
                Toast.makeText(this, getString(R.string.scrolledOverBottom, scrolledOverBottom),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.markedAsRead, Toast.LENGTH_SHORT).show();
                markAsReadAndClose();
            }
        } else {
            scrolledOverBottom = settings.getScrolledOverBottom();
        }
    }

    private double getReadingPosition() {
        int yOffset = scrollView.getScrollY();
        int viewHeight = scrollView.getHeight();
        int totalHeight = scrollView.getChildAt(0).getHeight();

        Log.v(TAG, "getReadingPosition() yOffset: " + yOffset + ", viewHeight: " + viewHeight
                + ", totalHeight: " + totalHeight);

        totalHeight -= viewHeight;

        double position = totalHeight >= 0 ? yOffset * 1. / totalHeight : 0;
        if(position > 100) position = 100;

        Log.d(TAG, "getReadingPosition() position: " + position);

        return position;
    }

    private void restoreReadingPosition() {
        Log.d(TAG, "restoreReadingPosition() articleProgress: " + articleProgress);

        if(articleProgress != null) {
            int viewHeight = scrollView.getHeight();
            int totalHeight = scrollView.getChildAt(0).getHeight();

            Log.v(TAG, "restoreReadingPosition() viewHeight: " + viewHeight
                    + ", totalHeight: " + totalHeight);

            totalHeight -= viewHeight;

            int yOffset = totalHeight > 0 ? ((int)Math.round(articleProgress * totalHeight)) : 0;

            Log.v(TAG, "restoreReadingPosition() yOffset: " + yOffset);

            scrollView.scrollTo(scrollView.getScrollX(), yOffset);
        }
    }

    public boolean toggleTTS(boolean autoPlay) {
        boolean result;
        if(ttsFragment == null) {
            ttsFragment = TtsFragment.newInstance(autoPlay);

            getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.viewMain, ttsFragment, TAG_TTS_FRAGMENT)
                .commit();

            settings.setTtsVisible(true);

            ttsFragment.onDocumentLoadStart(articleDomain, articleTitle, articleLanguage);
            if(loadingFinished) {
                ttsFragment.onDocumentLoadFinished(webViewContent, scrollView);
            }

            result = true;
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(ttsFragment)
                    .commit();

            ttsFragment = null;

            settings.setTtsVisible(false);

            result = false;
        }

        invalidateOptionsMenu();

        return result;
    }

    private boolean loadArticle(long id) {
        article = getArticle(id);

        if(article == null) return false;

        articleTitle = article.getTitle();
        Log.d(TAG, "loadArticle() articleTitle: " + articleTitle);
        articleDomain = article.getDomain();
        Log.d(TAG, "loadArticle() articleDomain: " + articleDomain);
        articleUrl = article.getUrl();
        Log.d(TAG, "loadArticle() articleUrl: " + articleUrl);
        articleProgress = article.getArticleProgress();
        Log.d(TAG, "loadArticle() articleProgress: " + articleProgress);
        articleLanguage = article.getLanguage();
        Log.d(TAG, "loadArticle() articleLanguage: " + articleLanguage);

        return true;
    }

    private Article getArticle(long articleID) {
        return articleDao.queryBuilder().where(ArticleDao.Properties.Id.eq(articleID)).unique();
    }

    private Long getAdjacentArticle(boolean previous) {
        QueryBuilder<Article> qb = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.isNotNull());

        if(previous) qb.where(ArticleDao.Properties.ArticleId.gt(article.getArticleId()));
        else qb.where(ArticleDao.Properties.ArticleId.lt(article.getArticleId()));

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
                try {
                    reader.close();
                } catch(IOException ignored) {}
            }
        }
    }

    private void onPageFinished() {
        Log.d(TAG, "onPageFinished() started");

        if(!isResumed) {
            onPageFinishedCallPostponedUntilResume = true;

            if(ttsFragment != null) {
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
        if(savePosition) articleProgress = getReadingPosition();

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

}
