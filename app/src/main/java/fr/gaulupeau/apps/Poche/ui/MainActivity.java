package fr.gaulupeau.apps.Poche.ui;

import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.Map;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesProgressEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesStartedEvent;
import fr.gaulupeau.apps.Poche.network.Updater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.ConfigurationTestHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        TagListFragment.OnFragmentInteractionListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String STATE_SAVED_FRAGMENT_STATES = "saved_fragment_states";
    private static final String STATE_CURRENT_FRAGMENT = "active_fragment";
    private static final String STATE_SEARCH_QUERY = "search_query";

    private static final String FRAGMENT_ARTICLE_LISTS = "fragment_article_lists";
    private static final String FRAGMENT_TAG_LIST = "fragment_tag_list";
    private static final String FRAGMENT_TAGGED_ARTICLE_LISTS = "fragment_tagged_article_lists";

    private Settings settings;

    private ConfigurationTestHelper configurationTestHelper;

    private ProgressBar progressBar;

    private NavigationView navigationView;

    private MenuItem searchMenuItem;
    private boolean searchMenuItemExpanded;

    private boolean checkConfigurationOnResume;
    private AlertDialog checkConfigurationDialog;
    private boolean firstSyncDone;
    private boolean tryToUpdateOnResume;

    private boolean offlineQueuePending;

    private boolean updateRunning;

    private Map<String, Fragment.SavedState> savedFragmentStates = new HashMap<>();

    private String currentFragmentType;
    private Fragment currentFragment;

    private Sortable.SortOrder sortOrder;
    private Sortable.SortOrder tagsSortOrder;
    private String searchQuery;
    private String searchQueryPrevious;
    private boolean searchUIPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        Themes.applyTheme(this, false);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        handleIntent(getIntent());

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        settings = new Settings(this);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        progressBar = (ProgressBar)findViewById(R.id.progressBar);

        navigationView = (NavigationView)findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        firstSyncDone = settings.isFirstSyncDone();

        offlineQueuePending = settings.isOfflineQueuePending();

        sortOrder = settings.getListSortOrder();
        tagsSortOrder = settings.getTagListSortOrder();

        String currentFragmentType = null;

        if(savedInstanceState != null) {
            Log.v(TAG, "onCreate() restoring state");

            Bundle bundle = savedInstanceState.getBundle(STATE_SAVED_FRAGMENT_STATES);
            if(bundle != null) {
                for(String key: bundle.keySet()) {
                    savedFragmentStates.put(key, bundle.<Fragment.SavedState>getParcelable(key));
                }
            }

            currentFragmentType = savedInstanceState.getString(STATE_CURRENT_FRAGMENT);

            performSearch(savedInstanceState.getString(STATE_SEARCH_QUERY));
        }
        if(searchQuery == null) performSearch("");

        if(currentFragmentType == null) currentFragmentType = FRAGMENT_ARTICLE_LISTS;
        // TODO: set nav drawer selected item

        if(savedInstanceState == null) {
            setCurrentFragment(currentFragmentType);
        } else {
            currentFragment = getSupportFragmentManager().findFragmentByTag(currentFragmentType);
            this.currentFragmentType = currentFragmentType;
        }

        EventBus.getDefault().register(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if(Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            Log.v(TAG, "handleIntent() search intent; query: " + query);

            performSearch(query);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.v(TAG, "onSaveInstanceState()");

        if(!savedFragmentStates.isEmpty()) {
            Bundle bundle = new Bundle(savedFragmentStates.size());

            for(Map.Entry<String, Fragment.SavedState> e: savedFragmentStates.entrySet()) {
                bundle.putParcelable(e.getKey(), e.getValue());
            }

            outState.putBundle(STATE_SAVED_FRAGMENT_STATES, bundle);
        }

        outState.putString(STATE_CURRENT_FRAGMENT, currentFragmentType);
        outState.putString(STATE_SEARCH_QUERY, searchQuery);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Themes.checkTheme(this);

        checkConfigurationOnResume = true;

        if(!firstSyncDone) {
            tryToUpdateOnResume = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO: check logic
        if(checkConfigurationOnResume) {
            checkConfigurationOnResume = false;

            if(!Settings.checkFirstRunInit(this)) {
                if(!settings.isConfigurationOk() && checkConfigurationDialog == null) {
                    AlertDialog.Builder messageBox = new AlertDialog.Builder(this);
                    messageBox.setTitle(settings.isConfigurationErrorShown()
                            ? R.string.d_configurationIsQuestionable_title
                            : R.string.d_configurationChanged_title);
                    messageBox.setMessage(settings.isConfigurationErrorShown()
                            ? R.string.d_configurationIsQuestionable_message
                            : R.string.d_configurationChanged_message);
                    messageBox.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            testConfiguration();
                        }
                    });
                    messageBox.setNegativeButton(R.string.d_configurationChanged_answer_decline, null);
                    messageBox.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            checkConfigurationDialog = null;
                        }
                    });
                    checkConfigurationDialog = messageBox.show();
                }
            }
        }

        if(tryToUpdateOnResume) {
            tryToUpdateOnResume = false;

            updateAllFeedsIfDbIsEmpty();
        }
    }

    @Override
    protected void onStop() {
        cancelConfigurationTest();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        if(drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu()");

        if(searchMenuItemExpanded) {
            // options menu invalidation happened when searchMenuItem was expanded
            searchMenuItemExpanded = false;
            searchMenuItem = null;

            Log.i(TAG, "onCreateOptionsMenu() searchMenuItem was not collapsed!");
            Log.v(TAG, "onCreateOptionsMenu() searchQuery: " + searchQuery
                    + ", searchQueryPrevious: " + searchQueryPrevious);

            performSearch(searchQueryPrevious);
        }

        getMenuInflater().inflate(R.menu.main, menu);

        searchMenuItem = menu.findItem(R.id.menu_main_search);
        MenuItemCompat.setOnActionExpandListener(
                searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                Log.v(TAG, "searchMenuItem expanded");
                searchMenuItemExpanded = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                Log.v(TAG, "searchMenuItem collapsed");
                searchMenuItemExpanded = false;
                return true;
            }
        });

        final SearchView searchView = (SearchView)searchMenuItem.getActionView();
        if(searchView != null) {
            searchView.setSearchableInfo(((SearchManager)getSystemService(Context.SEARCH_SERVICE))
                    .getSearchableInfo(getComponentName()));

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    Log.v(TAG, "onQueryTextSubmit() query: " + query);

                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    Log.v(TAG, "onQueryTextChange() newText: " + newText);

                    setSearchQuery(newText);

                    return true;
                }
            });
        }
        checkPendingSearchUI();

        if(!offlineQueuePending) {
            MenuItem menuItem = menu.findItem(R.id.menu_main_syncQueue);
            if(menuItem != null) {
                menuItem.setVisible(false);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_main_changeSortOrder:
                switchSortOrder();
                return true;

            case R.id.menu_main_syncQueue:
                syncQueue();
                return true;

            case R.id.menu_main_fullUpdate:
                fullUpdate(true);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()) {
            case R.id.nav_mainLists:
                setCurrentFragment(FRAGMENT_ARTICLE_LISTS);
                break;

            case R.id.nav_tags:
                setCurrentFragment(FRAGMENT_TAG_LIST);
                break;

            case R.id.nav_add:
                startActivity(new Intent(getBaseContext(), AddActivity.class));
                break;

            case R.id.nav_settings:
                startActivity(new Intent(getBaseContext(), SettingsActivity.class));
                break;

            case R.id.nav_about:
                Libs.ActivityStyle style;
                switch(Themes.getCurrentTheme()) {
                    case DARK:
                    case DARK_CONTRAST:
                        style = Libs.ActivityStyle.DARK;
                        break;

                    default:
                        style = Libs.ActivityStyle.LIGHT_DARK_TOOLBAR;
                        break;
                }
                new LibsBuilder()
                        .withActivityStyle(style)
                        .withAboutIconShown(true)
                        .withAboutVersionShown(true)
                        .withAboutDescription(getResources().getString(R.string.aboutText))
                        .start(this);
                break;
        }

        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN, priority = -1)
    public void onOfflineQueueChangedEvent(OfflineQueueChangedEvent event) {
        Log.d(TAG, "onOfflineQueueChangedEvent() started");

        Long queueLength = event.getQueueLength();

        boolean prevValue = offlineQueuePending;
        offlineQueuePending = queueLength == null || queueLength > 0;

        Log.d(TAG, "onOfflineQueueChangedEvent() offlineQueuePending: " + offlineQueuePending);

        if(prevValue != offlineQueuePending) {
            Log.d(TAG, "onOfflineQueueChangedEvent() invalidating options menu");
            invalidateOptionsMenu();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedsChangedEvent(FeedsChangedEvent event) {
        Log.d(TAG, "Got FeedsChangedEvent");

        if(event.isInvalidateAll()) {
            firstSyncDone = settings.isFirstSyncDone();
        }

        if(currentFragment instanceof ArticleListsFragment) {
            ((ArticleListsFragment)currentFragment).onFeedsChangedEvent(event);
        } else if(currentFragment instanceof RecyclerViewListFragment) {
            ((RecyclerViewListFragment)currentFragment).invalidateList();
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateArticlesStartedEvent(UpdateArticlesStartedEvent event) {
        Log.d(TAG, "onUpdateArticlesStartedEvent");

        updateStateChanged(true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateArticlesProgressEvent(UpdateArticlesProgressEvent event) {
        Log.d(TAG, "onUpdateArticlesProgressEvent");

        if(progressBar != null) {
            progressBar.setIndeterminate(false);
            progressBar.setMax(event.getTotal());
            progressBar.setProgress(event.getCurrent());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateArticlesFinishedEvent(UpdateArticlesFinishedEvent event) {
        Log.d(TAG, "onUpdateArticlesFinishedEvent");

        if(event.getResult().isSuccess()) {
            firstSyncDone = true;
            tryToUpdateOnResume = false;
        }

        updateStateChanged(false);
    }

    private void updateStateChanged(boolean started) {
        if(started == updateRunning) return;

        updateRunning = started;

        if(progressBar != null) {
            progressBar.setVisibility(started ? View.VISIBLE : View.GONE);
            progressBar.setIndeterminate(true);
        }
    }

    private void performSearch(String query) {
        setSearchQuery(query);

        if(TextUtils.isEmpty(query)) return;

        searchUIPending = true;
        checkPendingSearchUI();
    }

    private void checkPendingSearchUI() {
        if(searchMenuItem == null) return;
        if(!searchUIPending) return;

        searchUIPending = false;

        initSearchUI();
    }

    private void initSearchUI() {
        final SearchView searchView = (SearchView)searchMenuItem.getActionView();
        if(searchView == null) return;

        final String searchQueryToRestore = searchQuery;

        MenuItemCompat.expandActionView(searchMenuItem);

        searchView.post(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "searchView.post() restoring search string: " + searchQueryToRestore);
                searchView.setQuery(searchQueryToRestore, false);
            }
        });
    }

    private void setParametersToFragment(Fragment fragment) {
        Log.v(TAG, "setParametersToFragment() started");
        if(fragment == null) return;

        setSortOrder(fragment);
        setSearchQueryOnFragment(fragment, searchQuery);
    }

    private void switchSortOrder() {
        if(FRAGMENT_TAG_LIST.equals(currentFragmentType)) {
            tagsSortOrder = tagsSortOrder == Sortable.SortOrder.DESC
                    ? Sortable.SortOrder.ASC
                    : Sortable.SortOrder.DESC;

            settings.setTagListSortOrder(tagsSortOrder);
        } else {
            sortOrder = sortOrder == Sortable.SortOrder.DESC
                    ? Sortable.SortOrder.ASC
                    : Sortable.SortOrder.DESC;

            settings.setListSortOrder(sortOrder);
        }

        setSortOrder(currentFragment);
    }

    private void setSortOrder(Fragment fragment) {
        setSortOrder(fragment, FRAGMENT_TAG_LIST.equals(currentFragmentType)
                ? tagsSortOrder : sortOrder);
    }

    private void setSortOrder(Fragment fragment, Sortable.SortOrder sortOrder) {
        if(fragment instanceof Sortable) {
            ((Sortable)fragment).setSortOrder(sortOrder);
        }
    }

    private void setSearchQuery(String searchQuery) {
        this.searchQueryPrevious = this.searchQuery;
        this.searchQuery = searchQuery;

        setSearchQueryOnFragment(currentFragment, searchQuery);
    }

    private void setSearchQueryOnFragment(Fragment fragment, String searchQuery) {
        if(fragment instanceof Searchable) {
            ((Searchable)fragment).setSearchQuery(searchQuery);
        }
    }

    private void setCurrentFragment(String type) {
        if(TextUtils.equals(currentFragmentType, type)) {
            Log.i(TAG, "setCurrentFragment() ignoring switch to the same type: " + type);
            return;
        }

        setCurrentFragment(getFragment(type), type);
    }

    private void setCurrentFragment(Fragment fragment, String type) {
        updateNavigationUI(type);

        if(currentFragment != null && isFragmentStateSavable(currentFragmentType)) {
            Log.d(TAG, "setCurrentFragment() saving fragment state: " + currentFragmentType);

            savedFragmentStates.put(currentFragmentType, getSupportFragmentManager()
                    .saveFragmentInstanceState(currentFragment));
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_content_frame, fragment, type)
                .commit();

        currentFragment = fragment;
        currentFragmentType = type;

        setParametersToFragment(fragment);
    }

    private Fragment getFragment(String type) {
        Log.d(TAG, "getFragment() type: " + type);

        Fragment fragment = getSupportFragmentManager().findFragmentByTag(type);

        if(fragment == null) {
            Log.d(TAG, "getFragment() creating new instance");

            switch(type) {
                case FRAGMENT_ARTICLE_LISTS:
                    fragment = ArticleListsFragment.newInstance(null);
                    break;

                case FRAGMENT_TAG_LIST:
                    fragment = new TagListFragment();
                    break;

                default:
                    throw new IllegalArgumentException("Fragment type is not supported: " + type);
            }

            if(isFragmentStateSavable(type)) {
                Log.d(TAG, "getFragment() fragment is savable");

                Fragment.SavedState savedState = savedFragmentStates.get(type);
                if(savedState != null) {
                    Log.d(TAG, "getFragment() restoring fragment state");

                    fragment.setInitialSavedState(savedState);
                }
            }
        }

        return fragment;
    }

    private boolean isFragmentStateSavable(String type) {
        if(type == null) return false;

        switch(type) {
            case FRAGMENT_ARTICLE_LISTS:
            case FRAGMENT_TAG_LIST:
                return true;
        }

        return false;
    }

    private void updateNavigationUI(String type) {
        if(type == null || navigationView == null) return;
        if(TextUtils.equals(type, currentFragmentType)) return;

        if(FRAGMENT_TAGGED_ARTICLE_LISTS.equals(currentFragmentType)) {
            MenuItem item = navigationView.getMenu().findItem(R.id.nav_taggedLists);
            if(item != null) {
                item.setVisible(false);
                item.setEnabled(false);
            }
        }

        @IdRes int itemID = 0;
        switch(type) {
            case FRAGMENT_ARTICLE_LISTS:
                itemID = R.id.nav_mainLists;
                break;

            case FRAGMENT_TAG_LIST:
                itemID = R.id.nav_tags;
                break;

            case FRAGMENT_TAGGED_ARTICLE_LISTS:
                itemID = R.id.nav_taggedLists;
                MenuItem item = navigationView.getMenu().findItem(itemID);
                if(item != null) {
                    item.setVisible(true);
                    item.setEnabled(true);
                }
                break;
        }

        if(itemID != 0) {
            navigationView.setCheckedItem(itemID);

            MenuItem item = navigationView.getMenu().findItem(itemID);
            if(item != null) {
                setTitle(item.getTitle());
            }
        }
    }

    @Override
    public void onTagSelected(Tag tag) {
        Fragment fragment = ArticleListsFragment.newInstance(tag.getLabel());

        setCurrentFragment(fragment, FRAGMENT_TAGGED_ARTICLE_LISTS);
    }

    @Override
    public void onRecyclerViewListSwipeUpdate() {
        updateArticles(true, Updater.UpdateType.FAST);
    }

    private void syncQueue() {
        if(!WallabagConnection.isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            return;
        }

        ServiceHelper.syncQueue(this);
    }

    private void updateAllFeedsIfDbIsEmpty() {
        if(settings.isConfigurationOk() && !settings.isFirstSyncDone()) {
            fullUpdate(false);
        }
    }

    private void fullUpdate(boolean showErrors) {
        updateArticles(showErrors, Updater.UpdateType.FULL);
    }

    private boolean updateArticles(boolean showErrors, Updater.UpdateType updateType) {
        boolean result = false;

        if(updateRunning) {
            if(showErrors) {
                Toast.makeText(this, R.string.updateFeed_previousUpdateNotFinished,
                        Toast.LENGTH_SHORT).show();
            }
        } else if(!settings.isConfigurationOk()) {
            if(showErrors) {
                Toast.makeText(this, getString(R.string.txtConfigNotSet), Toast.LENGTH_SHORT).show();
            }
        } else if(WallabagConnection.isNetworkAvailable()) {
            ServiceHelper.syncAndUpdate(this, updateType, false, settings);

            result = true;
        } else {
            if(showErrors) {
                Toast.makeText(this, getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            }
        }

        return result;
    }

    private void testConfiguration() {
        cancelConfigurationTest();

        configurationTestHelper = new ConfigurationTestHelper(
                this, new ConfigurationTestHelper.ResultHandler() {
            @Override
            public void onConfigurationTestSuccess(String url) {
                updateAllFeedsIfDbIsEmpty();
            }

            @Override
            public void onConnectionTestFail(
                    WallabagWebService.ConnectionTestResult result, String details) {}

            @Override
            public void onApiAccessTestFail(TestApiAccessTask.Result result, String details) {}
        }, null, settings, true);

        configurationTestHelper.test();
    }

    private void cancelConfigurationTest() {
        if(configurationTestHelper != null) {
            configurationTestHelper.cancel();
            configurationTestHelper = null;
        }
    }

}
