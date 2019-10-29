package fr.gaulupeau.apps.Poche.network;

import android.text.TextUtils;

import wallabag.apiwrapper.*;
import wallabag.apiwrapper.WallabagService;
import wallabag.apiwrapper.exceptions.NotFoundException;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import wallabag.apiwrapper.models.Article;
import wallabag.apiwrapper.models.TokenResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;

public class WallabagServiceWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(WallabagServiceWrapper.class);

    private static volatile WallabagServiceWrapper instance;

    private WallabagService wallabagService;

    public static WallabagServiceWrapper getInstance() throws IncorrectConfigurationException {
        WallabagServiceWrapper wrapper = instance;
        if(wrapper == null) {
            wrapper = new WallabagServiceWrapper();
        }
        return instance = wrapper;
    }

    public static void resetInstance() {
        instance = null;
    }

    private WallabagServiceWrapper() throws IncorrectConfigurationException {
        final Settings settings = App.getInstance().getSettings();

        String url = settings.getUrl();
        if(TextUtils.isEmpty(url)) throw new IncorrectConfigurationException("URL is empty");

        wallabagService = WallabagService.instance(url, new ParameterHandler() {
            @Override
            public String getUsername() {
                return settings.getUsername();
            }

            @Override
            public String getPassword() {
                return settings.getPassword();
            }

            @Override
            public String getClientID() {
                return settings.getApiClientID();
            }

            @Override
            public String getClientSecret() {
                return settings.getApiClientSecret();
            }

            @Override
            public String getRefreshToken() {
                return settings.getApiRefreshToken();
            }

            @Override
            public String getAccessToken() {
                return settings.getApiAccessToken();
            }

            @Override
            public boolean tokensUpdated(TokenResponse token) {
                if(token.refreshToken != null) settings.setApiRefreshToken(token.refreshToken);
                settings.setApiAccessToken(token.accessToken);

                return !TextUtils.isEmpty(token.accessToken);
            }
        }, WallabagConnection.createClient(false));
    }

    public static Article executeModifyArticleCall(ModifyArticleBuilder builder)
            throws UnsuccessfulResponseException, IOException {
        try {
            return builder.execute();
        } catch(NotFoundException e) {
            LOG.info("Ignoring not found exception", e);
        }

        return null;
    }

    public Article addArticle(String url) throws UnsuccessfulResponseException, IOException {
        return wallabagService.addArticle(url);
    }

    public boolean deleteArticle(int articleID)
            throws UnsuccessfulResponseException, IOException {
        boolean found = false;

        try {
            if (CompatibilityHelper.isDeleteArticleWithIdSupported(wallabagService)) {
                wallabagService.deleteArticleWithId(articleID);
                found = true;
            } else {
                LOG.debug("deleteArticle() using older API method");
                found = wallabagService.deleteArticle(articleID) != null;
            }
        } catch(NotFoundException e) {
            LOG.info("deleteArticle() Ignoring not found exception for article ID: " + articleID, e);
        }

        return found;
    }

    public WallabagService getWallabagService() {
        return wallabagService;
    }

}
