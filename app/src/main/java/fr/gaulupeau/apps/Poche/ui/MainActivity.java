package fr.gaulupeau.apps.Poche.ui;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.Fragment;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
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
import fr.gaulupeau.apps.Poche.service.workers.ArticleUpdater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.ConfigurationTestHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        TagListFragment.OnFragmentInteractionListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String STATE_SAVED_FRAGMENT_STATES = "saved_fragment_states";
    private static final String STATE_CURRENT_FRAGMENT = "active_fragment";
    private static final String STATE_SEARCH_QUERY = "search_query";
    private static final String STATE_SELECTED_TAG = "selected_tag";

    private static final String FRAGMENT_ARTICLE_LISTS = "fragment_article_lists";
    private static final String FRAGMENT_TAG_LIST = "fragment_tag_list";
    private static final String FRAGMENT_TAGGED_ARTICLE_LISTS = "fragment_tagged_article_lists";

    private Settings settings;

    private ConfigurationTestHelper configurationTestHelper;

    private ProgressBar progressBar;

    private NavigationView navigationView;
    private TextView lastUpdateTimeView;

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
    private String selectedTag;

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

        navigationView = (NavigationView)findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if(navigationView != null) {
            View headerView = navigationView.getHeaderView(0);
            if(headerView != null) {
                lastUpdateTimeView = (TextView)headerView.findViewById(R.id.lastUpdateTime);
            }

            // Set different colors for items in the navigation bar in dark (high contrast) theme
            if (Themes.getCurrentTheme() != null && Themes.getCurrentTheme() == Themes.Theme.DARK_CONTRAST) {
                @SuppressLint("ResourceType") XmlResourceParser parser = getResources().getXml(R.color.dark_contrast_menu_item);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        navigationView.setItemTextColor(ColorStateList.createFromXml(getResources(), parser, getTheme()));
                        navigationView.setItemIconTintList(ColorStateList.createFromXml(getResources(), parser, getTheme()));

                    } else {
                        navigationView.setItemTextColor(ColorStateList.createFromXml(getResources(), parser));
                        navigationView.setItemIconTintList(ColorStateList.createFromXml(getResources(), parser));
                    }
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            boolean updated;

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                updateTime();
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                if(newState == DrawerLayout.STATE_IDLE) updated = false;
            }

            private void updateTime() {
                if(updated) return;
                updated = true;

                if(lastUpdateTimeView == null) return;

                Log.d(TAG, "DrawerListener.updateTime() updating time");

                updateLastUpdateTime();
            }
        });

        progressBar = (ProgressBar)findViewById(R.id.progressBar);

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

            selectedTag = savedInstanceState.getString(STATE_SELECTED_TAG);

            performSearch(savedInstanceState.getString(STATE_SEARCH_QUERY));
        }
        if(searchQuery == null) performSearch("");

        if(currentFragmentType == null) currentFragmentType = FRAGMENT_ARTICLE_LISTS;

        if(savedInstanceState == null) {
            setCurrentFragment(currentFragmentType);
        } else {
            currentFragment = getSupportFragmentManager().findFragmentByTag(currentFragmentType);
            this.currentFragmentType = currentFragmentType;
            updateNavigationUI(currentFragmentType);
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
        outState.putString(STATE_SELECTED_TAG, selectedTag);
        outState.putString(STATE_SEARCH_QUERY, searchQuery);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Themes.checkTheme(this);

        checkConfigurationOnResume = true;

        tryToUpdateOnResume = true;
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

            if(!firstSyncDone) {
                updateAllFeedsIfDbIsEmpty();
            } else {
                updateOnStartup();
            }
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
                supportInvalidateOptionsMenu();
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

            case R.id.menu_main_sweepDeletedArticles:
                sweepDeletedArticles();
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
                showAddBagDialog();
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
                CharSequence aboutCharSequence = getText(R.string.aboutText);
                String aboutString = aboutCharSequence instanceof Spanned
                        ? Html.toHtml((Spanned) aboutCharSequence)
                        : aboutCharSequence.toString();
                new LibsBuilder()
                        .withActivityStyle(style)
                        .withAboutIconShown(true)
                        .withAboutVersionShown(true)
                        .withAboutDescription(aboutString)
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

        updateLastUpdateTime();

        updateStateChanged(false);
    }

    private void updateLastUpdateTime() {
        if(lastUpdateTimeView == null) return;

        Log.d(TAG, "updateLastUpdateTime() updating time");

        long timestamp = settings.getLatestUpdateRunTimestamp();
        if(timestamp != 0) {
            lastUpdateTimeView.setText(getString(R.string.lastUpdateTimeLabel,
                    DateUtils.getRelativeTimeSpanString(timestamp)));
        } else {
            lastUpdateTimeView.setVisibility(View.INVISIBLE);
        }
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

        if(FRAGMENT_TAGGED_ARTICLE_LISTS.equals(currentFragmentType)) {
            MenuItem item = navigationView.getMenu().findItem(R.id.nav_taggedLists);
            if(item != null) {
                item.setVisible(false);
                item.setEnabled(false);
            }
        }

        CharSequence title = null;
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

                if(selectedTag != null) {
                    title = getString(R.string.title_main_tag, selectedTag);
                }

                MenuItem item = navigationView.getMenu().findItem(itemID);
                if(item != null) {
                    if(title != null) item.setTitle(title);
                    item.setVisible(true);
                    item.setEnabled(true);
                }
                break;
        }

        if(itemID != 0) {
            navigationView.setCheckedItem(itemID);

            if(title == null) {
                MenuItem item = navigationView.getMenu().findItem(itemID);
                if(item != null) {
                    title = item.getTitle();
                }
            }
        }
        if(title != null) {
            setTitle(title);
        }
    }

    @Override
    public void onTagSelected(Tag tag) {
        selectedTag = tag.getLabel();

        Fragment fragment = ArticleListsFragment.newInstance(tag.getLabel());

        setCurrentFragment(fragment, FRAGMENT_TAGGED_ARTICLE_LISTS);
    }

    @Override
    public void onRecyclerViewListSwipeUpdate() {
        updateArticles(true, ArticleUpdater.UpdateType.FAST);
    }

    private void syncQueue() {
        if(!WallabagConnection.isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            return;
        }

        OperationsHelper.syncQueue(this);
    }

    private void sweepDeletedArticles() {
        if(!WallabagConnection.isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            return;
        }

        OperationsHelper.sweepDeletedArticles(this);
    }

    private void updateAllFeedsIfDbIsEmpty() {
        if(settings.isConfigurationOk() && !settings.isFirstSyncDone()) {
            fullUpdate(false);
        }
    }

    private void updateOnStartup() {
        long delay = 5 * 60 * 1000; // 5 minutes
        if(settings.isAutoSyncOnStartupEnabled() && settings.isConfigurationOk()
                && settings.isFirstSyncDone()
                && settings.getLatestUpdateRunTimestamp() + delay < System.currentTimeMillis()) {
            updateArticles(false, ArticleUpdater.UpdateType.FAST);
        }
    }

    private void fullUpdate(boolean showErrors) {
        updateArticles(showErrors, ArticleUpdater.UpdateType.FULL);
    }

    private boolean updateArticles(boolean showErrors, ArticleUpdater.UpdateType updateType) {
        boolean result = false;

        if(updateRunning) {
            if(showErrors) {
                Toast.makeText(this, R.string.previousUpdateNotFinished, Toast.LENGTH_SHORT).show();
            }
        } else if(!settings.isConfigurationOk()) {
            if(showErrors) {
                Toast.makeText(this, getString(R.string.txtConfigNotSet), Toast.LENGTH_SHORT).show();
            }
        } else if(WallabagConnection.isNetworkAvailable()) {
            OperationsHelper.syncAndUpdate(this, settings, updateType, false);

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

    private void showAddBagDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_add_label);

        @SuppressLint("InflateParams")
        final View view = getLayoutInflater().inflate(R.layout.dialog_add, null);

        builder.setView(view);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TextView textView = view.findViewById(R.id.page_url);
                OperationsHelper.addArticleWithUI(getBaseContext(),
                        textView.getText().toString(), null);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        builder.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(currentFragment instanceof ArticleListsFragment) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    ((ArticleListsFragment) currentFragment).scroll(keyCode == KeyEvent.KEYCODE_PAGE_UP);
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (settings.isVolumeButtonsScrollingEnabled()) {
                        ((ArticleListsFragment) currentFragment).scroll(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
                        return true;
                    }
                    break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }
}
