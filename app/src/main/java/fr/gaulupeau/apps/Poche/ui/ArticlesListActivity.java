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
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
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
import fr.gaulupeau.apps.Poche.events.UpdateFeedsStartedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsFinishedEvent;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagServiceEndpoint;
import fr.gaulupeau.apps.Poche.network.tasks.TestFeedsTask;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.ConfigurationTestHelper;
import fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity;

import static fr.gaulupeau.apps.Poche.data.ListTypes.*;

public class ArticlesListActivity extends AppCompatActivity
        implements ArticlesListFragment.OnFragmentInteractionListener,
        SearchView.OnQueryTextListener, ViewPager.OnPageChangeListener {

    private static final String TAG = ArticlesListActivity.class.getSimpleName();

    private Settings settings;

    private ArticlesListPagerAdapter adapter;
    private ViewPager viewPager;

    private boolean isActive;

    private boolean[] invalidLists = new boolean[ArticlesListPagerAdapter.PAGES.length];

    private boolean checkConfigurationOnResume;
    private AlertDialog checkConfigurationDialog;
    private boolean firstSyncDone;
    private boolean tryToUpdateOnResume;

    private boolean offlineQueuePending;

    private boolean fullUpdateRunning;
    private int refreshingFragment = -1;

    private String searchString;

    private ConfigurationTestHelper configurationTestHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_articles_list);

        settings = new Settings(this);

        adapter = new ArticlesListPagerAdapter(getSupportFragmentManager());

        viewPager = (ViewPager) findViewById(R.id.articles_list_pager);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(this);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.articles_list_tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        viewPager.setCurrentItem(1);

        firstSyncDone = settings.isFirstSyncDone();

        offlineQueuePending = settings.isOfflineQueuePending();

        EventBus.getDefault().register(this);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_list, menu);

        SearchView searchView = (SearchView)menu.findItem(R.id.menuSearch).getActionView();
        if(searchView != null) {
            searchView.setSearchableInfo(((SearchManager)getSystemService(Context.SEARCH_SERVICE))
                    .getSearchableInfo(getComponentName()));

            searchView.setSubmitButtonEnabled(true);
            searchView.setOnQueryTextListener(this);
        }

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
                new LibsBuilder()
                        //provide a style (optional) (LIGHT, DARK, LIGHT_DARK_TOOLBAR)
                        .withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                        .withAboutIconShown(true)
                        .withAboutVersionShown(true)
                        .withAboutDescription(getResources().getString(R.string.aboutText))
                        .start(this);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override
    public void onPageScrollStateChanged(int state) {}

    @Override
    public void onPageSelected(int position) {
        Log.v(TAG, "onPageSelected() position: " + position);

        setSearchStringOnFragment(searchString);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.v(TAG, "onQueryTextSubmit() query: " + query);

        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        Log.v(TAG, "onQueryTextChange() newText: " + newText);

        setSearchString(newText);

        return true;
    }

    private void setSearchString(String searchString) {
        this.searchString = searchString;

        setSearchStringOnFragment(searchString);
    }

    private void setSearchStringOnFragment(String searchString) {
        ArticlesListFragment fragment = getCurrentFragment();
        if(fragment != null) fragment.setSearchString(searchString);
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
    public void onUpdateFeedsStartedEvent(UpdateFeedsStartedEvent event) {
        Log.d(TAG, "Got UpdateFeedsStartedEvent");

        notifyListUpdate(event.getFeedType(), true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUpdateFeedsStoppedEvent(UpdateFeedsFinishedEvent event) {
        Log.d(TAG, "Got UpdateFeedsFinishedEvent");

        if(event.getResult().isSuccess()) {
            firstSyncDone = true;
            tryToUpdateOnResume = false;
        }

        notifyListUpdate(event.getFeedType(), false);
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
    public boolean isFullUpdateRunning() {
        return fullUpdateRunning;
    }

    @Override
    public boolean isCurrentFeedUpdating() {
        return refreshingFragment != -1 && refreshingFragment == viewPager.getCurrentItem();
    }

    @Override
    public void updateFeed() {
        int position = viewPager.getCurrentItem();
        FeedUpdater.FeedType feedType = ArticlesListPagerAdapter.getFeedType(position);
        FeedUpdater.UpdateType updateType = feedType == FeedUpdater.FeedType.MAIN
                ? FeedUpdater.UpdateType.FAST : FeedUpdater.UpdateType.FULL;

        if(!updateFeed(true, feedType, updateType)) {
            setRefreshingUI(false);
        }
    }

    private void updateAllFeedsIfDbIsEmpty() {
        if(settings.isConfigurationOk() && !settings.isFirstSyncDone()) {
            updateAllFeeds(false);
        }
    }

    private void updateAllFeeds(boolean showErrors) {
        updateFeed(showErrors, null, null);
    }

    private void notifyListUpdate(FeedUpdater.FeedType feedType, boolean started) {
        int position = ArticlesListPagerAdapter.positionByFeedType(feedType);

        if(started) {
            if(position != -1) {
                if(refreshingFragment != -1 && refreshingFragment != position) {
                    setRefreshingUI(false, refreshingFragment); // should not happen
                }

                refreshingFragment = position;
                setRefreshingUI(true, position);
            } else {
                fullUpdateRunning = true;
                setRefreshingUI(true);
            }
        } else {
            if(refreshingFragment != -1) {
                setRefreshingUI(false, refreshingFragment);
                refreshingFragment = -1;
            }
            if(fullUpdateRunning) {
                fullUpdateRunning = false;
                setRefreshingUI(false);
            }
        }
    }

    private boolean updateFeed(boolean showErrors,
                               FeedUpdater.FeedType feedType,
                               FeedUpdater.UpdateType updateType) {
        boolean result = false;

        if(fullUpdateRunning || refreshingFragment != -1) {
            if(showErrors) {
                Toast.makeText(this, R.string.updateFeed_previousUpdateNotFinished,
                        Toast.LENGTH_SHORT).show();
            }
        } else if(!settings.isConfigurationOk()) {
            if(showErrors) {
                Toast.makeText(this, getString(R.string.txtConfigNotSet), Toast.LENGTH_SHORT).show();
            }
        } else if(WallabagConnection.isNetworkAvailable()) {
            ServiceHelper.updateFeed(this, feedType, updateType);

            result = true;
        } else {
            if(showErrors) {
                Toast.makeText(this, getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            }
        }

        return result;
    }

    private void invalidateLists(FeedsChangedEvent event) {
        if(event.isMainFeedChanged()) {
            invalidateList(ArticlesListPagerAdapter.positionByFeedType(FeedUpdater.FeedType.MAIN));
        }
        if(event.isFavoriteFeedChanged()) {
            invalidateList(ArticlesListPagerAdapter.positionByFeedType(FeedUpdater.FeedType.FAVORITE));
        }
        if(event.isArchiveFeedChanged()) {
            invalidateList(ArticlesListPagerAdapter.positionByFeedType(FeedUpdater.FeedType.ARCHIVE));
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
                f.updateList();
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
                f.updateList();
            } else {
                Log.w(TAG, "updateList() fragment is null");
            }
        } else {
            updateAllLists();
        }
    }

    private void setRefreshingUI(boolean refreshing) {
        ArticlesListFragment f = getCurrentFragment();
        if(f != null) {
            f.setRefreshingUI(refreshing);
        }
    }

    private void setRefreshingUI(boolean refreshing, int position) {
        ArticlesListFragment f = getFragment(position);
        if(f != null) {
            f.setRefreshingUI(refreshing);
        }
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
            public void onConfigurationTestSuccess(String url, Integer serverVersion) {
                updateAllFeedsIfDbIsEmpty();
            }

            @Override
            public void onConnectionTestFail(
                    WallabagServiceEndpoint.ConnectionTestResult result, String details) {}

            @Override
            public void onFeedsTestFail(TestFeedsTask.Result result, String details) {}
        }, null, settings, false, true);

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

        public ArticlesListPagerAdapter(FragmentManager fm) {
            super(fm);
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

        public static FeedUpdater.FeedType getFeedType(int position) {
            switch(PAGES[position]) {
                case LIST_TYPE_FAVORITES:
                    return FeedUpdater.FeedType.FAVORITE;
                case LIST_TYPE_ARCHIVED:
                    return FeedUpdater.FeedType.ARCHIVE;
                default:
                    return FeedUpdater.FeedType.MAIN;
            }
        }

        public static int positionByFeedType(FeedUpdater.FeedType feedType) {
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
