package fr.gaulupeau.apps.Poche.events;

public class LocalArticleReplacedEvent {

    private long localArticleId;
    private int articleId;
    private String givenUrl;

    public LocalArticleReplacedEvent(long localArticleId, int articleId, String givenUrl) {
        this.localArticleId = localArticleId;
        this.articleId = articleId;
        this.givenUrl = givenUrl;
    }

    public long getLocalArticleId() {
        return localArticleId;
    }

    public int getArticleId() {
        return articleId;
    }

    public String getGivenUrl() {
        return givenUrl;
    }

}
