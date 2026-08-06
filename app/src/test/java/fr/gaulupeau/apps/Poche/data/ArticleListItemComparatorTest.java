package fr.gaulupeau.apps.Poche.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

public class ArticleListItemComparatorTest {

    private static Article article(Long localId, Integer articleId) {
        Article article = new Article();
        article.setId(localId);
        article.setArticleId(articleId);
        return article;
    }

    @Test
    public void pendingArticlesWithDifferentLocalIdsAreDifferentItems() {
        assertFalse(ArticleListItemComparator.isSameItem(article(1L, null), article(2L, null)));
    }

    @Test
    public void sameLocalIdIsTheSameItem() {
        assertTrue(ArticleListItemComparator.isSameItem(article(1L, null), article(1L, null)));
    }

    @Test
    public void pendingAndSyncedArticleAreNeverTheSameItem() {
        // on sync the placeholder row is deleted and a new row inserted, so the PK differs
        assertFalse(ArticleListItemComparator.isSameItem(article(1L, null), article(2L, 42)));
    }

    @Test
    public void contentsComparisonToleratesNullFlagsAndText() {
        assertTrue(ArticleListItemComparator.hasSameContents(article(1L, null), article(1L, null)));
    }

    @Test
    public void nullFlagIsNotEqualToFalseFlag() {
        Article legacy = article(1L, null);          // pre-change placeholder: archive is null
        Article current = article(1L, null);
        current.setArchive(false);

        assertFalse(ArticleListItemComparator.hasSameContents(legacy, current));
    }

    @Test
    public void placeholderAndRealArticleHaveDifferentContents() {
        Article placeholder = article(1L, null);
        placeholder.setArchive(false);
        placeholder.setFavorite(false);

        Article real = article(2L, 42);
        real.setArchive(false);
        real.setFavorite(false);
        real.setTitle("Real title");
        real.setDomain("example.com");

        assertFalse(ArticleListItemComparator.hasSameContents(placeholder, real));
    }

    @Test
    public void articlesWithoutLocalIdsAreNeverTheSameItem() {
        // rows read from the DAO always have a PK, so this cannot happen in practice;
        // the guard is deliberate, because treating null == null as "same item" would
        // collapse unrelated unsaved articles onto one row
        assertFalse(ArticleListItemComparator.isSameItem(article(null, null), article(null, null)));
    }
}
