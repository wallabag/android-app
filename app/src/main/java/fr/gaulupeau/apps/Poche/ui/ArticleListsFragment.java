package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.EnumSet;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;

public class ArticleListsFragment extends Fragment implements Sortable, Searchable {

    private static final String TAG = ArticleListsFragment.class.getSimpleName();

    private static final String PARAM_TAG = "tag";

    private static final String STATE_SORT_ORDER = "sort_order";
    private static final String STATE_SEARCH_QUERY = "search_query";

    private static final EnumSet<ArticlesChangedEvent.ChangeType> CHANGE_SET = EnumSet.of(
            ArticlesChangedEvent.ChangeType.UNSPECIFIED,
            ArticlesChangedEvent.ChangeType.ADDED,
            ArticlesChangedEvent.ChangeType.DELETED,
            ArticlesChangedEvent.ChangeType.FAVORITED,
            ArticlesChangedEvent.ChangeType.UNFAVORITED,
            ArticlesChangedEvent.ChangeType.ARCHIVED,
            ArticlesChangedEvent.ChangeType.UNARCHIVED,
            ArticlesChangedEvent.ChangeType.CREATED_DATE_CHANGED,
            ArticlesChangedEvent.ChangeType.TITLE_CHANGED,
            ArticlesChangedEvent.ChangeType.DOMAIN_CHANGED,
            ArticlesChangedEvent.ChangeType.ESTIMATED_READING_TIME_CHANGED);

    private static final EnumSet<ArticlesChangedEvent.ChangeType> CHANGE_SET_FORCE_CONTENT_UPDATE
            = EnumSet.of(ArticlesChangedEvent.ChangeType.ESTIMATED_READING_TIME_CHANGED);

    private ArticleListsPagerAdapter adapter;
    private ViewPager viewPager;

    private Sortable.SortOrder sortOrder;
    private String searchQuery;

    public static ArticleListsFragment newInstance(String tag) {
        ArticleListsFragment fragment = new ArticleListsFragment();

        Bundle args = new Bundle();
        args.putString(PARAM_TAG, tag);
        fragment.setArguments(args);

        return fragment;
    }

    private String tag;

    public ArticleListsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getArguments() != null) {
            tag = getArguments().getString(PARAM_TAG);
        }

        if(savedInstanceState != null) {
            Log.v(TAG, "onCreate() restoring state");

            if(sortOrder == null) {
                sortOrder = Sortable.SortOrder.values()[savedInstanceState.getInt(STATE_SORT_ORDER)];
            }
            if(searchQuery == null) {
                searchQuery = savedInstanceState.getString(STATE_SEARCH_QUERY);
            }
        }
        if(sortOrder == null) sortOrder = SortOrder.DESC;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_article_lists, container, false);

        adapter = new ArticleListsPagerAdapter(getChildFragmentManager(), tag);

        viewPager = (ViewPager)view.findViewById(R.id.articles_list_pager);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                Log.v(TAG, "onPageSelected() position: " + position);

                setParametersToFragment(getCurrentFragment());
            }
        });

        TabLayout tabLayout = (TabLayout)view.findViewById(R.id.articles_list_tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        viewPager.setCurrentItem(1);

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.v(TAG, "onSaveInstanceState()");

        if(sortOrder != null) outState.putInt(STATE_SORT_ORDER, sortOrder.ordinal());
        if(searchQuery != null) outState.putString(STATE_SEARCH_QUERY, searchQuery);
    }

    @Override
    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;

        setSortOrder(getCurrentFragment(), sortOrder);
    }

    @Override
    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;

        setSearchQueryOnFragment(getCurrentFragment(), searchQuery);
    }

    public void onFeedsChangedEvent(FeedsChangedEvent event) {
        Log.d(TAG, "onFeedsChangedEvent()");

        invalidateLists(event);
    }

    private void setParametersToFragment(ArticleListFragment fragment) {
        Log.v(TAG, "setParametersToFragment() started");
        if(fragment == null) return;

        setSortOrder(fragment, sortOrder);
        setSearchQueryOnFragment(fragment, searchQuery);
    }

    private void setSortOrder(ArticleListFragment fragment,
                              Sortable.SortOrder sortOrder) {
        if(fragment != null) fragment.setSortOrder(sortOrder);
    }

    private void setSearchQueryOnFragment(ArticleListFragment fragment, String searchQuery) {
        if(fragment != null) fragment.setSearchQuery(searchQuery);
    }

    private ArticleListFragment getCurrentFragment() {
        return adapter == null || viewPager == null ? null
                : adapter.getCachedFragment(viewPager.getCurrentItem());
    }

    private ArticleListFragment getFragment(int position) {
        return adapter != null ? adapter.getCachedFragment(position) : null;
    }

    private void invalidateLists(FeedsChangedEvent event) {
        if(!Collections.disjoint(event.getInvalidateAllChanges(), CHANGE_SET)) {
            updateAllLists(!Collections.disjoint(event.getInvalidateAllChanges(),
                    CHANGE_SET_FORCE_CONTENT_UPDATE));
            return;
        }

        if(!Collections.disjoint(event.getMainFeedChanges(), CHANGE_SET)) {
            updateList(ArticleListsPagerAdapter.positionByFeedType(FeedsChangedEvent.FeedType.MAIN),
                    !Collections.disjoint(event.getMainFeedChanges(), CHANGE_SET_FORCE_CONTENT_UPDATE));
        }
        if(!Collections.disjoint(event.getFavoriteFeedChanges(), CHANGE_SET)) {
            updateList(ArticleListsPagerAdapter.positionByFeedType(FeedsChangedEvent.FeedType.FAVORITE),
                    !Collections.disjoint(event.getFavoriteFeedChanges(), CHANGE_SET_FORCE_CONTENT_UPDATE));
        }
        if(!Collections.disjoint(event.getArchiveFeedChanges(), CHANGE_SET)) {
            updateList(ArticleListsPagerAdapter.positionByFeedType(FeedsChangedEvent.FeedType.ARCHIVE),
                    !Collections.disjoint(event.getArchiveFeedChanges(), CHANGE_SET_FORCE_CONTENT_UPDATE));
        }
    }

    private void updateAllLists(boolean forceContentUpdate) {
        Log.d(TAG, "updateAllLists() started; forceContentUpdate: " + forceContentUpdate);

        for(int i = 0; i < ArticleListsPagerAdapter.PAGES.length; i++) {
            ArticleListFragment f = getFragment(i);
            if(f != null) {
                if(forceContentUpdate) f.forceContentUpdate();
                f.invalidateList();
            } else {
                Log.w(TAG, "updateAllLists() fragment is null; position: " + i);
            }
        }
    }

    private void updateList(int position, boolean forceContentUpdate) {
        Log.d(TAG, String.format("updateList() position: %d, forceContentUpdate: %s",
                position, forceContentUpdate));

        if(position != -1) {
            ArticleListFragment f = getFragment(position);
            if(f != null) {
                if(forceContentUpdate) f.forceContentUpdate();
                f.invalidateList();
            } else {
                Log.w(TAG, "updateList() fragment is null");
            }
        } else {
            updateAllLists(forceContentUpdate);
        }
    }

}
