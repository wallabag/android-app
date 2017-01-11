package fr.gaulupeau.apps.Poche.events;

public class ConnectivityChangedEvent {

    private boolean noConnectivity;

    public ConnectivityChangedEvent() {}

    public ConnectivityChangedEvent(boolean noConnectivity) {
        this.noConnectivity = noConnectivity;
    }

    public boolean isNoConnectivity() {
        return noConnectivity;
    }

}
