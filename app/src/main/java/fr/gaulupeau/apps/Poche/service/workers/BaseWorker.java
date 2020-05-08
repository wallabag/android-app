package fr.gaulupeau.apps.Poche.service.workers;

import android.content.Context;

import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;

public class BaseWorker {

    private final Context context;

    private Settings settings;

    private DaoSession daoSession;

    public BaseWorker(Context context) {
        this.context = context;
    }

    protected Context getContext() {
        return context;
    }

    protected Settings getSettings() {
        if (settings == null) {
            settings = new Settings(context);
        }

        return settings;
    }

    protected DaoSession getDaoSession() {
        if (daoSession == null) {
            daoSession = DbConnection.getSession();
        }

        return daoSession;
    }

}
