package fr.gaulupeau.apps.Poche.data;

import java.util.Objects;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

/**
 * Comparisons used by the article list's {@code DiffUtil} callback.
 *
 * <p>Items are identified by their local primary key rather than by the server-side article ID,
 * because a pending article has no server-side ID yet. {@code Objects.equals} is used instead of
 * {@code TextUtils.equals} so that this class stays free of Android dependencies and can be
 * unit-tested on the JVM.
 */
public final class ArticleListItemComparator {

    private ArticleListItemComparator() {}

    public static boolean isSameItem(Article a, Article b) {
        Long idA = a.getId();
        return idA != null && idA.equals(b.getId());
    }

    public static boolean hasSameContents(Article a, Article b) {
        return Objects.equals(a.getArchive(), b.getArchive())
                && Objects.equals(a.getFavorite(), b.getFavorite())
                && Objects.equals(a.getTitle(), b.getTitle())
                && Objects.equals(a.getDomain(), b.getDomain());
    }
}
