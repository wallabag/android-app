package fr.gaulupeau.apps.Poche.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import wallabag.apiwrapper.WallabagService;

public class ArticleActionsHelper {

    private static final String TAG = ArticleActionsHelper.class.getSimpleName();

    public void initMenu(Menu menu, Article article) {
        boolean archive = Boolean.TRUE.equals(article.getArchive());

        MenuItem archiveItem = menu.findItem(R.id.menuArticleMarkAsRead);
        archiveItem.setVisible(!archive);
        MenuItem unarchiveItem = menu.findItem(R.id.menuArticleMarkAsUnread);
        unarchiveItem.setVisible(archive);

        boolean favorite = Boolean.TRUE.equals(article.getFavorite());

        MenuItem favoriteItem = menu.findItem(R.id.menuArticleFavorite);
        favoriteItem.setVisible(!favorite);
        MenuItem unfavoriteItem = menu.findItem(R.id.menuArticleUnfavorite);
        unfavoriteItem.setVisible(favorite);
    }

    public boolean handleContextItemSelected(Activity activity, Article article, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuArticleMarkAsRead:
            case R.id.menuArticleMarkAsUnread:
                archive(activity, article, item.getItemId() == R.id.menuArticleMarkAsRead);
                return true;

            case R.id.menuArticleFavorite:
            case R.id.menuArticleUnfavorite:
                favorite(activity, article, item.getItemId() == R.id.menuArticleFavorite);
                return true;

            case R.id.menuShare:
                shareArticle(activity, article.getTitle(), article.getUrl());
                return true;

            case R.id.menuChangeTitle:
                showChangeTitleDialog(activity, article);
                return true;

            case R.id.menuManageTags:
                manageTags(activity, article.getArticleId());
                return true;

            case R.id.menuDelete:
                showDeleteArticleDialog(activity, article, null);
                return true;

            case R.id.menuOpenOriginal:
                openUrl(activity, article.getUrl());
                return true;

            case R.id.menuCopyOriginalURL:
                copyUrlToClipboard(activity, article.getUrl());
                return true;

            case R.id.menuDownloadAsFile:
                showDownloadFileDialog(activity, article);
                return true;
        }

        return false;
    }

    public void archive(Context context, Article article, boolean archive) {
        OperationsHelper.archiveArticle(context, article.getArticleId(), archive);
    }

    public void favorite(Context context, Article article, boolean favorite) {
        OperationsHelper.favoriteArticle(context, article.getArticleId(), favorite);
    }

    public void shareArticle(Context context, String articleTitle, String articleUrl) {
        String shareText = articleUrl;
        if (!TextUtils.isEmpty(articleTitle)) shareText = articleTitle + " " + shareText;

        if (new Settings(context).isAppendWallabagMentionEnabled()) {
            shareText += context.getString(R.string.share_text_extra);
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        if (!TextUtils.isEmpty(articleTitle)) send.putExtra(Intent.EXTRA_SUBJECT, articleTitle);
        send.putExtra(Intent.EXTRA_TEXT, shareText);

        context.startActivity(Intent.createChooser(send,
                context.getString(R.string.share_article_title)));
    }

    public void showChangeTitleDialog(Activity activity, Article article) {
        @SuppressLint("InflateParams") // ok for dialogs
        final View view = activity.getLayoutInflater().inflate(R.layout.dialog_change_title, null);

        view.<TextView>findViewById(R.id.editText_title).setText(article.getTitle());

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setView(view);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) ->
        {
            String title = view.<TextView>findViewById(R.id.editText_title).getText().toString();
            OperationsHelper.changeArticleTitle(activity, article.getArticleId(), title);
        });

        builder.setNegativeButton(android.R.string.cancel, null);

        builder.show();
    }

    public void manageTags(Context context, int articleId) {
        Intent manageTagsIntent = new Intent(context, ManageArticleTagsActivity.class);
        manageTagsIntent.putExtra(ManageArticleTagsActivity.PARAM_ARTICLE_ID, articleId);

        context.startActivity(manageTagsIntent);
    }

    public void showDeleteArticleDialog(Context context, Article article, Runnable okCallback) {
        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle(R.string.d_deleteArticle_title)
                .setMessage(R.string.d_deleteArticle_message);

        b.setPositiveButton(R.string.positive_answer, (dialog, which) -> {
            OperationsHelper.deleteArticle(context, article.getArticleId());

            if (okCallback != null) okCallback.run();
        });
        b.setNegativeButton(R.string.negative_answer, null);

        b.show();
    }

    public void openUrl(Context context, String url) {
        Log.d(TAG, "openUrl() url: " + url);
        if (TextUtils.isEmpty(url)) return;

        Uri uri = Uri.parse(url);
        if (uri.getScheme() == null) {
            Log.i(TAG, "openUrl() scheme is null, appending default scheme");
            uri = Uri.parse("http://" + url);
        }
        Log.d(TAG, "openUrl() uri: " + uri);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            Log.w(TAG, "openUrl() no activity to handle intent");
            Toast.makeText(context, R.string.message_couldNotOpenUrl, Toast.LENGTH_SHORT).show();
        }
    }

    public void copyUrlToClipboard(Context context, String url) {
        ClipboardManager clipboardManager = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData urlClipData = ClipData.newPlainText("article URL", url);
        clipboardManager.setPrimaryClip(urlClipData);
        Toast.makeText(context, R.string.txtUrlCopied, Toast.LENGTH_SHORT).show();
    }

    public void showDownloadFileDialog(Context context, Article article) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_title_downloadFileFormat);

        builder.setItems(R.array.options_downloadFormat_values, (dialog, which) -> {
            String selectedFormat = context.getResources()
                    .getStringArray(R.array.options_downloadFormat_values)[which];

            WallabagService.ResponseFormat format = WallabagService.ResponseFormat
                    .valueOf(selectedFormat);

            OperationsHelper.downloadArticleAsFile(context.getApplicationContext(),
                    article.getArticleId(), format, null);
        });

        builder.show();
    }

}
