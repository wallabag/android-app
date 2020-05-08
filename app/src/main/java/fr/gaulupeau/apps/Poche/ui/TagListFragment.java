package fr.gaulupeau.apps.Poche.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.greendao.query.QueryBuilder;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.TagListAdapter;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

public class TagListFragment extends RecyclerViewListFragment<Tag> {

    public interface OnFragmentInteractionListener
            extends ArticleListFragment.OnFragmentInteractionListener {
        void onTagSelected(Tag tag);
    }

    private static final String TAG = TagListFragment.class.getSimpleName();

    private static final int PER_PAGE_LIMIT = 30;

    private OnFragmentInteractionListener host;

    private TagDao tagDao;

    public TagListFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        tagDao = DbConnection.getSession().getTagDao();

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Log.v(TAG, "onAttach()");

        if (context instanceof OnFragmentInteractionListener) {
            host = (OnFragmentInteractionListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        Log.v(TAG, "onDetach()");

        host = null;
    }

    @Override
    protected RecyclerView.Adapter getListAdapter(List<Tag> list) {
        return new TagListAdapter(list, TagListFragment.this::onItemClick);
    }

    @Override
    protected List<Tag> getItems(int page) {
        QueryBuilder<Tag> qb = getQueryBuilder()
                .limit(PER_PAGE_LIMIT);

        if (page > 0) {
            qb.offset(PER_PAGE_LIMIT * page);
        }

        List<Tag> tags = detachObjects(qb.list());

        if (page == 0 && TextUtils.isEmpty(searchQuery)) {
            tags.add(0, new Tag(null, null, getString(R.string.untagged)));
        }

        return tags;
    }

    private QueryBuilder<Tag> getQueryBuilder() {
        QueryBuilder<Tag> qb = tagDao.queryBuilder();

        if (!TextUtils.isEmpty(searchQuery)) {
            qb.where(TagDao.Properties.Label.like("%" + searchQuery + "%"));
        }

        switch (sortOrder) {
            case ASC:
                qb.orderAsc(TagDao.Properties.Label);
                break;

            case DESC:
                qb.orderDesc(TagDao.Properties.Label);
                break;

            default:
                throw new IllegalStateException("Sort order not implemented: " + sortOrder);
        }

        return qb;
    }

    // removes tags from cache: necessary for DiffUtil to work
    private List<Tag> detachObjects(List<Tag> tags) {
        for (Tag tag : tags) {
            tagDao.detach(tag);
        }

        return tags;
    }

    @Override
    protected void onSwipeRefresh() {
        super.onSwipeRefresh();

        if (host != null) host.onRecyclerViewListSwipeUpdate();
    }

    private void onItemClick(int position) {
        if (host != null) host.onTagSelected(itemList.get(position));
    }

    @Override
    protected DiffUtil.Callback getDiffUtilCallback(List<Tag> oldItems, List<Tag> newItems) {
        return new TagListDiffCallback(oldItems, newItems);
    }

    static class TagListDiffCallback extends DiffUtil.Callback {

        private List<Tag> oldList;
        private List<Tag> newList;

        TagListDiffCallback(List<Tag> oldList, List<Tag> newList) {
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
            Tag tag1 = oldList.get(oldItemPosition);
            Tag tag2 = newList.get(newItemPosition);
            Integer tagID1 = tag1.getTagId();
            Integer tagID2 = tag2.getTagId();

            if (tagID1 == null && tagID2 == null) {
                return TextUtils.equals(tag1.getLabel(), tag2.getLabel());
            }

            return !(tagID1 == null || tagID2 == null) && tagID1.equals(tagID2);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Tag oldTag = oldList.get(oldItemPosition);
            Tag newTag = newList.get(newItemPosition);

            return TextUtils.equals(oldTag.getLabel(), newTag.getLabel());
        }

    }

}
