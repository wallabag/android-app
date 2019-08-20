package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;

public abstract class RecyclerViewListFragment<T> extends Fragment
        implements Sortable, Searchable, LoaderManager.LoaderCallbacks<List<T>> {

    private static final String TAG = "RecyclerVLFragment";

    protected static final String STATE_SORT_ORDER = "sort_order";
    protected static final String STATE_SEARCH_QUERY = "search_query";

    protected Sortable.SortOrder sortOrder;
    protected String searchQuery;

    protected SwipeRefreshLayout refreshLayout;
    protected RecyclerView recyclerView;
    protected LinearLayoutManager recyclerViewLayoutManager;

    protected List<T> itemList;
    protected RecyclerView.Adapter listAdapter;
    protected EndlessRecyclerViewScrollListener scrollListener;

    protected boolean active = false;
    protected boolean invalidList = true;

    protected boolean cleanLoad = true;
    protected int currentPage = 0;

    public RecyclerViewListFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.v(TAG, "onCreate()");

        if(savedInstanceState != null) {
            Log.v(TAG, "onCreate() restoring state");

            if(sortOrder == null) {
                sortOrder = Sortable.SortOrder.values()[savedInstanceState.getInt(STATE_SORT_ORDER)];
            }
            if(searchQuery == null) {
                searchQuery = savedInstanceState.getString(STATE_SEARCH_QUERY);
            }
        }
        if(sortOrder == null) sortOrder = Sortable.SortOrder.DESC;

        itemList = new ArrayList<>();

        listAdapter = getListAdapter(itemList);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(getLayoutResID(), container, false);

        recyclerView = (RecyclerView) view.findViewById(getRecyclerViewResID());

        recyclerViewLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(recyclerViewLayoutManager);

        scrollListener = new EndlessRecyclerViewScrollListener(recyclerViewLayoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                loadMore(page, totalItemsCount);
            }
        };

        recyclerView.addOnScrollListener(scrollListener);
        recyclerView.setAdapter(listAdapter);

        refreshLayout = (SwipeRefreshLayout) view.findViewById(getSwipeContainerResID());
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onSwipeRefresh();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        active = true;

        checkList();
    }

    @Override
    public void onPause() {
        super.onPause();

        active = false;

        if(refreshLayout != null) {
            // http://stackoverflow.com/a/27073879
            refreshLayout.setRefreshing(false);
            refreshLayout.clearAnimation();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.v(TAG, "onSaveInstanceState()");

        if(sortOrder != null) outState.putInt(STATE_SORT_ORDER, sortOrder.ordinal());
        if(searchQuery != null) outState.putString(STATE_SEARCH_QUERY, searchQuery);
    }

    @Override
    public Loader<List<T>> onCreateLoader(int id, Bundle args) {
        Log.d(TAG, "onCreateLoader()");

        return new AsyncTaskLoader<List<T>>(getActivity()) {
            @Override
            public List<T> loadInBackground() {
                return load();
            }

            @Override
            public void deliverResult(List<T> articles) {
                if(isStarted()) { // TODO: check
                    super.deliverResult(articles);
                }
            }

            @Override
            protected void onStartLoading() {
                forceLoad();
            }

            @Override
            protected void onStopLoading() {
                cancelLoad();
            }

            @Override
            protected void onReset() {
                super.onReset();

                onStopLoading();
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<List<T>> loader, List<T> data) {
        Log.d(TAG, "onLoadFinished()");

        processContent(data);
    }

    @Override
    public void onLoaderReset(Loader<List<T>> loader) {
        Log.d(TAG, "onLoaderReset()");
    }

    @Override
    public void setSortOrder(Sortable.SortOrder sortOrder) {
        Sortable.SortOrder oldSortOrder = this.sortOrder;
        this.sortOrder = sortOrder;

        if(sortOrder != oldSortOrder) invalidateList();
    }

    @Override
    public void setSearchQuery(String searchQuery) {
        String oldSearchQuery = this.searchQuery;
        this.searchQuery = searchQuery;

        if(!TextUtils.equals(oldSearchQuery, searchQuery)) invalidateList();
    }

    public void invalidateList() {
        invalidList = true;

        if(active) checkList();
    }

    protected void checkList() {
        if(invalidList) {
            invalidList = false;

            resetContent();
        }
    }

    protected @LayoutRes int getLayoutResID() {
        return R.layout.list;
    }

    protected @IdRes int getRecyclerViewResID() {
        return R.id.list_recyclerView;
    }

    protected @IdRes int getSwipeContainerResID() {
        return R.id.list_swipeContainer;
    }

    protected abstract RecyclerView.Adapter getListAdapter(List<T> list);

    protected void restartLoader() {
        Log.d(TAG, "restartLoader()");

        getLoaderManager().restartLoader(0, null, this);
    }

    protected void resetContent() {
        Log.d(TAG, "resetContent()");

        cleanLoad = true;
        currentPage = 0;
        restartLoader();
    }

    protected void processContent(List<T> items) {
        Log.d(TAG, "processContent() items.size(): " + items.size());

        if(cleanLoad) {
            processContentClean(items);
        } else {
            processContentAdd(items);
        }
    }

    protected void processContentClean(List<T> items) {
        Log.d(TAG, "processContentClean() items.size(): " + items.size());

        boolean scrollToTop = false;
        if(recyclerViewLayoutManager != null) {
            scrollToTop = recyclerViewLayoutManager.findFirstCompletelyVisibleItemPosition() == 0;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(getDiffUtilCallback(itemList, items));

        itemList.clear();
        itemList.addAll(items);

        diffResult.dispatchUpdatesTo(listAdapter);

        if(scrollListener != null) scrollListener.resetState();

        if(scrollToTop && recyclerView != null) {
            recyclerView.scrollToPosition(0);
        }
    }

    protected void processContentAdd(List<T> items) {
        Log.d(TAG, "processContentAdd() items.size(): " + items.size());

        itemList.addAll(items);

        final int addedItemsCount = items.size();
        final int totalItemsCount = itemList.size();

        recyclerView.post(new Runnable() {
            @Override
            public void run() {
                listAdapter.notifyItemRangeInserted(totalItemsCount, addedItemsCount);
            }
        });
    }

    protected void loadMore(int page, final int totalItemsCount) {
        Log.d(TAG, String.format("loadMore(page: %d, totalItemsCount: %d)", page, totalItemsCount));

        if(page <= currentPage) return;

        currentPage++;
        if(page != currentPage) {
            Log.w(TAG, String.format("loadMore() page request mismatch!" +
                            " page: %d, currentPage: %d; resetting",
                    page, currentPage));

            resetContent();
            return;
        }

        cleanLoad = false;
        restartLoader();
    }

    protected List<T> load() {
        Log.d(TAG, "load()");

        return getItems(currentPage);
    }

    protected abstract List<T> getItems(int page);

    protected abstract DiffUtil.Callback getDiffUtilCallback(List<T> oldItems, List<T> newItems);

    protected void onSwipeRefresh() {
        if(refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
    }

}
