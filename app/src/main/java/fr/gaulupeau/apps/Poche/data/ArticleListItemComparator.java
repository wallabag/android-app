package fr.gaulupeau.apps.Poche.data;

import java.util.Objects;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

/**
 * Comparisons used by the article list's {@code DiffUtil} callback.
 *
 * <p>{@code Objects.equals} is used instead of {@code TextUtils.equals} so that the comparisons
 * tolerate the null flags of a pending article, and so that they can be unit-tested on the JVM.
 */
public final class ArticleListItemComparator {

    private ArticleListItemComparator() {}

    /**
     * Items are identified by their server-side article ID where both have one: a {@code FULL}
     * update deletes and re-inserts every article, so the local primary key is not stable across
     * a sync. It is only used as the fallback for pending articles, which have no server-side ID.
     */
    public static boolean isSameItem(Article a, Article b) {
        Integer articleIdA = a.getArticleId();
        if (articleIdA != null && b.getArticleId() != null) {
            return articleIdA.equals(b.getArticleId());
        }

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
