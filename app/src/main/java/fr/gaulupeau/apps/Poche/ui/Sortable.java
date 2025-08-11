package fr.gaulupeau.apps.Poche.ui;

public interface Sortable {

    public enum SortOrder {
        DESC, ASC,
        CreationDateDESC,CreationDateASC,
        EstimatedReadingTimeDESC,EstimatedReadingTimeASC


    }

    void setSortOrder(SortOrder sortOrder);

}
