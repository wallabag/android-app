package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.greenrobot.greendao.query.LazyList;
import org.greenrobot.greendao.query.QueryBuilder;

import java.util.List;
import java.util.Random;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.ListAdapter;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_ARCHIVED;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_FAVORITES;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_UNREAD;

public class ArticleListFragment extends RecyclerViewListFragment<Article> {

    public interface OnFragmentInteractionListener {
        void onRecyclerViewListSwipeUpdate();
    }

    private static final String TAG = ArticleListFragment.class.getSimpleName();

    private static final String LIST_TYPE_PARAM = "list_type";
    private static final String TAG_PARAM = "tag";

    private static final int PER_PAGE_LIMIT = 30;

    private int listType;
    private String tagLabel;
    private Long tagID;

    private OnFragmentInteractionListener host;

    private ArticleDao articleDao;
    private TagDao tagDao;

    public static ArticleListFragment newInstance(int listType, String tag) {
        ArticleListFragment fragment = new ArticleListFragment();

        Bundle args = new Bundle();
        args.putInt(LIST_TYPE_PARAM, listType);
        args.putString(TAG_PARAM, tag);
        fragment.setArguments(args);

        return fragment;
    }

    public ArticleListFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if(arguments != null) {
            listType = arguments.getInt(LIST_TYPE_PARAM, LIST_TYPE_UNREAD);
            tagLabel = arguments.getString(TAG_PARAM);
        }

        Log.v(TAG, "Fragment " + listType + " onCreate()");

        DaoSession daoSession = DbConnection.getSession();
        articleDao = daoSession.getArticleDao();
        tagDao = daoSession.getTagDao();

        setHasOptionsMenu(true);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        Log.v(TAG, "Fragment " + listType + " onCreateOptionsMenu()");

        inflater.inflate(R.menu.fragment_article_list, menu);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Log.v(TAG, "Fragment " + listType + " onAttach()");

        if(context instanceof OnFragmentInteractionListener) {
            host = (OnFragmentInteractionListener)context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        Log.v(TAG, "Fragment " + listType + " onDetach()");

        host = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "Fragment " + listType + " onOptionsItemSelected()");

        switch(item.getItemId()) {
            case R.id.menu_list_openRandomArticle:
                openRandomArticle();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected RecyclerView.Adapter getListAdapter(List<Article> list) {
        return new ListAdapter(list, new ListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                Article article = itemList.get(position);
                openArticle(article.getId());
            }
        }, listType);
    }

    @Override
    protected void resetContent() {
        if(tagLabel != null) {
            // TODO: check: can be non-unique?
            Tag tag = tagDao.queryBuilder()
                    .where(TagDao.Properties.Label.eq(tagLabel))
                    .orderDesc(TagDao.Properties.Label)
                    .unique();

            tagID = tag != null ? tag.getId() : null;
        } else {
            tagID = null;
        }

        super.resetContent();
    }

    @Override
    protected List<Article> getItems(int page) {
        QueryBuilder<Article> qb = getQueryBuilder()
                .limit(PER_PAGE_LIMIT);

        if(page > 0) {
            qb.offset(PER_PAGE_LIMIT * page);
        }

        return removeContent(detachObjects(qb.list()));
    }

    private QueryBuilder<Article> getQueryBuilder() {
        QueryBuilder<Article> qb = articleDao.queryBuilder();

        if(tagID != null) {
            // TODO: try subquery
            qb.join(ArticleTagsJoin.class, ArticleTagsJoinDao.Properties.ArticleId)
                    .where(ArticleTagsJoinDao.Properties.TagId.eq(tagID));
        }

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
    private List<Article> detachObjects(List<Article> articles) {
        for(Article article: articles) {
            articleDao.detach(article);
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

    @Override
    protected void onSwipeRefresh() {
        super.onSwipeRefresh();

        if(host != null) host.onRecyclerViewListSwipeUpdate();
    }

    @Override
    protected DiffUtil.Callback getDiffUtilCallback(List<Article> oldItems, List<Article> newItems) {
        return new ArticleListDiffCallback(oldItems, newItems);
    }

    private void openRandomArticle() {
        LazyList<Article> articles = getQueryBuilder().listLazyUncached();

        if(!articles.isEmpty()) {
            long id = articles.get(new Random().nextInt(articles.size())).getId();

            openArticle(id);
        } else {
            Toast.makeText(getActivity(), R.string.no_articles, Toast.LENGTH_SHORT).show();
        }

        articles.close();
    }

    // TODO: include more info (order, search query, tag)
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

}
