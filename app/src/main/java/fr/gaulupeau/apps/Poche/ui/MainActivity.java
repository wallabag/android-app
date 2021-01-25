package fr.gaulupeau.apps.Poche.ui;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesProgressEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesStartedEvent;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.service.workers.ArticleUpdater;
import fr.gaulupeau.apps.Poche.ui.preferences.ConfigurationTestHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        TagListFragment.OnFragmentInteractionListener {

    static final String PARAM_TAG_LABEL = "tag_label";

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

    private LinearProgressIndicator progressBar;

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

    private final Map<String, Fragment.SavedState> savedFragmentStates = new HashMap<>();

    private String currentFragmentType;
    private Fragment currentFragment;

    private Sortable.SortOrder sortOrder;
    private Sortable.SortOrder tagsSortOrder;
    private String searchQuery;
    private String searchQueryPrevious;
    private boolean searchUIPending;
    private String selectedTag;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        Themes.applyTheme(this, false);
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.activity_main);

        this.setDefaultKeyMode(AppCompatActivity.DEFAULT_KEYS_SEARCH_LOCAL);

        this.settings = App.getSettings();

        final Toolbar toolbar = this.findViewById(R.id.toolbar);
        this.setSupportActionBar(toolbar);

        this.navigationView = this.findViewById(R.id.nav_view);
        this.navigationView.setNavigationItemSelectedListener(this);

        if (this.navigationView != null) {
            final View headerView = this.navigationView.getHeaderView(0);
            if (headerView != null) {
                this.lastUpdateTimeView = headerView.findViewById(R.id.lastUpdateTime);
            }

            // Set different colors for items in the navigation bar in dark (high contrast) theme
            if (Themes.getCurrentTheme() == Themes.Theme.DARK_CONTRAST) {
                @SuppressLint("ResourceType") final XmlResourceParser parser = this.getResources().getXml(R.color.dark_contrast_menu_item);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        this.navigationView.setItemTextColor(ColorStateList.createFromXml(this.getResources(), parser, this.getTheme()));
                        this.navigationView.setItemIconTintList(ColorStateList.createFromXml(this.getResources(), parser, this.getTheme()));
                    } else {
                        this.navigationView.setItemTextColor(ColorStateList.createFromXml(this.getResources(), parser));
                        this.navigationView.setItemIconTintList(ColorStateList.createFromXml(this.getResources(), parser));
                    }
                } catch (final XmlPullParserException | IOException e) {
                    Log.e(TAG, "onCreate()", e);
                }
            }
        }

        final DrawerLayout drawer = this.findViewById(R.id.drawer_layout);
        final ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            boolean updated;

            @Override
            public void onDrawerSlide(final View drawerView, final float slideOffset) {
                this.updateTime();
            }

            @Override
            public void onDrawerStateChanged(final int newState) {
                if (newState == DrawerLayout.STATE_IDLE) {
                    this.updated = false;
                }
            }

            private void updateTime() {
                if (this.updated) {
                    return;
                }
                this.updated = true;

                if (MainActivity.this.lastUpdateTimeView == null) {
                    return;
                }

                Log.d(MainActivity.TAG, "DrawerListener.updateTime() updating time");

                MainActivity.this.updateLastUpdateTime();
            }
        });

        this.progressBar = this.findViewById(R.id.progressBar);

        this.firstSyncDone = this.settings.isFirstSyncDone();

        this.offlineQueuePending = this.settings.isOfflineQueuePending();

        this.sortOrder = this.settings.getListSortOrder();
        this.tagsSortOrder = this.settings.getTagListSortOrder();

        String currentFragmentType = null;

        if (savedInstanceState == null) {
            final Intent intent = this.getIntent();

            this.selectedTag = intent.getStringExtra(PARAM_TAG_LABEL);

            if (!TextUtils.isEmpty(this.selectedTag)) {
                currentFragmentType = FRAGMENT_TAGGED_ARTICLE_LISTS;
            }

            if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                this.searchQuery = intent.getStringExtra(SearchManager.QUERY);
            }
        } else {
            Log.v(TAG, "onCreate() restoring state");

            final Bundle bundle = savedInstanceState.getBundle(STATE_SAVED_FRAGMENT_STATES);
            if (bundle != null) {
                for (final String key : bundle.keySet()) {
                    //noinspection ConstantConditions
                    this.savedFragmentStates.put(key, bundle.getParcelable(key));
                }
            }

            currentFragmentType = savedInstanceState.getString(STATE_CURRENT_FRAGMENT);

            this.selectedTag = savedInstanceState.getString(STATE_SELECTED_TAG);

            this.searchQuery = savedInstanceState.getString(STATE_SEARCH_QUERY);
        }

        if (this.searchQuery == null) {
            this.searchQuery = "";
        }
        this.performSearch(this.searchQuery);

        if (currentFragmentType == null) {
            currentFragmentType = FRAGMENT_ARTICLE_LISTS;
        }

        if (savedInstanceState == null) {
            this.setCurrentFragment(currentFragmentType);
        } else {
            this.currentFragment = this.getSupportFragmentManager().findFragmentByTag(currentFragmentType);
            this.currentFragmentType = currentFragmentType;
            this.updateNavigationUI(currentFragmentType);
        }

        EventHelper.register(this);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent()");

        final String tag = intent.getStringExtra(PARAM_TAG_LABEL);
        if (!TextUtils.isEmpty(tag)) {
            this.openTag(tag);
        }

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            final String query = intent.getStringExtra(SearchManager.QUERY);
            Log.v(TAG, "onNewIntent() search intent; query: " + query);

            this.performSearch(query);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.v(TAG, "onSaveInstanceState()");

        if (!this.savedFragmentStates.isEmpty()) {
            final Bundle bundle = new Bundle(this.savedFragmentStates.size());

            for (final Map.Entry<String, Fragment.SavedState> e : this.savedFragmentStates.entrySet()) {
                bundle.putParcelable(e.getKey(), e.getValue());
            }

            outState.putBundle(STATE_SAVED_FRAGMENT_STATES, bundle);
        }

        outState.putString(STATE_CURRENT_FRAGMENT, this.currentFragmentType);
        outState.putString(STATE_SELECTED_TAG, this.selectedTag);
        outState.putString(STATE_SEARCH_QUERY, this.searchQuery);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Themes.checkTheme(this);

        this.checkConfigurationOnResume = true;

        this.tryToUpdateOnResume = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO: check logic
        if (this.checkConfigurationOnResume) {
            this.checkConfigurationOnResume = false;

            if (!Settings.checkFirstRunInit(this)) {
                if (!this.settings.isConfigurationOk() && this.checkConfigurationDialog == null) {
                    final AlertDialog.Builder messageBox = new AlertDialog.Builder(this);
                    messageBox.setTitle(this.settings.isConfigurationErrorShown()
                            ? R.string.d_configurationIsQuestionable_title
                            : R.string.d_configurationChanged_title);
                    messageBox.setMessage(this.settings.isConfigurationErrorShown()
                            ? R.string.d_configurationIsQuestionable_message
                            : R.string.d_configurationChanged_message);
                    messageBox.setPositiveButton(R.string.ok, (dialog, which) -> this.testConfiguration());
                    messageBox.setNegativeButton(R.string.d_configurationChanged_answer_decline, null);
                    messageBox.setOnDismissListener(dialog -> this.checkConfigurationDialog = null);
                    this.checkConfigurationDialog = messageBox.show();
                }
            }
        }

        if (this.tryToUpdateOnResume) {
            this.tryToUpdateOnResume = false;

            if (!this.firstSyncDone) {
                this.updateAllFeedsIfDbIsEmpty();
            } else {
                this.updateOnStartup();
            }
        }
    }

    @Override
    protected void onStop() {
        this.cancelConfigurationTest();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        EventHelper.unregister(this);

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        final DrawerLayout drawer = this.findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu()");

        if (this.searchMenuItemExpanded) {
            // options menu invalidation happened when searchMenuItem was expanded
            this.searchMenuItemExpanded = false;
            this.searchMenuItem = null;

            Log.i(TAG, "onCreateOptionsMenu() searchMenuItem was not collapsed!");
            Log.v(TAG, "onCreateOptionsMenu() searchQuery: " + this.searchQuery
                    + ", searchQueryPrevious: " + this.searchQueryPrevious);

            this.performSearch(this.searchQueryPrevious);
        }

        this.getMenuInflater().inflate(R.menu.main, menu);

        this.searchMenuItem = menu.findItem(R.id.menu_main_search);
        this.searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(final MenuItem item) {
                Log.v(MainActivity.TAG, "searchMenuItem expanded");
                MainActivity.this.searchMenuItemExpanded = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(final MenuItem item) {
                Log.v(MainActivity.TAG, "searchMenuItem collapsed");
                MainActivity.this.supportInvalidateOptionsMenu();
                MainActivity.this.searchMenuItemExpanded = false;
                return true;
            }
        });

        final SearchView searchView = (SearchView) this.searchMenuItem.getActionView();
        if (searchView != null) {
            searchView.setSearchableInfo(((SearchManager) this.getSystemService(Context.SEARCH_SERVICE))
                    .getSearchableInfo(this.getComponentName()));

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(final String query) {
                    Log.v(MainActivity.TAG, "onQueryTextSubmit() query: " + query);

                    return true;
                }

                @Override
                public boolean onQueryTextChange(final String newText) {
                    Log.v(MainActivity.TAG, "onQueryTextChange() newText: " + newText);

                    MainActivity.this.setSearchQuery(newText);

                    return true;
                }
            });
        }
        this.checkPendingSearchUI();

        if (!this.offlineQueuePending) {
            final MenuItem menuItem = menu.findItem(R.id.menu_main_syncQueue);
            if (menuItem != null) {
                menuItem.setVisible(false);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_main_changeSortOrder:
                this.switchSortOrder();
                return true;

            case R.id.menu_main_syncQueue:
                this.syncQueue();
                return true;

            case R.id.menu_main_sweepDeletedArticles:
                this.sweepDeletedArticles();
                return true;

            case R.id.menu_main_fullUpdate:
                this.fullUpdate(true);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_mainLists:
                this.setCurrentFragment(FRAGMENT_ARTICLE_LISTS);
                break;

            case R.id.nav_tags:
                this.setCurrentFragment(FRAGMENT_TAG_LIST);
                break;

            case R.id.nav_add:
                this.showAddBagDialog();
                break;

            case R.id.nav_settings:
                this.startActivity(new Intent(this.getBaseContext(), SettingsActivity.class));
                break;

            case R.id.nav_about:
                final Libs.ActivityStyle style;
                switch (Themes.getCurrentTheme()) {
                    case DARK:
                    case DARK_CONTRAST:
                        style = Libs.ActivityStyle.DARK;
                        break;

                    default:
                        style = Libs.ActivityStyle.LIGHT_DARK_TOOLBAR;
                        break;
                }
                final CharSequence aboutCharSequence = this.getText(R.string.aboutText);
                final String aboutString = aboutCharSequence instanceof Spanned
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

        this.<DrawerLayout>findViewById(R.id.drawer_layout).closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        return this.handleContextMenuItemInFragment(item) || super.onContextItemSelected(item);
    }

    @Subscribe(threadMode = ThreadMode.MAIN, priority = -1)
    public void onOfflineQueueChangedEvent(final OfflineQueueChangedEvent event) {
        Log.d(TAG, "onOfflineQueueChangedEvent()");

        final Long queueLength = event.getQueueLength();

        final boolean prevValue = this.offlineQueuePending;
        this.offlineQueuePending = queueLength == null || queueLength > 0;

        Log.d(TAG, "onOfflineQueueChangedEvent() offlineQueuePending: " + this.offlineQueuePending);

        if (prevValue != this.offlineQueuePending) {
            Log.d(TAG, "onOfflineQueueChangedEvent() invalidating options menu");
            this.invalidateOptionsMenu();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedsChangedEvent(final FeedsChangedEvent event) {
        Log.d(TAG, "onFeedsChangedEvent()");

        if (event.isInvalidateAll()) {
            this.firstSyncDone = this.settings.isFirstSyncDone();
        }

        if (this.currentFragment instanceof ArticleListsFragment) {
            ((ArticleListsFragment) this.currentFragment).onFeedsChangedEvent(event);
        } else if (this.currentFragment instanceof RecyclerViewListFragment) {
            ((RecyclerViewListFragment) this.currentFragment).invalidateList();
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateArticlesStartedEvent(final UpdateArticlesStartedEvent event) {
        Log.d(TAG, "onUpdateArticlesStartedEvent()");

        this.updateStateChanged(true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateArticlesProgressEvent(final UpdateArticlesProgressEvent event) {
        Log.d(TAG, "onUpdateArticlesProgressEvent()");

        if (this.progressBar != null) {
            this.progressBar.setProgressCompat(event.getCurrent() / event.getTotal(), true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateArticlesFinishedEvent(final UpdateArticlesFinishedEvent event) {
        Log.d(TAG, "onUpdateArticlesFinishedEvent");

        if (event.getResult().isSuccess()) {
            this.firstSyncDone = true;
            this.tryToUpdateOnResume = false;
        }

        this.updateLastUpdateTime();

        this.updateStateChanged(false);
    }

    private void updateLastUpdateTime() {
        if (this.lastUpdateTimeView == null) {
            return;
        }

        Log.d(TAG, "updateLastUpdateTime() updating time");

        final long timestamp = this.settings.getLatestUpdateRunTimestamp();
        if (timestamp != 0) {
            this.lastUpdateTimeView.setText(this.getString(R.string.lastUpdateTimeLabel,
                    DateUtils.getRelativeTimeSpanString(timestamp)));
        } else {
            this.lastUpdateTimeView.setVisibility(View.INVISIBLE);
        }
    }

    private void updateStateChanged(final boolean started) {
        if (started == this.updateRunning) {
            return;
        }

        this.updateRunning = started;

        if (this.progressBar != null) {
            if (started) {
                this.progressBar.show();
            } else {
                this.progressBar.hide();
            }
        }
    }

    private void performSearch(final String query) {
        this.setSearchQuery(query);

        if (TextUtils.isEmpty(query)) {
            return;
        }

        this.searchUIPending = true;
        this.checkPendingSearchUI();
    }

    private void checkPendingSearchUI() {
        if (this.searchMenuItem == null) {
            return;
        }
        if (!this.searchUIPending) {
            return;
        }

        this.searchUIPending = false;

        this.initSearchUI();
    }

    private void initSearchUI() {
        final SearchView searchView = (SearchView) this.searchMenuItem.getActionView();
        if (searchView == null) {
            return;
        }

        final String searchQueryToRestore = this.searchQuery;

        this.searchMenuItem.expandActionView();

        searchView.post(() -> {
            Log.v(TAG, "searchView.post() restoring search string: " + searchQueryToRestore);
            searchView.setQuery(searchQueryToRestore, false);
        });
    }

    private void setParametersToFragment(final Fragment fragment) {
        Log.v(TAG, "setParametersToFragment() started");
        if (fragment == null) {
            return;
        }

        this.setSortOrder(fragment);
        MainActivity.setSearchQueryOnFragment(fragment, this.searchQuery);
    }

    private void switchSortOrder() {
        if (FRAGMENT_TAG_LIST.equals(this.currentFragmentType)) {
            this.tagsSortOrder = this.tagsSortOrder == Sortable.SortOrder.DESC
                    ? Sortable.SortOrder.ASC
                    : Sortable.SortOrder.DESC;

            this.settings.setTagListSortOrder(this.tagsSortOrder);
        } else {
            this.sortOrder = this.sortOrder == Sortable.SortOrder.DESC
                    ? Sortable.SortOrder.ASC
                    : Sortable.SortOrder.DESC;

            this.settings.setListSortOrder(this.sortOrder);
        }

        this.setSortOrder(this.currentFragment);
    }

    private void setSortOrder(final Fragment fragment) {
        MainActivity.setSortOrder(fragment, FRAGMENT_TAG_LIST.equals(this.currentFragmentType)
                ? this.tagsSortOrder : this.sortOrder);
    }

    private static void setSortOrder(final Fragment fragment, final Sortable.SortOrder sortOrder) {
        if (fragment instanceof Sortable) {
            ((Sortable) fragment).setSortOrder(sortOrder);
        }
    }

    private void setSearchQuery(final String searchQuery) {
        this.searchQueryPrevious = this.searchQuery;
        this.searchQuery = searchQuery;

        MainActivity.setSearchQueryOnFragment(this.currentFragment, searchQuery);
    }

    private static void setSearchQueryOnFragment(final Fragment fragment, final String searchQuery) {
        if (fragment instanceof Searchable) {
            ((Searchable) fragment).setSearchQuery(searchQuery);
        }
    }

    private boolean handleContextMenuItemInFragment(final MenuItem item) {
        return this.currentFragment instanceof ContextMenuItemHandler
                && ((ContextMenuItemHandler) this.currentFragment).handleContextItemSelected(this, item);
    }

    private void setCurrentFragment(final String type) {
        this.setCurrentFragment(type, false);
    }

    private void setCurrentFragment(final String type, final boolean force) {
        Log.d(TAG, String.format("setCurrentFragment(%s, %s)", type, force));

        if (!force && TextUtils.equals(this.currentFragmentType, type)) {
            Log.i(TAG, "setCurrentFragment() ignoring switch to the same type: " + type);
            return;
        }

        this.setCurrentFragment(this.getFragment(type), type);
    }

    private void setCurrentFragment(final Fragment fragment, final String type) {
        this.updateNavigationUI(type);

        if (this.currentFragment != null && MainActivity.isFragmentStateSavable(this.currentFragmentType)) {
            Log.d(TAG, "setCurrentFragment() saving fragment state: " + this.currentFragmentType);

            //noinspection ConstantConditions
            this.savedFragmentStates.put(this.currentFragmentType, this.getSupportFragmentManager()
                    .saveFragmentInstanceState(this.currentFragment));
        }

        this.getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_content_frame, fragment, type)
                .commit();

        this.currentFragment = fragment;
        this.currentFragmentType = type;

        this.setParametersToFragment(fragment);
    }

    private Fragment getFragment(final String type) {
        Log.d(TAG, "getFragment() type: " + type);

        Fragment fragment = this.getSupportFragmentManager().findFragmentByTag(type);

        if (fragment == null || !MainActivity.isFragmentReusable(type)) {
            Log.d(TAG, "getFragment() creating new instance");

            switch (type) {
                case FRAGMENT_ARTICLE_LISTS:
                    fragment = ArticleListsFragment.newInstance(null);
                    break;

                case FRAGMENT_TAGGED_ARTICLE_LISTS:
                    fragment = ArticleListsFragment.newInstance(this.selectedTag);
                    break;

                case FRAGMENT_TAG_LIST:
                    fragment = new TagListFragment();
                    break;

                default:
                    throw new IllegalArgumentException("Fragment type is not supported: " + type);
            }

            if (MainActivity.isFragmentStateSavable(type)) {
                Log.d(TAG, "getFragment() fragment is savable");

                final Fragment.SavedState savedState = this.savedFragmentStates.get(type);
                if (savedState != null) {
                    Log.d(TAG, "getFragment() restoring fragment state");

                    fragment.setInitialSavedState(savedState);
                }
            }
        }

        return fragment;
    }

    private static boolean isFragmentReusable(final String type) {
        return !type.equals(FRAGMENT_TAGGED_ARTICLE_LISTS);
    }

    private static boolean isFragmentStateSavable(final String type) {
        if (type == null) {
            return false;
        }

        switch (type) {
            case FRAGMENT_ARTICLE_LISTS:
            case FRAGMENT_TAG_LIST:
                return true;
        }

        return false;
    }

    private void updateNavigationUI(final String type) {
        if (type == null || this.navigationView == null) {
            return;
        }

        if (FRAGMENT_TAGGED_ARTICLE_LISTS.equals(this.currentFragmentType)) {
            final MenuItem item = this.navigationView.getMenu().findItem(R.id.nav_taggedLists);
            if (item != null) {
                item.setVisible(false);
                item.setEnabled(false);
            }
        }

        CharSequence title = null;
        @IdRes int itemID = 0;
        switch (type) {
            case FRAGMENT_ARTICLE_LISTS:
                itemID = R.id.nav_mainLists;
                break;

            case FRAGMENT_TAG_LIST:
                itemID = R.id.nav_tags;
                break;

            case FRAGMENT_TAGGED_ARTICLE_LISTS:
                itemID = R.id.nav_taggedLists;

                if (this.selectedTag != null) {
                    title = this.getString(R.string.title_main_tag, this.selectedTag);
                }

                final MenuItem item = this.navigationView.getMenu().findItem(itemID);
                if (item != null) {
                    if (title != null) {
                        item.setTitle(title);
                    }
                    item.setVisible(true);
                    item.setEnabled(true);
                }
                break;
        }

        if (itemID != 0) {
            this.navigationView.setCheckedItem(itemID);

            if (title == null) {
                final MenuItem item = this.navigationView.getMenu().findItem(itemID);
                if (item != null) {
                    title = item.getTitle();
                }
            }
        }
        if (title != null) {
            this.setTitle(title);
        }
    }

    @Override
    public void onTagSelected(final Tag tag) {
        this.openTag(tag.getLabel());
    }

    private void openTag(final String tagLabel) {
        this.selectedTag = tagLabel;

        this.setCurrentFragment(FRAGMENT_TAGGED_ARTICLE_LISTS, true);
    }

    @Override
    public void onRecyclerViewListSwipeUpdate() {
        this.updateArticles(true, ArticleUpdater.UpdateType.FAST);
    }

    private void syncQueue() {
        if (!WallabagConnection.isNetworkAvailable()) {
            Toast.makeText(this, this.getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            return;
        }

        OperationsHelper.syncQueue(this);
    }

    private void sweepDeletedArticles() {
        if (!WallabagConnection.isNetworkAvailable()) {
            Toast.makeText(this, this.getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            return;
        }

        OperationsHelper.sweepDeletedArticles(this);
    }

    private void updateAllFeedsIfDbIsEmpty() {
        if (this.settings.isConfigurationOk() && !this.settings.isFirstSyncDone()) {
            this.fullUpdate(false);
        }
    }

    private void updateOnStartup() {
        final long delay = TimeUnit.MINUTES.toMillis(5);
        if (this.settings.isAutoSyncOnStartupEnabled() && this.settings.isConfigurationOk()
                && this.settings.isFirstSyncDone()
                && this.settings.getLatestUpdateRunTimestamp() + delay < System.currentTimeMillis()) {
            this.updateArticles(false, ArticleUpdater.UpdateType.FAST);
        }
    }

    private void fullUpdate(final boolean showErrors) {
        this.updateArticles(showErrors, ArticleUpdater.UpdateType.FULL);
    }

    private void updateArticles(final boolean showErrors, final ArticleUpdater.UpdateType updateType) {
        if (this.updateRunning) {
            if (showErrors) {
                Toast.makeText(this, R.string.previousUpdateNotFinished, Toast.LENGTH_SHORT).show();
            }
        } else if (!this.settings.isConfigurationOk()) {
            if (showErrors) {
                Toast.makeText(this, this.getString(R.string.txtConfigNotSet), Toast.LENGTH_SHORT).show();
            }
        } else if (WallabagConnection.isNetworkAvailable()) {
            OperationsHelper.syncAndUpdate(this, this.settings, updateType, false);
        } else {
            if (showErrors) {
                Toast.makeText(this, this.getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void testConfiguration() {
        this.cancelConfigurationTest();

        this.configurationTestHelper = new ConfigurationTestHelper(
                this, new ConfigurationTestHelper.ResultHandler() {
            @Override
            public void onConfigurationTestSuccess(final String url) {
                MainActivity.this.updateAllFeedsIfDbIsEmpty();
            }

            @Override
            public void onConnectionTestFail(
                    final WallabagWebService.ConnectionTestResult result, final String details) {
            }

            @Override
            public void onApiAccessTestFail(final TestApiAccessTask.Result result, final String details) {
            }
        }, null, this.settings, true);

        this.configurationTestHelper.test();
    }

    private void cancelConfigurationTest() {
        if (this.configurationTestHelper != null) {
            this.configurationTestHelper.cancel();
            this.configurationTestHelper = null;
        }
    }

    private void showAddBagDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_add_label);

        @SuppressLint("InflateParams") final View view = this.getLayoutInflater().inflate(R.layout.dialog_add, null);

        builder.setView(view);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            final TextView textView = view.findViewById(R.id.page_url);
            OperationsHelper.addArticleWithUI(this, textView.getText().toString(), null);
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        builder.show();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (this.currentFragment instanceof ArticleListsFragment) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    ((ArticleListsFragment) this.currentFragment).scroll(keyCode == KeyEvent.KEYCODE_PAGE_UP);
                    return true;

                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (this.settings.isVolumeButtonsScrollingEnabled()) {
                        ((ArticleListsFragment) this.currentFragment).scroll(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
                        return true;
                    }
                    break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }
}
