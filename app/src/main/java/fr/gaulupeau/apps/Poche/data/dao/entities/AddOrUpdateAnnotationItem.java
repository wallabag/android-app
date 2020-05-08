package fr.gaulupeau.apps.Poche.data.dao.entities;

public class AddOrUpdateAnnotationItem extends ArticleIdItem<AddOrUpdateAnnotationItem> {

    AddOrUpdateAnnotationItem(QueueItem queueItem) {
        super(queueItem);
    }

    @Override
    AddOrUpdateAnnotationItem self() {
        return this;
    }

    public long getLocalAnnotationId() {
        return Long.parseLong(queueItem.getExtra());
    }

    public AddOrUpdateAnnotationItem setLocalAnnotationId(long annotationId) {
        queueItem.setExtra(String.valueOf(annotationId));
        return this;
    }

}
