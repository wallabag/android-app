package fr.gaulupeau.apps.Poche.data.dao.entities;

import android.text.TextUtils;

import org.greenrobot.greendao.annotation.*;
import org.greenrobot.greendao.converter.PropertyConverter;

import java.util.EnumSet;
import java.util.Iterator;

/**
 * Entity mapped to table "QUEUE_ITEM".
 */
@Entity
public class QueueItem {

    public enum Action {
        ADD_LINK(1), ARTICLE_DELETE(2), ARTICLE_CHANGE(3), ARTICLE_TAGS_DELETE(4),
        ANNOTATION_ADD(5), ANNOTATION_UPDATE(6), ANNOTATION_DELETE(7);

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

    }

    // if names changed, QUEUE_ITEM table must be cleared
    public enum ArticleChangeType {
        ARCHIVE, FAVORITE, TITLE, TAGS;

        private static String STRING_DELIMITER = ","; // non-special char in regex

        public static String enumSetToString(EnumSet<ArticleChangeType> enumSet) {
            if(enumSet.isEmpty()) return "";
            if(enumSet.size() == 1) return enumSet.iterator().next().name();

            Iterator<ArticleChangeType> it = enumSet.iterator();
            StringBuilder sb = new StringBuilder(it.next().name());
            while(it.hasNext()) {
                sb.append(STRING_DELIMITER).append(it.next().name());
            }

            return sb.toString();
        }

        public static EnumSet<ArticleChangeType> stringToEnumSet(String stringValue) {
            if(TextUtils.isEmpty(stringValue)) return EnumSet.noneOf(ArticleChangeType.class);
            if(!stringValue.contains(STRING_DELIMITER)) return EnumSet.of(valueOf(stringValue));

            EnumSet<ArticleChangeType> result = EnumSet.noneOf(ArticleChangeType.class);
            for(String s: stringValue.split(STRING_DELIMITER)) {
                result.add(valueOf(s));
            }

            return result;
        }

    }

    public static final String DELETED_TAGS_DELIMITER = ",";

    @Id
    private Long id;

    private Long queueNumber;

    @Convert(converter = QueueItem.ActionConverter.class, columnType = Integer.class)
    private QueueItem.Action action;

    private Integer articleId;
    private String extra;

    @Generated(hash = 1112811270)
    public QueueItem() {}

    public QueueItem(Long id) {
        this.id = id;
    }

    @Generated(hash = 1681630945)
    public QueueItem(Long id, Long queueNumber, QueueItem.Action action, Integer articleId,
                     String extra) {
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

    public Action getAction() {
        return action;
    }

    public void setAction(QueueItem.Action action) {
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

    public static class ActionConverter implements PropertyConverter<Action, Integer> {

        @Override
        public Action convertToEntityProperty(Integer databaseValue) {
            if(databaseValue == null) return null;

            for(Action role: Action.values()) {
                if(role.getId() == databaseValue) {
                    return role;
                }
            }

            return null;
        }

        @Override
        public Integer convertToDatabaseValue(Action entityProperty) {
            return entityProperty == null ? null : entityProperty.getId();
        }

    }

    @Override
    public String toString() {
        return "QueueItem{" +
                "id=" + id +
                ", queueNumber=" + queueNumber +
                ", action=" + action +
                ", articleId=" + articleId +
                ", extra='" + extra + '\'' +
                '}';
    }

}
