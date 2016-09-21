package fr.gaulupeau.apps.Poche.ui.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagServiceEndpoint;
import fr.gaulupeau.apps.Poche.network.tasks.TestFeedsTask;

// TODO: split classes?
public class ConnectionWizardActivity extends AppCompatActivity {

    public static final String EXTRA_SKIP_WELCOME = "skip_welcome";
    public static final String EXTRA_SHOW_SUMMARY = "show_summary";

    private static final String TAG = "ConnectionWizard";

    private static final String DATA_PROVIDER = "provider";
    private static final String DATA_URL = "url";
    private static final String DATA_USERNAME = "username";
    private static final String DATA_PASSWORD = "password";
    private static final String DATA_HTTP_AUTH_USERNAME = "http_auth_username";
    private static final String DATA_HTTP_AUTH_PASSWORD = "http_auth_password";
    private static final String DATA_CUSTOM_SSL_SETTINGS = "custom_ssl_settings";
    private static final String DATA_ACCEPT_ALL_CERTIFICATES = "accept_all_certificates";
    private static final String DATA_SERVER_VERSION = "server_version";
    private static final String DATA_FEEDS_USER_ID = "feeds_user_id";
    private static final String DATA_FEEDS_TOKEN = "feeds_token";

    private static final int PROVIDER_NO = -1;
    private static final int PROVIDER_V2_WALLABAG_ORG = 0;
    private static final int PROVIDER_FRAMABAG = 1;

    private static final String PAGE_NONE = "";
    private static final String PAGE_WELCOME = "welcome";
    private static final String PAGE_PROVIDER_SELECTION = "provider_selection";
    private static final String PAGE_CONFIG_GENERIC = "config_generic";
    private static final String PAGE_CONFIG_FRAMABAG = "config_framabag";
    private static final String PAGE_CONFIG_V2_WALLABAG_ORG = "config_v2_wallabag_org";
    private static final String PAGE_SUMMARY = "summary";

    public static void runWizard(Context context, boolean skipWelcome) {
        runWizard(context, skipWelcome, false);
    }

    public static void runWizard(Context context, boolean skipWelcome, boolean showSummary) {
        Intent intent = new Intent(context, ConnectionWizardActivity.class);
        if(skipWelcome) intent.putExtra(EXTRA_SKIP_WELCOME, true);
        if(showSummary) intent.putExtra(EXTRA_SHOW_SUMMARY, true);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(savedInstanceState == null) {
            Intent intent = getIntent();
            Bundle bundle = new Bundle();

            if(intent.getBooleanExtra(EXTRA_SHOW_SUMMARY, false)) {
                bundle.putBoolean(EXTRA_SHOW_SUMMARY, true);
            }

            String currentPage = null;

            String dataString = intent.getDataString();
            if(dataString != null) {
                Log.d(TAG, "onCreate() got data string: " + dataString);
                try {
                    ConnectionData connectionData = parseLoginData(dataString);

                    bundle.putString(DATA_URL, connectionData.mUrl);
                    bundle.putString(DATA_USERNAME, connectionData.mUsername);

                    currentPage = PAGE_PROVIDER_SELECTION;
                } catch(IllegalArgumentException e) {
                    Log.w(TAG, "onCreate() login data parsing exception", e);
                    Toast.makeText(this, R.string.connectionWizard_misc_incorrectConnectionURI,
                            Toast.LENGTH_SHORT).show();
                }
            }

            if(currentPage == null && intent.getBooleanExtra(EXTRA_SKIP_WELCOME, false)) {
                currentPage = PAGE_WELCOME;
            }

            next(currentPage, bundle, true);
        } else {
            // TODO: check
            Log.w(TAG, "onCreate() savedInstanceState != null");
        }
    }

    private ConnectionData parseLoginData(String connectionUri) {
        // wallabag://user@server.tld
        String prefix = "wallabag://";

        // URI missing or to short
        if (connectionUri == null || connectionUri.length() <= prefix.length() || !connectionUri.startsWith(prefix)) {
            throw new IllegalArgumentException("Incorrect URI scheme detected");
        }

        String data = connectionUri.substring(prefix.length());

        String[] values = data.split("@");

        if (values.length < 1 || values.length > 2) {
            // error illegal number of URI elements detected
            throw new IllegalArgumentException("Illegal number of login URL elements detected: " + values.length);
        }

        return new ConnectionData(values[0], values[1]);
    }

    public void prev(WizardPageFragment fragment, Bundle bundle) {
        String currentPage = fragment != null ? fragment.getPageName() : PAGE_NONE;

        if(PAGE_WELCOME.equals(currentPage) || PAGE_PROVIDER_SELECTION.equals(currentPage)) {
            finish();
        } else {
            // TODO: save data from current fragment? Or remove some data instead?
            getSupportFragmentManager().popBackStack();
        }
    }

    public void next(WizardPageFragment fragment, Bundle bundle) {
        next(fragment != null ? fragment.getPageName() : PAGE_NONE, bundle);
    }

    public void next(String currentPage, Bundle bundle) {
        next(currentPage, bundle, false);
    }

    private void next(String currentPage, Bundle bundle, boolean noBackStack) {
        if(currentPage == null) currentPage = PAGE_NONE;
        if(bundle == null) bundle = new Bundle();

        Fragment goToFragment = null;

        switch(currentPage) {
            case PAGE_NONE:
                goToFragment = new WelcomeFragment();
                break;

            case PAGE_WELCOME:
                goToFragment = new SelectProviderFragment();
                break;

            case PAGE_PROVIDER_SELECTION:
                int provider = bundle.getInt(DATA_PROVIDER, PROVIDER_NO);
                switch(provider) {
                    case PROVIDER_V2_WALLABAG_ORG:
                        goToFragment = new V2WallabagOrgConfigFragment();
                        break;

                    case PROVIDER_FRAMABAG:
                        goToFragment = new FramabagConfigFragment();
                        break;

                    default:
                        goToFragment = new GenericConfigFragment();
                        break;
                }
                break;

            case PAGE_CONFIG_GENERIC:
            case PAGE_CONFIG_FRAMABAG:
            case PAGE_CONFIG_V2_WALLABAG_ORG:
                goToFragment = new SummaryFragment();
                break;

            case PAGE_SUMMARY:
                finish();
                break;
        }

        if(goToFragment != null) {
            goToFragment.setArguments(bundle);

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, goToFragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            if(!noBackStack && !PAGE_NONE.equals(currentPage)) ft.addToBackStack(null);
            ft.commit();
        }
    }

    public static abstract class WizardPageFragment extends Fragment {

        protected ConnectionWizardActivity activity;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(getLayoutResourceID(), container, false);

            initButtons(v);

            return v;
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);

            if(context instanceof ConnectionWizardActivity) {
                activity = (ConnectionWizardActivity)context;
            }
        }

        @Override
        public void onDetach() {
            activity = null;

            super.onDetach();
        }

        public abstract String getPageName();

        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_welcome_fragment;
        }

        protected void initButtons(View v) {
            Button prevButton = (Button)v.findViewById(R.id.prev_button);
            Button nextButton = (Button)v.findViewById(R.id.next_button);

            if(prevButton != null) {
                prevButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prevButtonPressed();
                    }
                });
            }

            if(nextButton != null) {
                nextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        nextButtonPressed();
                    }
                });
            }
        }

        protected void prevButtonPressed() {
            goBack();
        }

        protected void nextButtonPressed() {
            goForward();
        }

        protected void goBack() {
            activity.prev(this, getArguments());
        }

        protected void goForward() {
            activity.next(this, getArguments());
        }

    }

    public static class WelcomeFragment extends WizardPageFragment {

        public String getPageName() {
            return PAGE_WELCOME;
        }

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_welcome_fragment;
        }

    }

    public static class SelectProviderFragment extends WizardPageFragment {

        public String getPageName() {
            return PAGE_PROVIDER_SELECTION;
        }

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_provider_selection_fragment;
        }

        @Override
        protected void nextButtonPressed() {
            Bundle bundle = getArguments();

            View view = getView();
            if(view != null) {
                RadioGroup radioGroup = (RadioGroup)view.findViewById(R.id.providerRadioGroup);
                int provider;
                switch(radioGroup.getCheckedRadioButtonId()) {
                    case R.id.providerV2WallabagOrg:
                        provider = PROVIDER_V2_WALLABAG_ORG;
                        break;

                    case R.id.providerFramabag:
                        provider = PROVIDER_FRAMABAG;
                        break;

                    default:
                        provider = PROVIDER_NO;
                        break;
                }

                bundle.putInt(DATA_PROVIDER, provider);
            }

            activity.next(this, bundle);
        }

    }

    public static class GenericConfigFragment extends WizardPageFragment
            implements ConfigurationTestHelper.GetCredentialsHandler,
            ConfigurationTestHelper.ResultHandler {

        protected String url;
        protected String username, password;
        protected String feedsUserID, feedsToken;
        protected String httpAuthUsername, httpAuthPassword;
        protected boolean customSSLSettings = Settings.getDefaultCustomSSLSettingsValue();
        protected boolean acceptAllCertificates;
        protected int wallabagServerVersion = -1;
        protected boolean tryPossibleURLs = true;

        protected ConfigurationTestHelper configurationTestHelper;

        public String getPageName() {
            return PAGE_CONFIG_GENERIC;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);

            if (v != null) {
                EditText urlEditText = (EditText) v.findViewById(R.id.wallabag_url);
                EditText usernameEditText = (EditText) v.findViewById(R.id.username);

                if (urlEditText != null && getArguments().containsKey(DATA_URL)) {
                    urlEditText.setText(getArguments().getString(DATA_URL));
                }

                if (usernameEditText != null && getArguments().containsKey(DATA_USERNAME)) {
                    usernameEditText.setText(getArguments().getString(DATA_USERNAME));
                }
            }

            return v;
        }

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_generic_config_fragment;
        }

        @Override
        protected void nextButtonPressed() {
            gatherData();
            runTest();
        }

        protected void gatherData() {
            View view = getView();
            if(view == null) return;

            EditText urlEditText = (EditText)view.findViewById(R.id.wallabag_url);
            EditText usernameEditText = (EditText)view.findViewById(R.id.username);
            EditText passwordEditText = (EditText)view.findViewById(R.id.password);

            url = urlEditText.getText().toString();
            username = usernameEditText.getText().toString();
            password = passwordEditText.getText().toString();
        }

        protected void runTest() {
            cancelTest();

            configurationTestHelper = new ConfigurationTestHelper(
                    activity, this, this, url, username, password, feedsUserID, feedsToken,
                    httpAuthUsername, httpAuthPassword, customSSLSettings, acceptAllCertificates,
                    wallabagServerVersion, tryPossibleURLs, false);
            configurationTestHelper.test();
        }

        protected void cancelTest() {
            if(configurationTestHelper != null) {
                configurationTestHelper.cancel();
                configurationTestHelper = null;
            }
        }

        @Override
        public void onGetCredentialsResult(String feedsUserID, String feedsToken) {
            this.feedsUserID = feedsUserID;
            this.feedsToken = feedsToken;
        }

        @Override
        public void onGetCredentialsFail() {}

        @Override
        public void onConfigurationTestSuccess(String url, Integer wallabagServerVersion) {
            if(url != null) acceptSuggestion(url);
            if(wallabagServerVersion != null) this.wallabagServerVersion = wallabagServerVersion;

            populateBundleWithConnectionSettings();

            goForward();
        }

        @Override
        public void onConnectionTestFail(WallabagServiceEndpoint.ConnectionTestResult result,
                                         String details) {}

        @Override
        public void onFeedsTestFail(TestFeedsTask.Result result, String details) {}

        protected void acceptSuggestion(String newUrl) {
            if(newUrl != null) url = newUrl;
            Log.i(TAG, "acceptSuggestion() going with " + url);

            View view = getView();
            EditText urlEditText = view != null
                    ? (EditText)view.findViewById(R.id.wallabag_url) : null;
            if(urlEditText != null) urlEditText.setText(url);
        }

        protected void populateBundleWithConnectionSettings() {
            Bundle bundle = getArguments();
            bundle.putString(DATA_URL, url);
            bundle.putString(DATA_USERNAME, username);
            bundle.putString(DATA_PASSWORD, password);
            bundle.putString(DATA_FEEDS_USER_ID, feedsUserID);
            bundle.putString(DATA_FEEDS_TOKEN, feedsToken);
            bundle.putString(DATA_HTTP_AUTH_USERNAME, httpAuthUsername);
            bundle.putString(DATA_HTTP_AUTH_PASSWORD, httpAuthPassword);
            bundle.putBoolean(DATA_CUSTOM_SSL_SETTINGS, customSSLSettings);
            bundle.putBoolean(DATA_ACCEPT_ALL_CERTIFICATES, acceptAllCertificates);
            bundle.putInt(DATA_SERVER_VERSION, wallabagServerVersion);
        }

    }

    public static class V2WallabagOrgConfigFragment extends GenericConfigFragment {

        public String getPageName() {
            return PAGE_CONFIG_V2_WALLABAG_ORG;
        }

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_v2wallabagorg_config_fragment;
        }

        @Override
        protected void gatherData() {
            View view = getView();
            if(view == null) return;

            EditText usernameEditText = (EditText)view.findViewById(R.id.username);
            EditText passwordEditText = (EditText)view.findViewById(R.id.password);

            username = usernameEditText.getText().toString();
            password = passwordEditText.getText().toString();

            url = "https://v2.wallabag.org";
            wallabagServerVersion = 2;

            tryPossibleURLs = false;
        }

    }

    public static class FramabagConfigFragment extends GenericConfigFragment {

        public String getPageName() {
            return PAGE_CONFIG_FRAMABAG;
        }

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_framabag_config_fragment;
        }

        @Override
        protected void gatherData() {
            View view = getView();
            if(view == null) return;

            EditText usernameEditText = (EditText)view.findViewById(R.id.username);
            EditText passwordEditText = (EditText)view.findViewById(R.id.password);

            username = usernameEditText.getText().toString();
            password = passwordEditText.getText().toString();

            url = "https://framabag.org/u/" + username;
            wallabagServerVersion = 1;

            tryPossibleURLs = false;
        }

    }

    public static class SummaryFragment extends WizardPageFragment {

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle bundle = getArguments();
            if(bundle == null || !bundle.getBoolean(EXTRA_SHOW_SUMMARY)) {
                Activity activity = getActivity();
                if(activity != null) {
                    saveSettings();

                    Toast.makeText(activity, R.string.connectionWizard_summary_toastMessage,
                            Toast.LENGTH_SHORT).show();

                    activity.finish();
                }
            }
        }

        public String getPageName() {
            return PAGE_SUMMARY;
        }

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_summary_fragment;
        }

        @Override
        protected void nextButtonPressed() {
            saveSettings();
            goForward();
        }

        protected void saveSettings() {
            Bundle bundle = getArguments();

            Settings settings = App.getInstance().getSettings();

            settings.setUrl(bundle.getString(DATA_URL));
            settings.setUsername(bundle.getString(DATA_USERNAME));
            settings.setPassword(bundle.getString(DATA_PASSWORD));
            settings.setHttpAuthUsername(bundle.getString(DATA_HTTP_AUTH_USERNAME));
            settings.setHttpAuthPassword(bundle.getString(DATA_HTTP_AUTH_PASSWORD));
            settings.setCustomSSLSettings(bundle.getBoolean(DATA_CUSTOM_SSL_SETTINGS));
            settings.setAcceptAllCertificates(bundle.getBoolean(DATA_ACCEPT_ALL_CERTIFICATES));
            settings.setWallabagServerVersion(bundle.getInt(DATA_SERVER_VERSION));
            settings.setFeedsUserID(bundle.getString(DATA_FEEDS_USER_ID));
            settings.setFeedsToken(bundle.getString(DATA_FEEDS_TOKEN));
            settings.setConfigurationOk(true);
            settings.setConfigurationErrorShown(false);
            settings.setFirstRun(false);
        }

    }

    private class ConnectionData {
        String mUsername;
        String mUrl;

        public ConnectionData(String username, String url) {
            mUsername = username;
            mUrl = url;
        }
    }
}
