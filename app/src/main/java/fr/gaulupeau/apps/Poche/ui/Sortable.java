package fr.gaulupeau.apps.Poche.ui;

public interface Sortable {

    public enum SortOrder {
        DESC, ASC
    }

    void setSortOrder(SortOrder sortOrder);

}
