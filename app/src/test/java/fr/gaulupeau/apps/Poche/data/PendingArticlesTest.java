package fr.gaulupeau.apps.Poche.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

public class PendingArticlesTest {

    private static Article article(String givenUrl, String title) {
        Article article = new Article();
        article.setId(1L);
        article.setGivenUrl(givenUrl);
        article.setTitle(title);
        return article;
    }

    @Test
    public void articleWithoutServerIdIsPending() {
        assertTrue(PendingArticles.isPending(article("https://example.com/a", null)));
    }

    @Test
    public void articleWithServerIdIsNotPending() {
        Article article = article("https://example.com/a", "Real title");
        article.setArticleId(42);

        assertFalse(PendingArticles.isPending(article));
    }

    @Test
    public void displayTitleUsesTitleWhenPresent() {
        assertEquals("Real title",
                PendingArticles.displayTitle(article("https://example.com/a", "Real title")));
    }

    @Test
    public void displayTitleFallsBackToGivenUrlWhenTitleIsNull() {
        assertEquals("https://example.com/a",
                PendingArticles.displayTitle(article("https://example.com/a", null)));
    }

    @Test
    public void displayTitleFallsBackToGivenUrlWhenTitleIsEmpty() {
        assertEquals("https://example.com/a",
                PendingArticles.displayTitle(article("https://example.com/a", "")));
    }

    @Test
    public void displayTitleReturnsEmptyStringWhenNothingIsAvailable() {
        assertEquals("", PendingArticles.displayTitle(article(null, null)));
    }
}
