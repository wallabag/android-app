package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class ArticleContent {

    @Id
    private Long id;

    private String content;

    @Generated(hash = 1175279025)
    public ArticleContent(Long id, String content) {
        this.id = id;
        this.content = content;
    }

    @Generated(hash = 949142728)
    public ArticleContent() {}

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
