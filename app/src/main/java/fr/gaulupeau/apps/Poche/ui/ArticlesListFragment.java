package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.greenrobot.greendao.query.LazyList;
import org.greenrobot.greendao.query.QueryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.ListAdapter;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

import static fr.gaulupeau.apps.Poche.data.ListTypes.*;

public class ArticlesListFragment extends Fragment implements ListAdapter.OnItemClickListener {

    public enum SortOrder {
        DESC, ASC
    }

    private static final String TAG = ArticlesListFragment.class.getSimpleName();

    private static final String LIST_TYPE_PARAM = "list_type";

    private static final String SORT_ORDER_STATE = "sort_order";
    private static final String SEARCH_QUERY_STATE = "search_query";

    private static final int PER_PAGE_LIMIT = 30;

    private int listType;
    private SortOrder sortOrder = SortOrder.DESC;
    private String searchQuery;

    private OnFragmentInteractionListener host;

    private SwipeRefreshLayout refreshLayout;
    private RecyclerView recyclerView;
    private LinearLayoutManager recyclerViewLayoutManager;

    private List<Article> mArticles;
    private ArticleDao mArticleDao;
    private ListAdapter mAdapter;
    private EndlessRecyclerViewScrollListener scrollListener;

    private boolean active = false;
    private boolean invalidList = true;

    public static ArticlesListFragment newInstance(int listType) {
        ArticlesListFragment fragment = new ArticlesListFragment();

        Bundle args = new Bundle();
        args.putInt(LIST_TYPE_PARAM, listType);
        fragment.setArguments(args);

        return fragment;
    }

    public ArticlesListFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.v(TAG, "Fragment " + listType + " onCreate()");

        if(getArguments() != null) {
            listType = getArguments().getInt(LIST_TYPE_PARAM, LIST_TYPE_UNREAD);
        }

        if(savedInstanceState != null) {
            Log.v(TAG, "Fragment " + listType + " onCreate() restoring state");

            sortOrder = SortOrder.values()[savedInstanceState.getInt(SORT_ORDER_STATE)];
            searchQuery = savedInstanceState.getString(SEARCH_QUERY_STATE);
        }

        DaoSession daoSession = DbConnection.getSession();
        mArticleDao = daoSession.getArticleDao();

        mArticles = new ArrayList<>();

        mAdapter = new ListAdapter(mArticles, this, listType);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.list, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.article_list);

        recyclerViewLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(recyclerViewLayoutManager);

        scrollListener = new EndlessRecyclerViewScrollListener(recyclerViewLayoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                loadMore(page, totalItemsCount);
            }
        };

        recyclerView.addOnScrollListener(scrollListener);
        recyclerView.setAdapter(mAdapter);

        refreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (host != null) {
                    host.updateFeed();
                }
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.v(TAG, "Fragment " + listType + " onResume()");

        active = true;

        checkList();
    }

    public void onPause() {
        super.onPause();

        Log.v(TAG, "Fragment " + listType + " onPause()");

        active = false;

        if(refreshLayout != null) {
            // http://stackoverflow.com/a/27073879
            refreshLayout.setRefreshing(false);
//            refreshLayout.destroyDrawingCache();
            refreshLayout.clearAnimation();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Log.v(TAG, "Fragment " + listType + " onAttach()");

        if(context instanceof OnFragmentInteractionListener) {
            host = (OnFragmentInteractionListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        Log.v(TAG, "Fragment " + listType + " onDetach()");

        host = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.v(TAG, "Fragment " + listType + " onSaveInstanceState()");

        outState.putInt(SORT_ORDER_STATE, sortOrder.ordinal());
        outState.putString(SEARCH_QUERY_STATE, searchQuery);
    }

    @Override
    public void onItemClick(int position) {
        Article article = mArticles.get(position);
        openArticle(article.getId());
    }

    public int getListType() {
        return listType;
    }

    public void setSortOrder(SortOrder sortOrder) {
        SortOrder oldSortOrder = this.sortOrder;
        this.sortOrder = sortOrder;

        if(sortOrder != oldSortOrder) invalidateList();
    }

    public void setSearchQuery(String searchQuery) {
        String oldSearchQuery = this.searchQuery;
        this.searchQuery = searchQuery;

        if(!TextUtils.equals(oldSearchQuery, searchQuery)) invalidateList();
    }

    public void invalidateList() {
        invalidList = true;

        if(active) checkList();
    }

    public void checkList() {
        if(invalidList) {
            invalidList = false;

            resetListContent();
        }
    }

    private void resetListContent() {
        List<Article> articles = getArticles(0);

        boolean scrollToTop = false;
        if(recyclerViewLayoutManager != null) {
            scrollToTop = recyclerViewLayoutManager.findFirstCompletelyVisibleItemPosition() == 0;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new ArticleListDiffCallback(mArticles, articles));

        mArticles.clear();
        mArticles.addAll(articles);

        diffResult.dispatchUpdatesTo(mAdapter);

        if(scrollListener != null) scrollListener.resetState();

        if(scrollToTop && recyclerView != null) {
            recyclerView.scrollToPosition(0);
        }
    }

    private void loadMore(int page, final int totalItemsCount) {
        Log.d(TAG, String.format("loadMore(page: %d, totalItemsCount: %d)", page, totalItemsCount));

        List<Article> articles = getArticles(page);
        final int addedItemsCount = articles.size();

        mArticles.addAll(articles);
        recyclerView.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyItemRangeInserted(totalItemsCount, addedItemsCount);
            }
        });
    }

    public void openRandomArticle() {
        LazyList<Article> articles = getArticlesQueryBuilder().listLazyUncached();

        if(!articles.isEmpty()) {
            long id = articles.get(new Random().nextInt(articles.size())).getId();
            articles.close();

            openArticle(id);
        } else {
            Toast.makeText(getActivity(), R.string.no_articles, Toast.LENGTH_SHORT).show();
        }
    }

    public void setRefreshingUI(boolean refreshing) {
        if(refreshLayout != null) {
            refreshLayout.setRefreshing(refreshing);
        }
    }

    private List<Article> getArticles(int page) {
        QueryBuilder<Article> qb = getArticlesQueryBuilder()
                .limit(PER_PAGE_LIMIT);

        if(page > 0) {
            qb.offset(PER_PAGE_LIMIT * page);
        }

        return removeContent(detachArticleObjects(qb.list()));
    }

    private QueryBuilder<Article> getArticlesQueryBuilder() {
        QueryBuilder<Article> qb = mArticleDao.queryBuilder();

        switch(listType) {
            case LIST_TYPE_ARCHIVED:
                qb.where(ArticleDao.Properties.Archive.eq(true));
                break;

            case LIST_TYPE_FAVORITES:
                qb.where(ArticleDao.Properties.Favorite.eq(true));
                break;

            default:
                qb.where(ArticleDao.Properties.Archive.eq(false));
                break;
        }

        if(!TextUtils.isEmpty(searchQuery)) {
            qb.whereOr(ArticleDao.Properties.Title.like("%" + searchQuery + "%"),
                    ArticleDao.Properties.Content.like("%" + searchQuery + "%"));
        }

        switch(sortOrder) {
            case ASC:
                qb.orderAsc(ArticleDao.Properties.ArticleId);
                break;

            case DESC:
                qb.orderDesc(ArticleDao.Properties.ArticleId);
                break;

            default:
                throw new IllegalStateException("Sort order not implemented: " + sortOrder);
        }

        return qb;
    }

    // removes articles from cache: necessary for DiffUtil to work
    private List<Article> detachArticleObjects(List<Article> articles) {
        for(Article article: articles) {
            mArticleDao.detach(article);
        }

        return articles;
    }

    // removes content from article objects in order to free memory
    private List<Article> removeContent(List<Article> articles) {
        for(Article article: articles) {
            article.setContent(null);
        }

        return articles;
    }

    private void openArticle(long id) {
        Activity activity = getActivity();
        if(activity != null) {
            Intent intent = new Intent(activity, ReadArticleActivity.class);
            intent.putExtra(ReadArticleActivity.EXTRA_ID, id);

            switch(listType) {
                case LIST_TYPE_FAVORITES:
                    intent.putExtra(ReadArticleActivity.EXTRA_LIST_FAVORITES, true);
                    break;
                case LIST_TYPE_ARCHIVED:
                    intent.putExtra(ReadArticleActivity.EXTRA_LIST_ARCHIVED, true);
                    break;
                default:
                    intent.putExtra(ReadArticleActivity.EXTRA_LIST_ARCHIVED, false);
                    break;
            }

            startActivity(intent);
        }
    }

    private static class ArticleListDiffCallback extends DiffUtil.Callback {

        private List<Article> oldList;
        private List<Article> newList;

        ArticleListDiffCallback(List<Article> oldList, List<Article> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getArticleId().equals(
                    newList.get(newItemPosition).getArticleId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Article oldArticle = oldList.get(oldItemPosition);
            Article newArticle = newList.get(newItemPosition);

            return oldArticle.getArchive().equals(newArticle.getArchive())
                    && oldArticle.getFavorite().equals(newArticle.getFavorite())
                    && TextUtils.equals(oldArticle.getTitle(), newArticle.getTitle())
                    && TextUtils.equals(oldArticle.getUrl(), newArticle.getUrl());
        }

    }

    public interface OnFragmentInteractionListener {
        void updateFeed();
    }

}
