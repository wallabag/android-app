package fr.gaulupeau.apps.Poche.data.dao.entities;

import android.text.TextUtils;

import org.greenrobot.greendao.annotation.*;
import org.greenrobot.greendao.converter.PropertyConverter;

import java.util.EnumSet;
import java.util.Iterator;

/**
 * Entity mapped to table "QUEUE_ITEM".
 * <p>Avoid using fields of this class directly - prefer action-specific classes
 * available via {@link #asSpecificItem()}.
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

    @Id
    private Long id;

    private Long queueNumber;

    @Convert(converter = QueueItem.ActionConverter.class, columnType = Integer.class)
    private QueueItem.Action action;

    private Integer articleId;
    private Long localArticleId;
    private String extra;
    private String extra2;

    @Generated(hash = 1112811270)
    public QueueItem() {}

    public QueueItem(QueueItem.Action action) {
        this.action = action;
    }

    @Generated(hash = 20035817)
    public QueueItem(Long id, Long queueNumber, QueueItem.Action action, Integer articleId,
                     Long localArticleId, String extra, String extra2) {
        this.id = id;
        this.queueNumber = queueNumber;
        this.action = action;
        this.articleId = articleId;
        this.localArticleId = localArticleId;
        this.extra = extra;
        this.extra2 = extra2;
    }

    public <T extends SpecificItem> T asSpecificItem() {
        @SuppressWarnings("unchecked") // cast will fail for incorrect type, but it is desired
        T item = (T) getView();
        return item;
    }

    private SpecificItem getView() {
        if (action == null) throw new RuntimeException("action is not set!");

        switch (action) {
            case ADD_LINK:
                return new AddLinkItem(this);
            case ARTICLE_DELETE:
                return new ArticleDeleteItem(this);
            case ARTICLE_CHANGE:
                return new ArticleChangeItem(this);
            case ARTICLE_TAGS_DELETE:
                return new ArticleTagsDeleteItem(this);
            case ANNOTATION_ADD:
            case ANNOTATION_UPDATE:
                return new AddOrUpdateAnnotationItem(this);
            case ANNOTATION_DELETE:
                return new DeleteAnnotationItem(this);
            default:
                throw new RuntimeException("Not implemented for action=" + action);
        }
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

    public Long getLocalArticleId() {
        return localArticleId;
    }

    public void setLocalArticleId(Long localArticleId) {
        this.localArticleId = localArticleId;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getExtra2() {
        return extra2;
    }

    public void setExtra2(String extra2) {
        this.extra2 = extra2;
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
                ", localArticleId=" + localArticleId +
                ", extra='" + extra + '\'' +
                ", extra2='" + extra2 + '\'' +
                '}';
    }

}
