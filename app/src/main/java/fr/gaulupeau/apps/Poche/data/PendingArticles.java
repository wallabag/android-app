package fr.gaulupeau.apps.Poche.data;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

/**
 * Helpers for articles that were added locally but have not been pushed to the server yet.
 *
 * <p>Such an article has no server-side ID: it is inserted by
 * {@code OperationsWorker.addArticle()} together with an {@code ADD_LINK} queue item, and is
 * replaced by the real article once the offline queue is synced.
 */
public final class PendingArticles {

    private PendingArticles() {}

    public static boolean isPending(Article article) {
        return article.getArticleId() == null;
    }

    /**
     * The title to show in a list. A pending article has no title yet, so its URL is used.
     */
    public static String displayTitle(Article article) {
        String title = article.getTitle();
        if (title != null && !title.isEmpty()) return title;

        String givenUrl = article.getGivenUrl();
        return givenUrl != null ? givenUrl : "";
    }
}
