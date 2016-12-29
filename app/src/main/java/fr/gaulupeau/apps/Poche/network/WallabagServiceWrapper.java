package fr.gaulupeau.apps.Poche.network;

import com.di72nn.stuff.wallabag.apiwrapper.*;
import com.di72nn.stuff.wallabag.apiwrapper.WallabagService;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.NotFoundException;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import com.di72nn.stuff.wallabag.apiwrapper.models.Article;
import com.di72nn.stuff.wallabag.apiwrapper.models.TokenResponse;

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
        if(url == null || url.isEmpty()) throw new IncorrectConfigurationException("URL is empty");

        wallabagService = new WallabagService(url, new ParameterHandler() {
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
            public void tokensUpdated(TokenResponse token) {
                if(token.refreshToken != null) settings.setApiRefreshToken(token.refreshToken);
                settings.setApiAccessToken(token.accessToken);
            }
        });
    }

    public Article addArticle(String url) throws UnsuccessfulResponseException, IOException {
        return wallabagService.addArticle(url);
    }

    public Article favoriteArticle(int articleID, boolean favorite)
            throws UnsuccessfulResponseException, IOException {
        try {
            return wallabagService.modifyArticleBuilder(articleID).starred(favorite).execute();
        } catch(NotFoundException e) {
            LOG.info("Ignoring not found exception for article ID: " + articleID, e);
        }

        return null;
    }

    public Article archiveArticle(int articleID, boolean archive)
            throws UnsuccessfulResponseException, IOException {
        try {
            return wallabagService.modifyArticleBuilder(articleID).archive(archive).execute();
        } catch(NotFoundException e) {
            LOG.info("Ignoring not found exception for article ID: " + articleID, e);
        }

        return null;
    }

    public Article deleteArticle(int articleID)
            throws UnsuccessfulResponseException, IOException {
        try {
            return wallabagService.deleteArticle(articleID);
        } catch(NotFoundException e) {
            LOG.info("Ignoring not found exception for article ID: " + articleID, e);
        }

        return null;
    }

    public WallabagService getWallabagService() {
        return wallabagService;
    }

}
