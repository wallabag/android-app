package fr.gaulupeau.apps.Poche.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.OperationsHelper;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

public class ManageArticleTagsDialogFragment extends DialogFragment {

    private static final String TAG = "ManageArtTagsFragment";

    private static final String PARAM_ARTICLE_ID = "article_id";

    private Article article;
    private List<Tag> allTags;
    private List<Tag> selectedTags;

    public static ManageArticleTagsDialogFragment newInstance(int articleID) {
        ManageArticleTagsDialogFragment fragment = new ManageArticleTagsDialogFragment();

        Bundle args = new Bundle();
        args.putInt(PARAM_ARTICLE_ID, articleID);
        fragment.setArguments(args);

        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.d(TAG, "onCreateDialog() started");

        article = DbConnection.getSession().getArticleDao().queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(getArguments().getInt(PARAM_ARTICLE_ID)))
                .unique();
        selectedTags = new ArrayList<>(article.getTags());
        allTags = DbConnection.getSession().getTagDao().queryBuilder().list();

        String[] items = new String[allTags.size()];
        boolean[] checkedItems = new boolean[allTags.size()];

        for(int i = 0; i < items.length; i++) {
            Tag tag = allTags.get(i);
            items[i] = tag.getLabel();
            checkedItems[i] = selectedTags.contains(tag);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMultiChoiceItems(items, checkedItems,
                new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                if(isChecked) {
                    selectedTags.add(allTags.get(which));
                } else {
                    selectedTags.remove(allTags.get(which));
                }
            }
        });

        builder.setPositiveButton(R.string.manageTags_save, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Log.d(TAG, "positiveButton.onClick() saving tags");
                OperationsHelper.setArticleTags(getActivity(), article.getArticleId(), selectedTags);
            }
        });
        builder.setNegativeButton(R.string.manageTags_cancel, null);

        return builder.create();
    }

}
