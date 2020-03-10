package fr.gaulupeau.apps.Poche.ui.preferences;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.text.TextUtils;
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
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.ui.BaseActionBarActivity;
import fr.gaulupeau.apps.Poche.network.ClientCredentials;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;

// TODO: split classes?
public class ConnectionWizardActivity extends BaseActionBarActivity {

    public static final String EXTRA_SKIP_WELCOME = "skip_welcome";
    public static final String EXTRA_SHOW_SUMMARY = "show_summary";
    public static final String EXTRA_FILL_OUT_FROM_SETTINGS = "fill_out_from_settings";

    private static final String TAG = "ConnectionWizard";

    private static final int REQUEST_CODE_QR_CODE = 1;

    private static final String DATA_PROVIDER = "provider";
    private static final String DATA_URL = "url";
    private static final String DATA_USERNAME = "username";
    private static final String DATA_PASSWORD = "password";
    private static final String DATA_HTTP_AUTH_USERNAME = "http_auth_username";
    private static final String DATA_HTTP_AUTH_PASSWORD = "http_auth_password";
    private static final String DATA_API_CLIENT_ID = "api_client_id";
    private static final String DATA_API_CLIENT_SECRET = "api_client_secret";

    private static final int PROVIDER_NO = -1;
    private static final int PROVIDER_WALLABAG_IT = 0;
    private static final int PROVIDER_FRAMABAG = 1;

    private static final String PAGE_NONE = "";
    private static final String PAGE_WELCOME = "welcome";
    private static final String PAGE_PROVIDER_SELECTION = "provider_selection";
    private static final String PAGE_CONFIG_GENERIC = "config_generic";
    private static final String PAGE_CONFIG_WALLABAG_IT = "config_wallabag_it";
    private static final String PAGE_CONFIG_FRAMABAG = "config_framabag";
    private static final String PAGE_SUMMARY = "summary";

    public static void runWizard(Context context, boolean skipWelcome) {
        runWizard(context, skipWelcome, false, false);
    }

    public static void runWizard(Context context, boolean skipWelcome, boolean showSummary,
                                 boolean fillOutFromSettings) {
        Intent intent = new Intent(context, ConnectionWizardActivity.class);
        if(skipWelcome) intent.putExtra(EXTRA_SKIP_WELCOME, true);
        if(showSummary) intent.putExtra(EXTRA_SHOW_SUMMARY, true);
        if(fillOutFromSettings) intent.putExtra(EXTRA_FILL_OUT_FROM_SETTINGS, true);
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

            boolean fillOutData = false;
            String url = null, username = null, password = null;

            String dataString = intent.getDataString();
            if(dataString != null) {
                Log.d(TAG, "onCreate() got data string: " + dataString);
                try {
                    ConnectionData connectionData = parseLoginData(dataString);

                    url = connectionData.url;
                    username = connectionData.username;
                    password = connectionData.password;

                    fillOutData = true;
                } catch(IllegalArgumentException e) {
                    Log.w(TAG, "onCreate() login data parsing exception", e);
                    Toast.makeText(this, R.string.connectionWizard_misc_incorrectConnectionURI,
                            Toast.LENGTH_SHORT).show();
                }
            }

            if(intent.getBooleanExtra(EXTRA_FILL_OUT_FROM_SETTINGS, false)) {
                Settings settings = App.getInstance().getSettings();

                url = settings.getUrl();
                username = settings.getUsername();
                password = settings.getPassword();

                fillOutData = true;
            }

            if(fillOutData) {
                boolean filledOutSomething = false;

                if(WallabagItConfigFragment.WALLABAG_IT_HOSTNAME.equals(url)
                        || ("https://" + WallabagItConfigFragment.WALLABAG_IT_HOSTNAME).equals(url)) {
                    bundle.putInt(DATA_PROVIDER, PROVIDER_WALLABAG_IT);
                    filledOutSomething = true;
                } else if(FramabagConfigFragment.FRAMABAG_HOSTNAME.equals(url)
                        || ("https://" + FramabagConfigFragment.FRAMABAG_HOSTNAME).equals(url)) {
                    bundle.putInt(DATA_PROVIDER, PROVIDER_FRAMABAG);
                    filledOutSomething = true;
                } else if(!TextUtils.isEmpty(url) && !"https://".equals(url)) {
                    bundle.putString(DATA_URL, url);
                    filledOutSomething = true;
                }

                if(!TextUtils.isEmpty(username)) {
                    bundle.putString(DATA_USERNAME, username);
                    filledOutSomething = true;
                }
                if(!TextUtils.isEmpty(password)) {
                    bundle.putString(DATA_PASSWORD, password);
                    filledOutSomething = true;
                }

                if(filledOutSomething) currentPage = PAGE_PROVIDER_SELECTION;
            }

            if(currentPage == null && intent.getBooleanExtra(EXTRA_SKIP_WELCOME, false)) {
                currentPage = PAGE_WELCOME;
            }

            next(currentPage, bundle, true);
        } else {
            Log.d(TAG, "onCreate() savedInstanceState != null");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_CODE_QR_CODE) {
            if(resultCode == RESULT_OK) {
                String resultString = data.getStringExtra("SCAN_RESULT");
                Log.d(TAG, "onActivityResult() got string: " + resultString);

                if(resultString == null) return;

                Uri uri = Uri.parse(resultString);
                if(!"wallabag".equals(uri.getScheme())) {
                    Log.i(TAG, "onActivityResult() unrecognized URI scheme: " + uri.getScheme());
                    Toast.makeText(this, R.string.connectionWizard_misc_incorrectConnectionURI,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = getIntent();
                intent.setData(uri);
                intent.removeExtra(EXTRA_FILL_OUT_FROM_SETTINGS);
                startActivity(intent);
                finish();
            }
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

        if(values.length != 2) {
            // error illegal number of URI elements detected
            throw new IllegalArgumentException("Illegal number of login URL elements detected: " + values.length);
        }

        String username = values[0];
        String password = null;
        if(username.contains(":")) {
            int index = username.indexOf(":");
            password = username.substring(index + 1);
            username = username.substring(0, index);
        }

        return new ConnectionData(username, password, values[1]);
    }

    private void scanQrCode() {
        try {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");

            startActivityForResult(intent, REQUEST_CODE_QR_CODE);
        } catch(ActivityNotFoundException e) {
            Log.i(TAG, "scanQrCode() exception", e);

            Toast.makeText(this, R.string.connectionWizard_misc_installQrCodeScanner,
                    Toast.LENGTH_LONG).show();

            Uri marketUri = Uri.parse("market://details?id=de.markusfisch.android.binaryeye");
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
            startActivity(marketIntent);
        } catch(Exception e) {
            Log.w(TAG, "scanQrCode() exception", e);
        }
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
                    case PROVIDER_WALLABAG_IT:
                        goToFragment = new WallabagItConfigFragment();
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
            case PAGE_CONFIG_WALLABAG_IT:
            case PAGE_CONFIG_FRAMABAG:
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
            ft.commitAllowingStateLoss();
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
        protected void initButtons(View v) {
            super.initButtons(v);

            Button scanCodeButton = (Button)v.findViewById(R.id.scanQrCodeButton);
            if(scanCodeButton != null) {
                scanCodeButton.setOnClickListener(v1 -> activity.scanQrCode());
            }
        }

        @Override
        protected void nextButtonPressed() {
            Bundle bundle = getArguments();

            View view = getView();
            if(view != null) {
                RadioGroup radioGroup = (RadioGroup)view.findViewById(R.id.providerRadioGroup);
                int provider;
                switch(radioGroup.getCheckedRadioButtonId()) {
                    case R.id.providerWallabagIt:
                        provider = PROVIDER_WALLABAG_IT;
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
        protected String clientID, clientSecret;
        protected String httpAuthUsername, httpAuthPassword;
        protected boolean tryPossibleURLs = true;

        protected ConfigurationTestHelper configurationTestHelper;

        public String getPageName() {
            return PAGE_CONFIG_GENERIC;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);

            if (v != null) {
                EditText urlEditText = (EditText)v.findViewById(R.id.wallabag_url);
                EditText usernameEditText = (EditText)v.findViewById(R.id.username);
                EditText passwordEditText = (EditText)v.findViewById(R.id.password);

                if(urlEditText != null && getArguments().containsKey(DATA_URL)) {
                    urlEditText.setText(getArguments().getString(DATA_URL));
                }

                if(usernameEditText != null && getArguments().containsKey(DATA_USERNAME)) {
                    usernameEditText.setText(getArguments().getString(DATA_USERNAME));
                }
                if(passwordEditText != null && getArguments().containsKey(DATA_PASSWORD)) {
                    passwordEditText.setText(getArguments().getString(DATA_PASSWORD));
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

            if(urlEditText != null) url = urlEditText.getText().toString();
            if(usernameEditText != null) username = usernameEditText.getText().toString();
            if(passwordEditText != null) password = passwordEditText.getText().toString();
        }

        protected void runTest() {
            cancelTest();

            configurationTestHelper = new ConfigurationTestHelper(
                    activity, this, this, url, httpAuthUsername, httpAuthPassword,
                    username, password, clientID, clientSecret, tryPossibleURLs, false);
            configurationTestHelper.test();
        }

        protected void cancelTest() {
            if(configurationTestHelper != null) {
                configurationTestHelper.cancel();
                configurationTestHelper = null;
            }
        }

        @Override
        public void onGetCredentialsResult(ClientCredentials clientCredentials) {
            this.clientID = clientCredentials.clientID;
            this.clientSecret = clientCredentials.clientSecret;
        }

        @Override
        public void onGetCredentialsFail() {}

        @Override
        public void onConfigurationTestSuccess(String url) {
            if(url != null) acceptSuggestion(url);

            populateBundleWithConnectionSettings();

            goForward();
        }

        @Override
        public void onConnectionTestFail(WallabagWebService.ConnectionTestResult result,
                                         String details) {}

        @Override
        public void onApiAccessTestFail(TestApiAccessTask.Result result, String details) {}

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
            bundle.putString(DATA_API_CLIENT_ID, clientID);
            bundle.putString(DATA_API_CLIENT_SECRET, clientSecret);
            bundle.putString(DATA_HTTP_AUTH_USERNAME, httpAuthUsername);
            bundle.putString(DATA_HTTP_AUTH_PASSWORD, httpAuthPassword);
        }

    }

    public static class WallabagItConfigFragment extends GenericConfigFragment {

        public String getPageName() {
            return PAGE_CONFIG_WALLABAG_IT;
        }

        static final String WALLABAG_IT_HOSTNAME = "app.wallabag.it";

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_wallabagit_config_fragment;
        }

        @Override
        protected void gatherData() {
            super.gatherData();

            url = "https://" + WALLABAG_IT_HOSTNAME;
            tryPossibleURLs = false;
        }

    }

    public static class FramabagConfigFragment extends GenericConfigFragment {

        public String getPageName() {
            return PAGE_CONFIG_FRAMABAG;
        }

        static final String FRAMABAG_HOSTNAME = "framabag.org";

        @Override
        protected int getLayoutResourceID() {
            return R.layout.connection_wizard_framabag_config_fragment;
        }

        @Override
        protected void gatherData() {
            super.gatherData();

            url = "https://" + FRAMABAG_HOSTNAME;
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

            String url = bundle.getString(DATA_URL);
            String username = bundle.getString(DATA_USERNAME);
            String httpAuthUsername = bundle.getString(DATA_HTTP_AUTH_USERNAME);
            String clientID = bundle.getString(DATA_API_CLIENT_ID);

            boolean newUrl = !TextUtils.equals(settings.getUrl(), url);
            boolean newUser = newUrl
                    || !TextUtils.equals(settings.getUsername(), username)
                    || !TextUtils.equals(settings.getHttpAuthUsername(), httpAuthUsername)
                    || !TextUtils.equals(settings.getApiClientID(), clientID);

            settings.setUrl(url);
            settings.setUsername(username);
            settings.setPassword(bundle.getString(DATA_PASSWORD));
            settings.setHttpAuthUsername(httpAuthUsername);
            settings.setHttpAuthPassword(bundle.getString(DATA_HTTP_AUTH_PASSWORD));
            settings.setApiClientID(clientID);
            settings.setApiClientSecret(bundle.getString(DATA_API_CLIENT_SECRET));
            settings.setConfigurationOk(true);
            settings.setConfigurationErrorShown(false);
            settings.setFirstRun(false);

            if(newUser) {
                settings.setApiRefreshToken("");
                settings.setApiAccessToken("");

                if(newUrl) {
                    WallabagConnection.resetWallabagService();
                }

                OperationsHelper.wipeDB(settings);
            }
        }

    }

    private class ConnectionData {
        String username;
        String password;
        String url;

        ConnectionData(String username, String password, String url) {
            this.username = username;
            this.password = password;
            this.url = url;
        }
    }
}
