package fr.gaulupeau.apps.Poche;

public class Article {
    public String url;
    public String id;
    public String title;
    public String content;
    public String archive;
    
    public Article(String url, String id, String title, String content, String archive) {
                super();
                this.url = url;
                this.id = id;
                this.title = title;
                this.content = content;
                this.archive = archive;
        }
}
