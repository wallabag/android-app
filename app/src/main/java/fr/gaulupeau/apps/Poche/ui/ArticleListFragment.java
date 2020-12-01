package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import org.greenrobot.greendao.query.LazyList;
import org.greenrobot.greendao.query.QueryBuilder;
import org.greenrobot.greendao.query.WhereCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.ListAdapter;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.FtsDao;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_ARCHIVED;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_FAVORITES;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_UNREAD;

public class ArticleListFragment extends RecyclerViewListFragment<Article, ListAdapter>
        implements ContextMenuItemHandler {

    public interface OnFragmentInteractionListener {
        void onRecyclerViewListSwipeUpdate();
    }

    private static final String TAG = ArticleListFragment.class.getSimpleName();

    private static final String LIST_TYPE_PARAM = "list_type";
    private static final String TAG_PARAM = "tag";

    private static final int PER_PAGE_LIMIT = 30;

    private int listType;
    private String tagLabel;
    private boolean untagged;
    private List<Long> tagIDs;

    private OnFragmentInteractionListener host;

    private ArticleDao articleDao;
    private TagDao tagDao;

    private boolean forceContentUpdate;

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
        if (arguments != null) {
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
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        Log.v(TAG, "Fragment " + listType + " onCreateOptionsMenu()");

        inflater.inflate(R.menu.fragment_article_list, menu);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Log.v(TAG, "Fragment " + listType + " onAttach()");

        if (context instanceof OnFragmentInteractionListener) {
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
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "Fragment " + listType + " onOptionsItemSelected()");

        if (item.getItemId() == R.id.menu_list_openRandomArticle) {
            openRandomArticle();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean handleContextItemSelected(Activity activity, MenuItem item) {
        return listAdapter.handleContextItemSelected(activity, item);
    }

    public void forceContentUpdate() {
        forceContentUpdate = true;
    }

    @Override
    protected ListAdapter createListAdapter(List<Article> list) {
        return new ListAdapter(App.getInstance(), App.getSettings(),
                list, position -> openArticle(itemList.get(position).getId()), listType);
    }

    @Override
    protected void resetContent() {
        untagged = false;
        tagIDs = null;

        if (getString(R.string.untagged).equals(tagLabel)) {
            untagged = true;
        } else if (tagLabel != null) {
            List<Tag> tags = tagDao.queryBuilder()
                    .where(TagDao.Properties.Label.eq(tagLabel))
                    .orderDesc(TagDao.Properties.Label)
                    .list();

            tagIDs = new ArrayList<>(tags.size());
            for (Tag t : tags) {
                tagIDs.add(t.getId());
            }
        }

        super.resetContent();

        forceContentUpdate = false;
    }

    @Override
    protected List<Article> getItems(int page) {
        QueryBuilder<Article> qb = getQueryBuilder()
                .limit(PER_PAGE_LIMIT);

        if (page > 0) {
            qb.offset(PER_PAGE_LIMIT * page);
        }

        return detachObjects(qb.list());
    }

    private QueryBuilder<Article> getQueryBuilder() {
        QueryBuilder<Article> qb = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.isNotNull());

        if (untagged) {
            qb.where(new WhereCondition.PropertyCondition(ArticleDao.Properties.Id, " NOT IN "
                    + "(select " + ArticleTagsJoinDao.Properties.ArticleId.columnName
                    + " from " + ArticleTagsJoinDao.TABLENAME + ")"));
        } else if (tagIDs != null && !tagIDs.isEmpty()) {
            // TODO: try subquery
            qb.join(ArticleTagsJoin.class, ArticleTagsJoinDao.Properties.ArticleId)
                    .where(ArticleTagsJoinDao.Properties.TagId.in(tagIDs));
        }

        switch (listType) {
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

        if (!TextUtils.isEmpty(searchQuery)) {
            if (FtsDao.isFtsSupported()) {
                qb.where(new WhereCondition.PropertyCondition(ArticleDao.Properties.Id, " IN (" +
                        FtsDao.getQueryString() + DatabaseUtils.sqlEscapeString(searchQuery) + ")"));
            } else {
                qb.where(ArticleDao.Properties.Title.like("%" + searchQuery + "%"));
            }
        }

        switch (sortOrder) {
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
        for (Article article : articles) {
            articleDao.detach(article);
        }

        return articles;
    }

    @Override
    protected void onSwipeRefresh() {
        super.onSwipeRefresh();

        if (host != null) host.onRecyclerViewListSwipeUpdate();
    }

    @Override
    protected DiffUtil.Callback getDiffUtilCallback(List<Article> oldItems, List<Article> newItems) {
        return new ArticleListDiffCallback(oldItems, newItems, forceContentUpdate);
    }

    private void openRandomArticle() {
        LazyList<Article> articles = getQueryBuilder().listLazyUncached();

        if (!articles.isEmpty()) {
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
        if (activity != null) {
            Intent intent = new Intent(activity, ReadArticleActivity.class);
            intent.putExtra(ReadArticleActivity.EXTRA_ID, id);

            switch (listType) {
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
        private boolean forceContentUpdate;

        ArticleListDiffCallback(List<Article> oldList, List<Article> newList, boolean forceContentUpdate) {
            this.oldList = oldList;
            this.newList = newList;
            this.forceContentUpdate = forceContentUpdate;
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
            if (forceContentUpdate) return false;

            Article oldArticle = oldList.get(oldItemPosition);
            Article newArticle = newList.get(newItemPosition);

            return oldArticle.getArchive().equals(newArticle.getArchive())
                    && oldArticle.getFavorite().equals(newArticle.getFavorite())
                    && TextUtils.equals(oldArticle.getTitle(), newArticle.getTitle())
                    && TextUtils.equals(oldArticle.getDomain(), newArticle.getDomain());
        }

    }

}
