package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.*;

/**
 * Entity mapped to table "QUEUE_ITEM".
 */
@Entity
public class QueueItem {

    @Id
    private Long id;

    private Long queueNumber; // not used
    private int action;

    private Integer articleId;
    private String extra;

    @Generated(hash = 1112811270)
    public QueueItem() {}

    public QueueItem(Long id) {
        this.id = id;
    }

    @Generated(hash = 1601114234)
    public QueueItem(Long id, Long queueNumber, int action, Integer articleId, String extra) {
        this.id = id;
        this.queueNumber = queueNumber;
        this.action = action;
        this.articleId = articleId;
        this.extra = extra;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQueueNumber() {
        return queueNumber;
    }

    public void setQueueNumber(Long queueNumber) {
        this.queueNumber = queueNumber;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public Integer getArticleId() {
        return articleId;
    }

    public void setArticleId(Integer articleId) {
        this.articleId = articleId;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

}
