package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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

    private static final String TAG = ArticlesListFragment.class.getSimpleName();

    private static final String LIST_TYPE_PARAM = "list_type";

    private static final int PER_PAGE_LIMIT = 30;

    private int listType;

    private OnFragmentInteractionListener host;

    private SwipeRefreshLayout refreshLayout;
    private RecyclerView recyclerView;

    private List<Article> mArticles;
    private ArticleDao mArticleDao;
    private ListAdapter mAdapter;
    private EndlessRecyclerViewScrollListener scrollListener;

    private boolean firstShown = true;

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

        if (getArguments() != null) {
            listType = getArguments().getInt(LIST_TYPE_PARAM, LIST_TYPE_UNREAD);
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

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);

        scrollListener = new EndlessRecyclerViewScrollListener(layoutManager) {
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

        if(firstShown) {
            firstShown = false;

            updateList();
        }

        onShow();
    }

    public void onPause() {
        super.onPause();

        Log.v(TAG, "Fragment " + listType + " onPause()");

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
    public void onItemClick(int position) {
        Article article = mArticles.get(position);
        openArticle(article.getId());
    }

    public void onShow() {
        Log.v(TAG, "Fragment " + listType + " onShow()");

        checkRefresh();
    }

    public void updateList() {
        List<Article> articles = getArticles(0);

        mArticles.clear();
        mArticles.addAll(articles);
        mAdapter.notifyDataSetChanged();
        scrollListener.resetState();
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

    private void checkRefresh() {
        if(host != null) {
            setRefreshingUI(host.isFullUpdateRunning() || host.isCurrentFeedUpdating());
        }
    }

    private List<Article> getArticles(int page) {
        QueryBuilder<Article> qb = getArticlesQueryBuilder()
                .limit(PER_PAGE_LIMIT);

        if(page > 0) {
            qb.offset(PER_PAGE_LIMIT * page);
        }

        return qb.list();
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

        qb.orderDesc(ArticleDao.Properties.ArticleId);

        return qb;
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

    public interface OnFragmentInteractionListener {
        void updateFeed();
        boolean isFullUpdateRunning();
        boolean isCurrentFeedUpdating();
    }

}
