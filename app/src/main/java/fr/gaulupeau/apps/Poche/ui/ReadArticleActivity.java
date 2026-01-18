package fr.gaulupeau.apps.Poche.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
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
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.greendao.query.QueryBuilder;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.StorageHelper;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.tts.JsTtsController;
import fr.gaulupeau.apps.Poche.tts.TtsFragment;
import fr.gaulupeau.apps.Poche.tts.TtsHost;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.widget.Toolbar;

import static android.text.Html.escapeHtml;

public class ReadArticleActivity extends AppCompatActivity {

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
            ArticlesChangedEvent.ChangeType.TAG_SET_CHANGED,
            ArticlesChangedEvent.ChangeType.TAGS_CHANGED_GLOBALLY,
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

    private ArticleActionsHelper articleActionsHelper = new ArticleActionsHelper();

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
    private boolean onyxWorkaroundEnabled;

    private NestedScrollView scrollView;
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
    private Double articleProgress;

    private Long previousArticleID;
    private Long nextArticleID;

    private TtsHost ttsHost;
    private JsTtsController jsTtsController;

    private int webViewHeightBeforeUpdate;
    private Runnable positionRestorationRunnable;

    private boolean isResumed;
    private boolean onPageFinishedCallPostponedUntilResume;
    private boolean loadingFinished;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);

        // not sure if it is relevant to WebView
        WallabagConnection.initConscrypt();

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        settings = App.getSettings();

        fullscreenArticleView = settings.isFullscreenArticleView();
        if (fullscreenArticleView) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        if (settings.isKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.article);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (fullscreenArticleView) {
            Objects.requireNonNull(getSupportActionBar()).hide();
            toolbar.setVisibility(View.GONE);
        } else {    // enable and handle the back button in the toolbar
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        Intent intent = getIntent();
        long articleID = intent.getLongExtra(EXTRA_ID, -1);
        Log.d(TAG, "onCreate() articleId: " + articleID);

        if (intent.hasExtra(EXTRA_LIST_FAVORITES)) {
            contextFavorites = intent.getBooleanExtra(EXTRA_LIST_FAVORITES, false);
        }
        if (intent.hasExtra(EXTRA_LIST_ARCHIVED)) {
            contextArchived = intent.getBooleanExtra(EXTRA_LIST_ARCHIVED, false);
        }

        articleDao = DbConnection.getSession().getArticleDao();

        if (!loadArticle(articleID)) {
            Log.e(TAG, "onCreate() did not find article with ID: " + articleID);
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
        onyxWorkaroundEnabled = settings.isOnyxWorkaroundEnabled();

        setTitle(articleTitle);

        // article is loaded - update menu
        invalidateOptionsMenu();

        scrollView = findViewById(R.id.scroll);
        scrollViewLastChild = scrollView.getChildAt(scrollView.getChildCount() - 1);
        webViewContent = findViewById(R.id.webViewContent);
        loadingPlaceholder = findViewById(R.id.tv_loading_article);
        bottomTools = findViewById(R.id.bottomTools);
        hrBar = findViewById(R.id.view1);

        initButtons();

        initWebView();

        loadArticleToWebView();

        initTts();

        if (disableTouch) {
            showDisableTouchToast();
        }

        EventHelper.register(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent()");

        // a proper reinitialization is needed to do without a restart

        finish();
        startActivity(intent);
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

    @Override
    public void onPause() {
        super.onPause();

        isResumed = false;
    }

    @Override
    public void onStop() {
        if (loadingFinished && article != null) {
            cancelPositionRestoration();

            OperationsHelper.setArticleProgress(this, article.getArticleId(), getReadingPosition());
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        removeTtsContainerHeightListener();
        EventHelper.unregister(this);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        Log.d(TAG, "onCreateOptionsMenu() started");

        getMenuInflater().inflate(R.menu.option_article, menu);

        if (article != null) articleActionsHelper.initMenu(menu, article);

        menu.findItem(R.id.menuTTS).setChecked(ttsFragment != null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuArticleMarkAsRead:
            case R.id.menuArticleMarkAsUnread:
                markAsReadAndClose();
                return true;

            case R.id.menuDelete:
                deleteArticle();
                return true;

            case R.id.menuIncreaseFontSize:
                changeFontSize(true);
                return true;

            case R.id.menuDecreaseFontSize:
                changeFontSize(false);
                return true;

            case R.id.menuTTS:
                toggleTTS(true);
                return true;
        }

        if (articleActionsHelper.handleContextItemSelected(this, article, item)) return true;

        return super.onOptionsItemSelected(item);
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
            if (code == disableTouchKeyCode && (disableTouch || disableTouchOptionEnabled)) {
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
        Menu menu = mode.getMenu();
        mode.getMenuInflater().inflate(R.menu.read_article_activity, menu);
        menu.findItem(R.id.menu_tag).setOnMenuItemClickListener(item -> {
            webViewContent.evaluateJavascript(
                    "(function(){return window.getSelection().toString()})()",
                    this::createTagFromSelection);
            mode.finish();
            return true;
        });

        MenuItem annotateItem = menu.findItem(R.id.menu_annotate);
        if (annotationsEnabled) {
            annotateItem.setOnMenuItemClickListener(i -> {
                webViewContent.evaluateJavascript("invokeAnnotator();", null);
//                mode.finish(); // seems to reset selection too early (not on emulator though)
                return true;
            });
        } else {
            annotateItem.setVisible(false);
        }
        // refresh menu content
        mode.invalidate();
        super.onActionModeStarted(mode);
    }

    private void createTagFromSelection(String selection) {
        selection = selection.replaceAll("^\"|\"$", "")
                .replaceAll("\\\\n|\\\\r|\\\\t", " ");

        Intent intent = new Intent(this, ManageArticleTagsActivity.class);
        intent.putExtra(ManageArticleTagsActivity.PARAM_ARTICLE_ID, article.getArticleId());
        intent.putExtra(ManageArticleTagsActivity.PARAM_TAG_LABEL, selection);
        startActivity(intent);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onArticlesChangedEvent(ArticlesChangedEvent event) {
        Log.d(TAG, "onArticlesChangedEvent() started");

        boolean updatePrevNext = false;
        if (event.containsAny(CHANGE_SET_PREV_NEXT)) {
            updatePrevNext = true;
        } else {
            EnumSet<ArticlesChangedEvent.ChangeType> changes;
            if (contextArchived != null) {
                changes = contextArchived ? event.getArchiveFeedChanges() : event.getMainFeedChanges();
            } else if (contextFavorites != null && contextFavorites) {
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

        if (updatePrevNext) {
            Log.d(TAG, "onArticleChangedEvent() prev/next buttons changed");

            updatePrevNextButtons();
        }

        EnumSet<ArticlesChangedEvent.ChangeType> changes = event.getArticleChanges(article);
        if (changes == null) return;

        Log.d(TAG, "onArticlesChangedEvent() changes: " + changes);

        boolean updateActions;
        boolean updateContent;
        boolean updateTitle;
        boolean updateURL;

        if (changes.contains(FeedsChangedEvent.ChangeType.UNSPECIFIED)) {
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

        if (updateActions) {
            Log.d(TAG, "onArticleChangedEvent() actions changed");

            updateMarkAsReadButton();

            invalidateOptionsMenu();
        }

        if (updateTitle) {
            Log.d(TAG, "onArticleChangedEvent() title changed");

            articleTitle = article.getTitle();
            setTitle(articleTitle);
        }

        if (updateURL) {
            Log.d(TAG, "onArticleChangedEvent() URL changed");

            articleUrl = article.getUrl();
        }

        if (updateContent) {
            Log.d(TAG, "onArticleChangedEvent() content changed");

//            prepareToRestorePosition(true);

            loadArticleToWebView();

            initTtsForArticle();

//            restorePositionAfterUpdate();
        }
    }

    public TtsHost getTtsHost() {
        if (ttsHost == null) {
            ttsHost = new TtsHost() {
                @Override
                public JsTtsController getJsTtsController() {
                    return jsTtsController;
                }

                @Override
                public WebView getWebView() {
                    return webViewContent;
                }

                @Override
                public int getScrollY() {
                    return scrollView.getScrollY();
                }

                @Override
                public int getViewHeight() {
                    return scrollView.getHeight();
                }

                @Override
                public void scrollTo(int y) {
                    if (smoothScrolling) {
                        scrollView.smoothScrollTo(scrollView.getScrollX(), y);
                    } else {
                        scrollView.scrollTo(scrollView.getScrollX(), y);
                    }
                }

                @Override
                public boolean previousArticle() {
                    return openPreviousArticle();
                }

                @Override
                public boolean nextArticle() {
                    return openNextArticle();
                }
            };
        }
        return ttsHost;
    }

    private void showDisableTouchToast() {
        Toast.makeText(this, disableTouch
                        ? R.string.message_disableTouch_inputDisabled
                        : R.string.message_disableTouch_inputEnabled,
                Toast.LENGTH_SHORT).show();
    }

    private void initButtons() {
        initMarkAsReadButtonView();
        intiDeleteButtonView();
        initPrevNextButtons();
    }

    private void initMarkAsReadButtonView() {
        Button buttonMarkRead = findViewById(R.id.btnMarkRead);
        Button buttonMarkUnread = findViewById(R.id.btnMarkUnread);

        OnClickListener onClickListener = v -> markAsReadAndClose();

        buttonMarkRead.setOnClickListener(onClickListener);
        buttonMarkUnread.setOnClickListener(onClickListener);

        updateMarkAsReadButton();
    }

    private void updateMarkAsReadButton() {
        boolean archived = article.getArchive();

        findViewById(R.id.btnMarkRead).setVisibility(!archived ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnMarkUnread).setVisibility(archived ? View.VISIBLE : View.GONE);
    }

    private void intiDeleteButtonView() {
        Button buttonDelete = findViewById(R.id.btnDelete);

        buttonDelete.setOnClickListener(v -> deleteArticle());
    }

    private void initPrevNextButtons() {
        ImageButton buttonGoPrevious = findViewById(R.id.btnGoPrevious);
        ImageButton buttonGoNext = findViewById(R.id.btnGoNext);

        buttonGoPrevious.setOnClickListener(v -> openPreviousArticle());
        buttonGoNext.setOnClickListener(v -> openNextArticle());

        updatePrevNextButtons();
    }

    private void updatePrevNextButtons() {
        previousArticleID = getAdjacentArticle(true);
        nextArticleID = getAdjacentArticle(false);

        findViewById(R.id.btnGoPrevious).setVisibility(previousArticleID == null ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnGoNext).setVisibility(nextArticleID == null ? View.GONE : View.VISIBLE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings webViewSettings = webViewContent.getSettings();
        webViewSettings.setJavaScriptEnabled(true);

        if (settings.isImageCacheEnabled() && !webViewSettings.getAllowFileAccess()) {
            Log.d(TAG, "initWebView() enabling WebView file access");
            webViewSettings.setAllowFileAccess(true);
        }

        initTtsController();
        initAnnotationController();

        webViewContent.setWebChromeClient(new WebChromeClient() {
            private View customView;

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("WebView.onCM", String.format("%s @ %d: %s", cm.message(),
                        cm.lineNumber(), cm.sourceId()));
                return true;
            }

            //Necessary for enabling watching youtube videos in fullscreen
            public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback) {
                customView = paramView;

                //Hide action bar and bottom buttons while in fullscreen
                FrameLayout.LayoutParams fullscreen = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                ((FrameLayout) ReadArticleActivity.this.getWindow().getDecorView())
                        .addView(customView, fullscreen);
            }

            //Necessary for enabling watching youtube videos in fullscreen
            public void onHideCustomView() {
                //Show action bar and bottom buttons when leaving fullscreen

                ((FrameLayout) ReadArticleActivity.this.getWindow().getDecorView())
                        .removeView(customView);
            }
        });

        webViewContent.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                ReadArticleActivity.this.onPageFinished();

                super.onPageFinished(view, url);
            }

            @SuppressWarnings("deprecation") // the suggested method is not called before API 24
            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                // If we try to open current URL, do not propose to save it, directly open browser
                if (url.equals(articleUrl)) {
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

                if (!TextUtils.isEmpty(host)) {
                    String httpAuthHost = settings.getUrl();
                    try {
                        httpAuthHost = new URL(httpAuthHost).getHost();
                    } catch (Exception ignored) {}

                    if (host.contains(httpAuthHost)) {
                        Log.d(TAG, "onReceivedHttpAuthRequest() host match");
                        handler.proceed(settings.getHttpAuthUsername(), settings.getHttpAuthPassword());
                        return;
                    }
                }

                super.onReceivedHttpAuthRequest(view, handler, host, realm);
            }

        });

        if (fontSize != 100) setFontSize(fontSize);

        GestureDetector.SimpleOnGestureListener gestureListener
                = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // note: e1 - previous event, e2 - current event
                // velocity* - velocity in pixels per second

                if (!swipeArticles) return false;

                if (e1 == null || e2 == null) return false;
                if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1) return false;

//                if (Math.abs(e1.getY() - e2.getY()) > 150) {
//                    Log.d("FLING", "not a horizontal fling (distance)");
//                    return false; // not a horizontal move (distance)
//                }

                if (Math.abs(velocityX) < 80) {
                    Log.v("FLING", "too slow");
                    return false; // too slow
                }

                if (Math.abs(velocityX / velocityY) < 3) {
                    Log.v("FLING", "not a horizontal fling");
                    return false; // not a horizontal fling
                }

                float diff = e1.getX() - e2.getX();

                if (Math.abs(diff) < 80) { // configurable
                    Log.v("FLING", "too small distance");
                    return false; // too small distance
                }

                if (diff > 0) { // right-to-left: next
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
                if (!tapToScroll) return false;

                if (e.getPointerCount() > 1) return false;

                int viewHeight = scrollView.getHeight();
                float y = e.getY() - scrollView.getScrollY();

                if (y > viewHeight * 0.25 && y < viewHeight * 0.75) {
                    int viewWidth = scrollView.getWidth();
                    float x = e.getX();

                    if (x < viewWidth * 0.3) { // left part
                        scroll(true, screenScrollingPercent, smoothScrolling, false);
                    } else if (x > viewWidth * 0.7) { // right part
                        scroll(false, screenScrollingPercent, smoothScrolling, false);
                    }
                }

                return false;
            }
        };

        final GestureDetector gestureDetector = new GestureDetector(this, gestureListener);

        webViewContent.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private void initTtsController() {
        // add the controller now even if TTS is not used,
        // otherwise it won't be possible to enable TTS without content reloading
        jsTtsController = new JsTtsController();
        webViewContent.addJavascriptInterface(jsTtsController, "hostWebViewTextController");
    }

    private void initAnnotationController() {
        if (!annotationsEnabled) return;

        JsAnnotationController annotationController = new JsAnnotationController(
                new JsAnnotationController.Callback() {
                    @Override
                    public List<Annotation> getAnnotations() {
                        return article.getAnnotations();
                    }

                    @Override
                    public Annotation createAnnotation(Annotation annotation) { // TODO: fix: waiting call
                        return waitForFuture(OperationsHelper.addAnnotation(
                                ReadArticleActivity.this, article.getArticleId(), annotation));
                    }

                    @Override
                    public Annotation updateAnnotation(Annotation annotation) {
                        OperationsHelper.updateAnnotation(ReadArticleActivity.this,
                                article.getArticleId(), annotation);
                        return annotation;
                    }

                    @Override
                    public Annotation deleteAnnotation(Annotation annotation) {
                        OperationsHelper.deleteAnnotation(ReadArticleActivity.this,
                                article.getArticleId(), annotation);
                        return annotation;
                    }

                    private <T> T waitForFuture(Future<T> future) {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            Log.w("JsAnnotCtrl.Callback", "waitForFuture() exception", e);
                        }
                        return null;
                    }
                });

        webViewContent.addJavascriptInterface(annotationController, "hostAnnotationController");
    }

    private void loadArticleToWebView() {
        String htmlPage = getHtmlPage();

        loadingFinished = false;
        webViewContent.loadDataWithBaseURL("file:///android_asset/", htmlPage,
                "text/html", "utf-8", null);
    }

    private String getHtmlPage() {
        String cssName;
        boolean highContrast = false;
        boolean weightedFont = false;
        switch (Themes.getCurrentTheme()) {
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

            case DAY_NIGHT_CONTRAST:
                highContrast = true;
            case DAY_NIGHT:
                cssName = "main";

                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    cssName = "dark";
                    break;
                }

                break;

        }

        List<String> additionalClasses = new ArrayList<>();
        if (highContrast) additionalClasses.add("high-contrast");
        if (weightedFont) additionalClasses.add("weighted-font");
        if (settings.isArticleFontSerif()) additionalClasses.add("serif-font");
        if (settings.isArticleTextAlignmentJustify()) additionalClasses.add("text-align-justify");
        additionalClasses.add(settings.getHandlePreformattedTextOption());

        String classAttr;
        if (!additionalClasses.isEmpty()) {
            StringBuilder sb = new StringBuilder();

            sb.append(" class=\"");
            for (String cl : additionalClasses) {
                sb.append(cl).append(' ');
            }
            sb.append('"');

            classAttr = sb.toString();
        } else {
            classAttr = "";
        }

        String htmlBase = getHtmlBase();

        String extraHead = getExtraHead();
        String stats = getStats();
        String tags = getTags();
        String previewImage = getPreviewImage();
        String htmlContent = getHtmlContent();

        return String.format(htmlBase, cssName, extraHead, classAttr, escapeHtml(articleTitle),
                escapeHtml(articleUrl), escapeHtml(articleDomain), stats, tags, previewImage, htmlContent);
    }

    private String getHtmlBase() {
        return StorageHelper.readRawString(R.raw.webview_htmlbase);
    }

    private String getExtraHead() {
        String extra = "";

        if (annotationsEnabled) {
            extra += "\n" +
                    "\t\t<script src=\"annotator.min.js\"></script>" +
                    "\n" +
                    "\t\t<script src=\"annotations-android-app.js\"></script>";
        }

        if (onyxWorkaroundEnabled) {
            extra += "\n" +
                    "\t\t<script src=\"onyx-style-workaround.js\"></script>";
        }

        if (settings.isMathRenderingEnabled()) {
            String delimiters = TextUtils.join(",", settings.getMathRenderingDelimiters());
            extra += String.format(StorageHelper.readRawString(R.raw.katex_part), delimiters);
        }

        return extra;
    }

    private String getStats() {
        StringBuilder stats = new StringBuilder();

        stats.append("<li>");
        // Material icon 'today'
        stats.append("\t<i class=\"material-icons no-tts\">&#xE8DF</i>");
        stats.append(android.text.format.DateFormat.getDateFormat(this).format(article.getCreationDate()))
                .append(' ')
                .append(android.text.format.DateFormat.getTimeFormat(this).format(article.getCreationDate()));
        stats.append("</li>");


        Date publishedAt = article.getPublishedAt();
        if (publishedAt != null) {
            stats.append("<li>");
            // Material icon 'today'
            stats.append("\t<i class=\"material-icons no-tts\">&#xE3C9</i>");
            stats.append(android.text.format.DateFormat.getDateFormat(this).format(publishedAt))
                    .append(' ')
                    .append(android.text.format.DateFormat.getTimeFormat(this).format(publishedAt));
            stats.append("</li>");
        }

        if (!TextUtils.isEmpty(article.getAuthors())) {
            stats.append("<li>");
            // Material icon 'person'
            stats.append("\t<i class=\"material-icons no-tts\">&#xE7FD</i>");
            stats.append(escapeHtml(article.getAuthors()));
            stats.append("</li>");
        }

        int estimatedReadingTime = article.getEstimatedReadingTime(settings.getReadingSpeed());
        stats.append("<li>");
        // Material icon 'timer'
        stats.append("\t<i class=\"material-icons no-tts\">&#xE425</i>");
        stats.append("<span class=\"tts-only\">")
                .append(getString(R.string.content_estimatedReadingTimeTTSLabel))
                .append("</span>");
        stats.append(escapeHtml(getString(R.string.content_estimatedReadingTime,
                estimatedReadingTime > 0 ? estimatedReadingTime : "< 1")));
        stats.append("</li>");

        String statsString = stats.toString();

        if (BuildConfig.DEBUG) Log.d(TAG, "getStats() statsString: " + statsString);
        return statsString;
    }

    private String getPreviewImage() {
        StringBuilder previewImage = new StringBuilder();

        if (settings.isPreviewImageEnabled() && !TextUtils.isEmpty(article.getPreviewPictureURL())) {
            previewImage.append("<img")
                    .append(" alt=\"")
                    .append(escapeHtml(getString(R.string.articleContent_previewImageAltText)))
                    .append('"')
                    .append(" src=\"")
                    .append(escapeHtml(article.getPreviewPictureURL()))
                    .append("\"/>");
        }

        String previewImageString = previewImage.toString();

        if (BuildConfig.DEBUG) Log.d(TAG, "getPreviewImage() previewImageString: " + previewImageString);

        return doImageUrlReplacements(previewImageString);
    }

    private String getTags() {
        StringBuilder tags = new StringBuilder();

        if (!article.getTags().isEmpty()) {
            Tag.sortTagListByLabel(article.getTags());
            tags.append("<div class=\"tags\">");

            for (Tag tag : article.getTags()) {
                tags.append("<div class=\"tag\">");
                tags.append("<a href=\"tag://").append(tag.getId()).append("\">")
                        .append(escapeHtml(tag.getLabel()))
                        .append("</a>");
                tags.append("</div>");
            }
            tags.append("</div>");
        }
        String tagsString = tags.toString();
        if (BuildConfig.DEBUG) Log.d(TAG, "getTags() tagsString: " + tagsString);

        return tagsString;
    }

    private String getHtmlContent() {
        String htmlContent = article.getContent();

        if (htmlContent == null) {
            Log.w(TAG, "getHtmlContent() content is null for articleId: "
                    + article.getArticleId());

            htmlContent = getString(R.string.contentIsTooLong);
        }

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

    private void loadingFinished() {
        loadingFinished = true;

        loadingPlaceholder.setVisibility(View.GONE);
        bottomTools.setVisibility(View.VISIBLE);
        hrBar.setVisibility(View.VISIBLE);

        // should there be a pause between visibility change and position restoration?

        restoreReadingPosition();

        ttsOnDocumentLoadingFinished();
    }

    private void handleUrlClicked(final String url) {
        Log.d(TAG, "handleUrlClicked() url: " + url);
        if (TextUtils.isEmpty(url)) return;

        if (handleTagClicked(url)) return;

        @SuppressLint("InflateParams") // it's ok to inflate with null for AlertDialog
        View v = getLayoutInflater().inflate(R.layout.dialog_title_url, null);

        v.<TextView>findViewById(R.id.tv_dialog_title_url).setText(url);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setCustomTitle(v);

        builder.setItems(
                new CharSequence[]{
                        getString(R.string.d_urlAction_openInBrowser),
                        getString(R.string.d_urlAction_addToWallabag),
                        getString(R.string.d_urlAction_copyToClipboard),
                        getString(R.string.menuShare)
                }, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openURL(url);
                            break;
                        case 1:
                            OperationsHelper.addArticleWithUI(this, url, articleUrl);
                            break;
                        case 2:
                            copyUrlToClipboard(url);
                            break;
                        case 3:
                            shareArticle(url);
                            break;
                    }
                });

        builder.show();
    }

    private boolean handleTagClicked(String url) {
        final String tagUrlPrefix = "tag://";

        if (!url.startsWith(tagUrlPrefix)) return false;

        long tagId;
        try {
            tagId = Long.parseLong(url.substring(tagUrlPrefix.length()));
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "handleTagClicked() couldn't handle tag URL: " + url);
            return true;
        }

        Tag tag = null;
        for (Tag t : article.getTags()) {
            if (t.getId() == tagId) {
                tag = t;
                break;
            }
        }

        if (tag == null) {
            Log.w(TAG, "handleTagClicked() couldn't find tag by ID: " + tagId);
            return true;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.PARAM_TAG_LABEL, tag.getLabel());

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);

        return true;
    }

    private void openURL(String url) {
        articleActionsHelper.openUrl(this, url);
    }

    private void markAsReadAndClose() {
        articleActionsHelper.archive(this, article, !article.getArchive());

        finish();
    }

    private void shareArticle(String articleUrl) {
        articleActionsHelper.shareArticle(this, null, articleUrl);
    }

    private void deleteArticle() {
        articleActionsHelper.showDeleteArticleDialog(this, article, this::finish);
    }

    private void copyUrlToClipboard(String url) {
        articleActionsHelper.copyUrlToClipboard(this, url);
    }

    private void changeFontSize(boolean increase) {
        prepareToRestorePosition(true);

        int step = 5;
        fontSize += step * (increase ? 1 : -1);
        if (!increase && fontSize < 5) fontSize = 5;

        setFontSize(fontSize);

        settings.setArticleFontSize(fontSize);

        restorePositionAfterUpdate();
    }

    private void openArticle(Long id) {
        if (ttsFragment != null) {
            ttsFragment.onOpenNewArticle();
        }

        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(ReadArticleActivity.EXTRA_ID, id);
        if (contextFavorites != null) intent.putExtra(EXTRA_LIST_FAVORITES, contextFavorites);
        if (contextArchived != null) intent.putExtra(EXTRA_LIST_ARCHIVED, contextArchived);

        startActivity(intent);
    }

    public boolean openPreviousArticle() {
        if (previousArticleID != null) {
            openArticle(previousArticleID);
            return true;
        }

        Toast.makeText(this, R.string.noPreviousArticle, Toast.LENGTH_SHORT).show();
        return false;
    }

    public boolean openNextArticle() {
        if (nextArticleID != null) {
            openArticle(nextArticleID);
            return true;
        }

        Toast.makeText(this, R.string.noNextArticle, Toast.LENGTH_SHORT).show();
        return false;
    }

    private void scroll(boolean up, float percent, boolean smooth, boolean keyUsed) {
        if (scrollView == null) return;

        int viewHeight = scrollView.getHeight();
        int yOffset = scrollView.getScrollY();

        int newYOffset = yOffset;
        int step = (int) (viewHeight * percent / 100);
        if (up) {
            newYOffset -= step;
        } else {
            newYOffset += step;
        }

        if (newYOffset != yOffset) {
            if (smooth) {
                scrollView.smoothScrollTo(scrollView.getScrollX(), newYOffset);
            } else {
                scrollView.scrollTo(scrollView.getScrollX(), newYOffset);
            }
        }

        if (!up && keyUsed && newYOffset + viewHeight > scrollViewLastChild.getBottom()) {
            if (scrolledOverBottom > 1) {
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
        if (position > 100) position = 100;

        Log.d(TAG, "getReadingPosition() position: " + position);

        return position;
    }

    private void restoreReadingPosition() {
        Log.d(TAG, "restoreReadingPosition() articleProgress: " + articleProgress);

        if (articleProgress != null) {
            int viewHeight = scrollView.getHeight();
            int totalHeight = scrollView.getChildAt(0).getHeight();

            Log.v(TAG, "restoreReadingPosition() viewHeight: " + viewHeight
                    + ", totalHeight: " + totalHeight);

            totalHeight -= viewHeight;

            int yOffset = totalHeight > 0 ? ((int) Math.round(articleProgress * totalHeight)) : 0;

            Log.v(TAG, "restoreReadingPosition() yOffset: " + yOffset);

            scrollView.scrollTo(scrollView.getScrollX(), yOffset);
        }
    }

    private void initTts() {
        if (settings.isTtsVisible()) {
            ttsFragment = (TtsFragment) getSupportFragmentManager()
                    .findFragmentByTag(TAG_TTS_FRAGMENT);

            if (ttsFragment == null) {
                toggleTTS(false);
            }
        }
    }

    public void toggleTTS(boolean autoPlay) {
        if (ttsFragment == null) {
            ttsFragment = TtsFragment.newInstance(autoPlay);

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.tts_container, ttsFragment, TAG_TTS_FRAGMENT)
                    .commit();

            settings.setTtsVisible(true);

            initTtsForArticle();

            setupTtsContainerHeightListener();
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(ttsFragment)
                    .commit();

            ttsFragment = null;

            settings.setTtsVisible(false);

            adjustScrollViewPaddingForTts(0);

            removeTtsContainerHeightListener();
        }

        invalidateOptionsMenu();
    }

    private ViewTreeObserver.OnGlobalLayoutListener ttsContainerLayoutListener;

    private void setupTtsContainerHeightListener() {
        FrameLayout ttsContainer = findViewById(R.id.tts_container);
        if (ttsContainer == null) return;

        removeTtsContainerHeightListener();

        ttsContainerLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private int lastHeight = -1;

            @Override
            public void onGlobalLayout() {
                FrameLayout container = findViewById(R.id.tts_container);
                if (container != null) {
                    int currentHeight = container.getHeight();
                    if (currentHeight != lastHeight && currentHeight > 0) {
                        lastHeight = currentHeight;
                        adjustScrollViewPaddingForTts(currentHeight);
                    }
                }
            }
        };

        ttsContainer.getViewTreeObserver().addOnGlobalLayoutListener(ttsContainerLayoutListener);
    }

    private void removeTtsContainerHeightListener() {
        if (ttsContainerLayoutListener != null) {
            FrameLayout ttsContainer = findViewById(R.id.tts_container);
            if (ttsContainer != null) {
                ttsContainer.getViewTreeObserver().removeOnGlobalLayoutListener(ttsContainerLayoutListener);
                ttsContainer.getViewTreeObserver().removeOnGlobalLayoutListener(ttsContainerLayoutListener);
            }
            ttsContainerLayoutListener = null;
        }
    }

    private void adjustScrollViewPaddingForTts(int ttsHeight) {
        if (scrollView != null) {
            scrollView.setPadding(
                    scrollView.getPaddingLeft(),
                    scrollView.getPaddingTop(),
                    scrollView.getPaddingRight(),
                    ttsHeight
            );
        }
    }

    private void initTtsForArticle() {
        if (ttsFragment != null) {
            ttsFragment.initForArticle(article);
            if (loadingFinished) {
                ttsOnDocumentLoadingFinished();
            }
        }
    }

    private void ttsOnDocumentLoadingFinished() {
        if (ttsFragment != null) {
            ttsFragment.onDocumentLoadFinished();
        }
    }

    private boolean loadArticle(long id) {
        article = getArticle(id);

        if (article == null) return false;

        articleTitle = article.getTitle();
        Log.v(TAG, "loadArticle() articleTitle: " + articleTitle);
        articleDomain = article.getDomain();
        Log.v(TAG, "loadArticle() articleDomain: " + articleDomain);
        articleUrl = article.getUrl();
        Log.v(TAG, "loadArticle() articleUrl: " + articleUrl);
        articleProgress = article.getArticleProgress();
        Log.v(TAG, "loadArticle() articleProgress: " + articleProgress);
        Log.v(TAG, "loadArticle() articleLanguage: " + article.getLanguage());

        return true;
    }

    private Article getArticle(long articleId) {
        return articleDao.queryBuilder()
                .where(ArticleDao.Properties.Id.eq(articleId))
                .unique();
    }

    private Long getAdjacentArticle(boolean previous) {
        QueryBuilder<Article> qb = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.isNotNull());

        // possible problem: will skip articles with the same creation date
        if (previous) qb.where(ArticleDao.Properties.CreationDate.gt(article.getCreationDate()));
        else qb.where(ArticleDao.Properties.CreationDate.lt(article.getCreationDate()));

        if (contextFavorites != null) qb.where(ArticleDao.Properties.Favorite.eq(contextFavorites));
        if (contextArchived != null) qb.where(ArticleDao.Properties.Archive.eq(contextArchived));

        if (previous) qb.orderAsc(ArticleDao.Properties.CreationDate);
        else qb.orderDesc(ArticleDao.Properties.CreationDate);

        List<Article> l = qb.limit(1).list();
        if (!l.isEmpty()) {
            return l.get(0).getId();
        }

        return null;
    }

    private void onPageFinished() {
        Log.d(TAG, "onPageFinished() started");

        if (!isResumed) {
            onPageFinishedCallPostponedUntilResume = true;

            // TODO: check
            // apparently needed for TTS to support going to next article while screen is off
            ttsOnDocumentLoadingFinished();
            return;
        }

        // dirty. Looks like there is no good solution
        webViewContent.postDelayed(new Runnable() {
            int counter;

            @Override
            public void run() {
                // "< 50" is workaround for https://github.com/wallabag/android-app/issues/178
                if (webViewContent.getHeight() < 50) {
                    if (++counter > 1000) {
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
        if (savePosition) articleProgress = getReadingPosition();

        webViewHeightBeforeUpdate = webViewContent.getHeight();
    }

    private void restorePositionAfterUpdate() {
        cancelPositionRestoration();

        webViewContent.postDelayed(positionRestorationRunnable = new Runnable() {
            int counter;

            @Override
            public void run() {
                if (webViewContent.getHeight() == webViewHeightBeforeUpdate) {
                    if (++counter > 1000) {
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
        if (positionRestorationRunnable != null) {
            Log.d(TAG, "cancelPositionRestoration() trying to cancel previous task");
            if (webViewContent != null) webViewContent.removeCallbacks(positionRestorationRunnable);
            positionRestorationRunnable = null;
        }
    }

    private void setFontSize(int size) {
        webViewContent.getSettings().setTextZoom(size);
    }

}