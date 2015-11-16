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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.greenrobot.dao.query.LazyList;
import de.greenrobot.dao.query.QueryBuilder;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.ListAdapter;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;

import static fr.gaulupeau.apps.Poche.data.ListTypes.*;

public class ArticlesListFragment extends Fragment implements ListAdapter.OnItemClickListener {

    // TODO: remove logging
    private static final String TAG = ArticlesListFragment.class.getSimpleName();

    private static final String LIST_TYPE_PARAM = "list_type";

    private int listType;

    private OnFragmentInteractionListener host;

    private SwipeRefreshLayout refreshLayout;

    private Settings settings;
    private List<Article> mArticles;
    private ArticleDao mArticleDao;
    private ListAdapter mAdapter;

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

        settings = new Settings(getActivity());

        DaoSession daoSession = DbConnection.getSession();
        mArticleDao = daoSession.getArticleDao();

        mArticles = new ArrayList<>();

        mAdapter = new ListAdapter(mArticles, this, listType);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.list, container, false);

        RecyclerView readList = (RecyclerView) view.findViewById(R.id.article_list);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        readList.setLayoutManager(layoutManager);

        readList.setAdapter(mAdapter);

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

        Log.d(TAG, "Fragment " + listType + " onResume()");

        if(firstShown) {
            firstShown = false;

            updateList();
        }

        onShow();
    }

    public void onPause() {
        super.onPause();

        Log.d(TAG, "Fragment " + listType + " onPause()");

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

        Log.d(TAG, "Fragment " + listType + " onAttach()");

        if(context instanceof OnFragmentInteractionListener) {
            host = (OnFragmentInteractionListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        Log.d(TAG, "Fragment " + listType + " onDetach()");

        host = null;
    }

    @Override
    public void onItemClick(int position) {
        Article article = mArticles.get(position);
        openArticle(article.getId());
    }

    public void onShow() {
        Log.d(TAG, "Fragment " + listType + " onShow()");

        checkRefresh();
    }

    public void updateList() {
        List<Article> articles = getArticles();

        mArticles.clear();
        mArticles.addAll(articles);
        mAdapter.notifyDataSetChanged();
    }

    public void openRandomArticle() {
        LazyList<Article> articles = getArticlesQueryBuilder(false).listLazyUncached();

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

    private List<Article> getArticles() {
        return getArticlesQueryBuilder(true).list();
    }

    private QueryBuilder<Article> getArticlesQueryBuilder(boolean honorLimit) {
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

        if(honorLimit) {
            int limit = settings.getInt(Settings.LIST_LIMIT, -1);
            if(limit > 0) qb.limit(limit);
        }

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
