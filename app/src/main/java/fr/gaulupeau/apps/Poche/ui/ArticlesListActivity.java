package fr.gaulupeau.apps.Poche.ui;

import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesStartedEvent;
import fr.gaulupeau.apps.Poche.network.Updater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.ConfigurationTestHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity;

import static fr.gaulupeau.apps.Poche.data.ListTypes.*;

public class ArticlesListActivity extends BaseActionBarActivity
        implements ArticlesListFragment.OnFragmentInteractionListener,
        ViewPager.OnPageChangeListener {

    private static final String TAG = ArticlesListActivity.class.getSimpleName();

    private static final String SEARCH_QUERY_STATE = "search_query";

    private Settings settings;

    private ArticlesListPagerAdapter adapter;
    private ViewPager viewPager;

    private MenuItem searchMenuItem;

    private boolean isActive;

    private boolean[] invalidLists = new boolean[ArticlesListPagerAdapter.PAGES.length];

    private boolean checkConfigurationOnResume;
    private AlertDialog checkConfigurationDialog;
    private boolean firstSyncDone;
    private boolean tryToUpdateOnResume;

    private boolean offlineQueuePending;

    private boolean updateRunning;

    private ArticlesListFragment.SortOrder sortOrder;
    private String searchQuery;
    private boolean searchUIPending;

    private ConfigurationTestHelper configurationTestHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideBackButtonFromActionBar();
        setContentView(R.layout.activity_articles_list);

        Log.v(TAG, "onCreate()");

        handleIntent(getIntent());

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        settings = new Settings(this);

        adapter = new ArticlesListPagerAdapter(
                getSupportFragmentManager(), savedInstanceState != null);

        viewPager = (ViewPager) findViewById(R.id.articles_list_pager);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(this);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.articles_list_tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        firstSyncDone = settings.isFirstSyncDone();

        offlineQueuePending = settings.isOfflineQueuePending();

        sortOrder = settings.getListSortOrder();

        if(savedInstanceState != null) {
            Log.v(TAG, "onCreate() restoring state");

            performSearch(savedInstanceState.getString(SEARCH_QUERY_STATE));
        }

        EventBus.getDefault().register(this);

        viewPager.setCurrentItem(1);
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
    protected void onStart() {
        super.onStart();

        checkConfigurationOnResume = true;

        if(!firstSyncDone) {
            tryToUpdateOnResume = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        isActive = true;

        checkLists();

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
    protected void onPause() {
        isActive = false;

        super.onPause();
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.v(TAG, "onSaveInstanceState()");

        outState.putString(SEARCH_QUERY_STATE, searchQuery);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_list, menu);

        searchMenuItem = menu.findItem(R.id.menuSearch);
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
            MenuItem menuItem = menu.findItem(R.id.menuSyncQueue);
            if(menuItem != null) {
                menuItem.setVisible(false);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuFullUpdate:
                updateAllFeeds(true);
                return true;
            case R.id.menuSyncQueue:
                syncQueue();
                return true;
            case R.id.menuChangeSortOrder:
                switchSortOrder();
                return true;
            case R.id.menuSettings:
                startActivity(new Intent(getBaseContext(), SettingsActivity.class));
                return true;
            case R.id.menuBagPage:
                startActivity(new Intent(getBaseContext(), AddActivity.class));
                return true;
            case R.id.menuOpenRandomArticle:
                openRandomArticle();
                return true;
            case R.id.menuAbout:
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
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if(adapter == null) {
            // happens if activity recreated (e.g., on orientation change)
            Log.v(TAG, "onAttachFragment() activity is not initialized yet; ignoring");
            return;
        }

        if(fragment instanceof ArticlesListFragment) {
            ArticlesListFragment articlesListFragment = (ArticlesListFragment)fragment;
            Log.v(TAG, "onAttachFragment() listType: " + articlesListFragment.getListType());

            setParametersToFragment(articlesListFragment);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override
    public void onPageScrollStateChanged(int state) {}

    @Override
    public void onPageSelected(int position) {
        Log.v(TAG, "onPageSelected() position: " + position);

        setParametersToFragment(getCurrentFragment());
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

    private void setParametersToFragment(ArticlesListFragment fragment) {
        Log.v(TAG, "setParametersToFragment() started");
        if(fragment == null) return;

        Log.v(TAG, "setParametersToFragment() listType: " + fragment.getListType());

        setSortOrder(fragment, sortOrder);
        setSearchQueryOnFragment(fragment, searchQuery);
        setRefreshingUI(fragment, updateRunning);
    }

    private void switchSortOrder() {
        sortOrder = sortOrder == ArticlesListFragment.SortOrder.DESC
                ? ArticlesListFragment.SortOrder.ASC
                : ArticlesListFragment.SortOrder.DESC;

        settings.setListSortOrder(sortOrder);

        setSortOrder(getCurrentFragment(), sortOrder);
    }

    private void setSortOrder(ArticlesListFragment fragment,
                              ArticlesListFragment.SortOrder sortOrder) {
        if(fragment != null) fragment.setSortOrder(sortOrder);
    }

    private void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;

        setSearchQueryOnFragment(getCurrentFragment(), searchQuery);
    }

    private void setSearchQueryOnFragment(ArticlesListFragment fragment, String searchQuery) {
        if(fragment != null) fragment.setSearchQuery(searchQuery);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedsChangedEvent(FeedsChangedEvent event) {
        Log.d(TAG, "Got FeedsChangedEvent");

        if(event.isInvalidateAll()) {
            firstSyncDone = settings.isFirstSyncDone();
        }

        invalidateLists(event);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateFeedsStartedEvent(UpdateArticlesStartedEvent event) {
        Log.d(TAG, "Got UpdateArticlesStartedEvent");

        updateStateChanged(true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateFeedsStoppedEvent(UpdateArticlesFinishedEvent event) {
        Log.d(TAG, "Got UpdateArticlesFinishedEvent");

        if(event.getResult().isSuccess()) {
            firstSyncDone = true;
            tryToUpdateOnResume = false;
        }

        updateStateChanged(false);
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

    @Override
    public void updateFeed() {
        if(!updateRunning && !updateFeed(true, Updater.UpdateType.FAST)) {
            // cancels the refresh animation if the update was not started
            setRefreshingUI(false);
        }
    }

    private void updateAllFeedsIfDbIsEmpty() {
        if(settings.isConfigurationOk() && !settings.isFirstSyncDone()) {
            updateAllFeeds(false);
        }
    }

    private void updateAllFeeds(boolean showErrors) {
        updateFeed(showErrors, Updater.UpdateType.FULL);
    }

    private void updateStateChanged(boolean started) {
        if(started == updateRunning) return;

        updateRunning = started;

        setRefreshingUI(started);
    }

    private boolean updateFeed(boolean showErrors, Updater.UpdateType updateType) {
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

    private void invalidateLists(FeedsChangedEvent event) {
        if(event.isInvalidateAll()) {
            invalidateList(-1);
            return;
        }

        // TODO: fix: unused
        if(event.isMainFeedChanged()) {
            invalidateList(ArticlesListPagerAdapter
                    .positionByFeedType(FeedsChangedEvent.FeedType.MAIN));
        }
        if(event.isFavoriteFeedChanged()) {
            invalidateList(ArticlesListPagerAdapter
                    .positionByFeedType(FeedsChangedEvent.FeedType.FAVORITE));
        }
        if(event.isArchiveFeedChanged()) {
            invalidateList(ArticlesListPagerAdapter
                    .positionByFeedType(FeedsChangedEvent.FeedType.ARCHIVE));
        }
    }

    private void invalidateList(int position) {
        Log.d(TAG, "invalidateList() started with position: " + position);

        if(position != -1) {
            invalidLists[position] = true;
        } else {
            for(int i = 0; i < invalidLists.length; i++) {
                invalidLists[i] = true;
            }
        }

        if(isActive) {
            Log.d(TAG, "invalidateList() activity is active; calling checkLists()");
            checkLists();
        }

        Log.d(TAG, "invalidateList() finished");
    }

    private void checkLists() {
        Log.d(TAG, "checkLists() started");

        for(int i = 0; i < invalidLists.length; i++) {
            if(invalidLists[i]) {
                invalidLists[i] = false;

                Log.d(TAG, "checkLists() updating list with position: " + i);
                updateList(i);
            }
        }

        Log.d(TAG, "checkLists() finished");
    }

    private void updateAllLists() {
        Log.d(TAG, "updateAllLists() started");

        for(int i = 0; i < ArticlesListPagerAdapter.PAGES.length; i++) {
            ArticlesListFragment f = getFragment(i);
            if(f != null) {
                f.invalidateList();
            } else {
                Log.w(TAG, "updateAllLists() fragment is null; position: " + i);
            }
        }
    }

    private void updateList(int position) {
        Log.d(TAG, "updateList() position: " + position);

        if(position != -1) {
            ArticlesListFragment f = getFragment(position);
            if(f != null) {
                f.invalidateList();
            } else {
                Log.w(TAG, "updateList() fragment is null");
            }
        } else {
            updateAllLists();
        }
    }

    private void setRefreshingUI(boolean refreshing) {
        setRefreshingUI(getCurrentFragment(), refreshing);
    }

    private void setRefreshingUI(ArticlesListFragment fragment, boolean refreshing) {
        if(fragment != null) fragment.setRefreshingUI(refreshing);
    }

    private void openRandomArticle() {
        ArticlesListFragment f = getCurrentFragment();
        if(f != null) {
            f.openRandomArticle();
        }
    }

    private ArticlesListFragment getCurrentFragment() {
        return adapter == null || viewPager == null ? null
                : adapter.getCachedFragment(viewPager.getCurrentItem());
    }

    private ArticlesListFragment getFragment(int position) {
        return adapter != null ? adapter.getCachedFragment(position) : null;
    }

    private void syncQueue() {
        if(!WallabagConnection.isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            return;
        }

        ServiceHelper.syncQueue(this);
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

    public static class ArticlesListPagerAdapter extends FragmentPagerAdapter {

        private static final String TAG = "ArtListPagerAdapter";

        private static int[] PAGES = {
                LIST_TYPE_FAVORITES,
                LIST_TYPE_UNREAD,
                LIST_TYPE_ARCHIVED
        };

        private ArticlesListFragment[] fragments = new ArticlesListFragment[PAGES.length];

        public ArticlesListPagerAdapter(FragmentManager fm, boolean tryToRestoreFragments) {
            super(fm);

            if(tryToRestoreFragments) {
                Log.d(TAG, "<init>() trying to restore fragments");

                for(Fragment f: fm.getFragments()) {
                    if(f instanceof ArticlesListFragment) {
                        ArticlesListFragment articlesListFragment = (ArticlesListFragment)f;
                        Log.d(TAG, "<init>() found fragment of type: "
                                + articlesListFragment.getListType());

                        for(int i = 0; i < PAGES.length; i++) {
                            if(articlesListFragment.getListType() == PAGES[i]) {
                                fragments[i] = articlesListFragment;

                                Log.d(TAG, "<init>() restored fragment at position: " + i);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public Fragment getItem(int position) {
            Log.d(TAG, "getItem " + position);
            return getFragment(position);
        }

        @Override
        public int getCount() {
            return PAGES.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch(PAGES[position]) {
                case LIST_TYPE_FAVORITES:
                    return App.getInstance().getString(R.string.feedName_favorites);
                case LIST_TYPE_ARCHIVED:
                    return App.getInstance().getString(R.string.feedName_archived);
                default:
                    return App.getInstance().getString(R.string.feedName_unread);
            }
        }

        public static int positionByFeedType(FeedsChangedEvent.FeedType feedType) {
            if(feedType == null) return -1;

            int listType;
            switch(feedType) {
                case FAVORITE:
                    listType = LIST_TYPE_FAVORITES;
                    break;
                case ARCHIVE:
                    listType = LIST_TYPE_ARCHIVED;
                    break;
                default:
                    listType = LIST_TYPE_UNREAD;
                    break;
            }

            for(int i = 0; i < PAGES.length; i++) {
                if(listType == PAGES[i]) return i;
            }

            return -1;
        }

        public ArticlesListFragment getCachedFragment(int position) {
            return fragments[position];
        }

        private ArticlesListFragment getFragment(int position) {
            Log.d(TAG, "getFragment " + position);

            ArticlesListFragment f = fragments[position];
            if(f == null) {
                Log.d(TAG, "creating new instance");
                fragments[position] = f = ArticlesListFragment.newInstance(PAGES[position]);
            }

            return f;
        }

    }

}
