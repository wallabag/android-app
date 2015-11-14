package fr.gaulupeau.apps.Poche.ui;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.URL;
import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.network.tasks.AddLinkTask;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.network.tasks.DeleteArticleTask;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.tasks.ToggleArchiveTask;
import fr.gaulupeau.apps.Poche.network.tasks.ToggleFavoriteTask;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.utils.Utilities;

public class ReadArticleActivity extends BaseActionBarActivity {

	public static final String EXTRA_ID = "ReadArticleActivity.id";
	public static final String EXTRA_LIST_ARCHIVED = "ReadArticleActivity.archived";
	public static final String EXTRA_LIST_FAVORITES = "ReadArticleActivity.favorites";

    private ScrollView scrollView;
	private WebView webViewContent;
    private TextView loadingPlaceholder;
    private LinearLayout bottomTools;
    private View hrBar;

    private Article mArticle;
    private ArticleDao mArticleDao;

    private Boolean contextFavorites;
    private Boolean contextArchived;

    private String titleText;
    private String originalUrlText;
    private Double positionToRestore;

    private Long previousArticleID;
    private Long nextArticleID;

    private boolean loadingFinished;

    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.article);

		Intent intent = getIntent();
        long articleId = intent.getLongExtra(EXTRA_ID, -1);
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

        titleText = mArticle.getTitle();
        originalUrlText = mArticle.getUrl();
        String originalUrlHost = "";

        try {
            URL originalUrl = new URL(originalUrlText);
            originalUrlHost = originalUrl.getHost();
        } catch (Exception e) {
            //
        }

		String htmlContent = mArticle.getContent();
        positionToRestore = mArticle.getArticleProgress();
        Integer estimatedTimeNumber = Utilities.estimatedTime(mArticle.getContent()).intValue();

        setTitle(titleText);

        boolean highContrast = App.getInstance().getSettings()
                .getBoolean(Settings.HIGH_CONTRAST, false);

		String htmlHeader = "<html>\n" +
				"\t<head>\n" +
				"\t\t<meta name=\"viewport\" content=\"initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />\n" +
				"\t\t<meta charset=\"utf-8\">\n" +
				"\t\t<link rel=\"stylesheet\" href=\"main.css\" media=\"all\" id=\"main-theme\">\n" +
				"\t\t<link rel=\"stylesheet\" href=\"ratatouille.css\" media=\"all\" id=\"extra-theme\">\n" +
				"\t</head>\n" +
				"\t\t<div id=\"main\">\n" +
				"\t\t\t<body" + (highContrast ? " class=\"hicontrast\"" : "") + ">\n" +
				"\t\t\t\t<div id=\"content\" class=\"w600p center\">\n" +
				"\t\t\t\t\t<div id=\"article\">\n" +
				"\t\t\t\t\t\t<header class=\"mbm\">\n" +
				"\t\t\t\t\t\t\t<h1>" + titleText + "</h1>\n" +
                "\t\t\t\t\t\t\t<span class=\"urlhost\"><a href=\"" + originalUrlText + "\">" + originalUrlHost + "</a></span><br>\n" +
                "\t\t\t\t\t\t\t<span class=\"estimatedtime\">" + getString(R.string.estimated_time, estimatedTimeNumber) + "</span>\n" +
				"\t\t\t\t\t\t</header>\n" +
				"\t\t\t\t\t\t<article>";

		String htmlFooter = "</article>\n" +
				"\t\t\t\t\t</div>\n" +
				"\t\t\t\t</div>\n" +
				"\t\t\t</body>\n" +
				"\t\t</div>\n" +
				"</html>";

        scrollView = (ScrollView) findViewById(R.id.scroll);
		webViewContent = (WebView) findViewById(R.id.webViewContent);
        webViewContent.getSettings().setJavaScriptEnabled(true); // TODO: make optional?
        webViewContent.setWebChromeClient(new WebChromeClient() {}); // TODO: check
        webViewContent.loadDataWithBaseURL("file:///android_asset/",
                htmlHeader + htmlContent + htmlFooter, "text/html", "utf-8", null);

        webViewContent.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // dirty. Looks like there is no good solution
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (webViewContent.getHeight() == 0) {
                            webViewContent.postDelayed(this, 10);
                        } else {
                            loadingFinished();
                        }
                    }
                }, 10);

                super.onPageFinished(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                return openUrl(url);
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
                openArticle(previousArticleID);
            }
        });
        btnGoNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openArticle(nextArticleID);
            }
        });
	}

    private void loadingFinished() {
        loadingFinished = true;

        loadingPlaceholder.setVisibility(View.GONE);
        bottomTools.setVisibility(View.VISIBLE);
        hrBar.setVisibility(View.VISIBLE);

        restoreReadingPosition();
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
                                new AddLinkTask(url, ReadArticleActivity.this).execute();
                                break;
                        }
                    }
                });
        builder.show();

        return true;
    }

    private void markAsReadAndClose() {
        new ToggleArchiveTask(this, mArticle.getArticleId(), mArticleDao, mArticle).execute();

        finish();
    }

    private boolean toggleFavorite() {
        new ToggleFavoriteTask(this, mArticle.getArticleId(), mArticleDao, mArticle).execute();

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
                new DeleteArticleTask(ReadArticleActivity.this,
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

    private void openArticle(Long id) {
        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(ReadArticleActivity.EXTRA_ID, id);
        if(contextFavorites != null) intent.putExtra(EXTRA_LIST_FAVORITES, contextFavorites);
        if(contextArchived != null) intent.putExtra(EXTRA_LIST_ARCHIVED, contextArchived);
        startActivity(intent);
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStop() {
        if(loadingFinished && mArticle != null) {
            mArticle.setArticleProgress(getReadingPosition());
            mArticleDao.update(mArticle);
        }

        super.onStop();
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
