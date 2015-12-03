package fr.gaulupeau.apps.Poche.data;

import static fr.gaulupeau.apps.Poche.data.Settings.URL;
import static fr.gaulupeau.apps.Poche.data.Settings.USER_ID;
import static fr.gaulupeau.apps.Poche.data.Settings.TOKEN;

/**
 * Created by kevinmeyer on 19/01/15.
 */
public class WallabagSettings {
    public String wallabagURL;
    public String userID;
    public String userToken;

    private Settings settings;

    public WallabagSettings(Settings settings) {
        this.settings = settings;
    }

    public static WallabagSettings settingsFromDisk(Settings settings) {
        WallabagSettings wallabagSettings = new WallabagSettings(settings);
        wallabagSettings.load();
        return wallabagSettings;
    }

    public boolean isValid() {
        //TODO Should also check for valid URL and valid userID
        return !(wallabagURL.equals("http://")) && !(userID.equals("")) && !(userToken.equals(""));
    }

    public void load() {
        wallabagURL = settings.getString(URL, "http://");
        userID = settings.getString(USER_ID, "");
        userToken = settings.getString(TOKEN, "");
    }

    public void save() {
        settings.setString(URL, wallabagURL);
        settings.setString(USER_ID, userID);
        settings.setString(TOKEN, userToken);
    }
}
