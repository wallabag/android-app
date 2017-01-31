package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Unique;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class Tag {

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

}
