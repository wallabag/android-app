package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.widget.Toast;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.ui.ConnectionFailAlert;

public class ToggleArchiveTask extends GenericArticleTask {

    public ToggleArchiveTask(Context context, int articleId, ArticleDao articleDao, Article article) {
        super(context, articleId, articleDao, article);
    }

    @Override
    protected Boolean doInBackgroundSimple(Void... params) throws IOException {
        if(service.toggleArchive(articleId)) return true;

        errorMessage = "Couldn't sync to server";
        return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);

        article.setArchive(!article.getArchive());
        article.setSync(success); // ?
        articleDao.update(article);

        if (success) {
            if(context != null) {
                Toast.makeText(context, article.getArchive()
                                ? R.string.moved_to_archive_message
                                : R.string.marked_as_unread_message,
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            if(context != null) ConnectionFailAlert.getDialog(context, errorMessage).show();
        }
    }

}
