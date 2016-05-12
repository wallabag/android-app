package fr.gaulupeau.apps.Poche.network.tasks;

import android.content.Context;
import android.widget.Toast;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.ui.DialogHelperActivity;
import fr.gaulupeau.apps.Poche.ui.IconUnreadWidget;

public class ToggleArchiveTask extends GenericArticleTask {

    public ToggleArchiveTask(Context context, int articleId, ArticleDao articleDao, Article article) {
        super(context, articleId, articleDao, article);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        article.setArchive(!article.getArchive());
        articleDao.update(article);
    }

    @Override
    protected Boolean doInBackgroundSimple(Void... params) throws IOException {
        if(isOffline || noCredentials) return false;

        if(service.toggleArchive(articleId)) return true;

        if(context != null) errorMessage = context.getString(R.string.toggleArchive_errorMessage);
        return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);

        article.setSync(success); // ?
        articleDao.update(article);

        if(success || isOffline || noCredentials) {
            if(context != null) {
                Toast.makeText(context, article.getArchive()
                                ? R.string.moved_to_archive_message
                                : R.string.marked_as_unread_message,
                        Toast.LENGTH_SHORT).show();

                if(isOffline && !noCredentials) {
                    Toast.makeText(context, R.string.toggleArchive_noInternetConnection,
                            Toast.LENGTH_SHORT).show();
                }
                IconUnreadWidget.triggerWidgetUpdate(context);
            }
        } else {
            if(context != null) {
                DialogHelperActivity.showConnectionFailureDialog(context, errorMessage);
            }
        }
    }

}
