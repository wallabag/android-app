package fr.gaulupeau.apps.Poche.data.dao.entities;

public class DeleteAnnotationItem extends ArticleIdItem<DeleteAnnotationItem> {

    DeleteAnnotationItem(QueueItem queueItem) {
        super(queueItem);
    }

    @Override
    DeleteAnnotationItem self() {
        return this;
    }

    public int getRemoteAnnotationId() {
        return Integer.parseInt(queueItem.getExtra());
    }

    public DeleteAnnotationItem setRemoteAnnotationId(int annotationId) {
        queueItem.setExtra(String.valueOf(annotationId));
        return this;
    }

}
