package fr.gaulupeau.apps.Poche.data.dao.entities;

public class SpecificItem {

    QueueItem queueItem;

    SpecificItem(QueueItem queueItem) {
        this.queueItem = queueItem;
    }

    public QueueItem genericItem() {
        return queueItem;
    }

}
