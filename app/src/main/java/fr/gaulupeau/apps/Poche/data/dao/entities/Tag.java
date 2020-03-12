package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Unique;
import org.greenrobot.greendao.annotation.Generated;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Entity
public class Tag {

    private static final Comparator<Tag> LABEL_COMPARATOR = new Comparator<Tag>() {
        @Override
        public int compare(Tag o1, Tag o2) {
            String s1 = o1.getLabel(), s2 = o2.getLabel();
            return s1 == null
                    ? (s2 == null ? 0 : -1)
                    : (s2 == null ? 1 : s1.compareTo(s2));
        }
    };

    @Id
    private Long id;

    @Unique
    private Integer tagId;
    private String label;

    @Generated(hash = 1605720318)
    public Tag() {}

    @Generated(hash = 624255546)
    public Tag(Long id, Integer tagId, String label) {
        this.id = id;
        this.tagId = tagId;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getTagId() {
        return tagId;
    }

    public void setTagId(Integer tagId) {
        this.tagId = tagId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return String.format("Tag {id: %s, tagId: %s, label: \"%s\"}", id, tagId, label);
    }

    public static void sortTagListByLabel(List<Tag> list) {
        Collections.sort(list, LABEL_COMPARATOR);
    }

}
