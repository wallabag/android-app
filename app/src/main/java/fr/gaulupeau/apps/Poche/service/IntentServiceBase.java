package fr.gaulupeau.apps.Poche.service;

import android.app.IntentService;
import android.util.Log;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectCredentialsException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

public abstract class IntentServiceBase extends IntentService {

    private static final String TAG = MainService.class.getSimpleName();

    private Settings settings;

    private DaoSession daoSession;
    private WallabagService wallabagService;

    public IntentServiceBase(String name) {
        super(name);
    }

    protected ActionResult processException(Exception e, String scope) {
        ActionResult result = new ActionResult();

        Log.w(TAG, String.format("%s %s", scope, e.getClass().getName()), e);

        if(e instanceof RequestException) {
            if(e instanceof IncorrectCredentialsException) {
                result.setErrorType(ActionResult.ErrorType.IncorrectCredentials);
            } else if(e instanceof IncorrectConfigurationException) {
                result.setErrorType(ActionResult.ErrorType.IncorrectConfiguration);
            } else {
                result.setErrorType(ActionResult.ErrorType.Unknown);
                result.setMessage(e.getMessage());
            }
        } else if(e instanceof IOException) {
            boolean handled = false;

            if(getSettings().isConfigurationOk()) {
                if(e instanceof java.net.UnknownHostException
                        || e instanceof java.net.ConnectException // TODO: maybe filter by message
                        || e instanceof java.net.SocketTimeoutException) {
                    result.setErrorType(ActionResult.ErrorType.Temporary);
                    handled = true;
                } else if(e instanceof javax.net.ssl.SSLException
                        && e.getMessage() != null
                        && e.getMessage().contains("Connection timed out")) {
                    result.setErrorType(ActionResult.ErrorType.Temporary);
                    handled = true;
                }
            }

            if(!handled) {
                result.setErrorType(ActionResult.ErrorType.Unknown);
                result.setMessage(e.toString());
            }
            // IOExceptions in most cases mean temporary error,
            // in some cases may mean that the action was completed anyway.
        } else { // other exceptions meant to be handled outside
            result.setErrorType(ActionResult.ErrorType.Unknown);
        }

        return result;
    }

    protected Settings getSettings() {
        if(settings == null) {
            settings = new Settings(this);
        }

        return settings;
    }

    protected DaoSession getDaoSession() {
        if(daoSession == null) {
            daoSession = DbConnection.getSession();
        }

        return daoSession;
    }

    protected WallabagService getWallabagService() {
        if(wallabagService == null) {
            Settings settings = getSettings();
            wallabagService = WallabagService.fromSettings(settings);
        }

        return wallabagService;
    }

}
