package fr.gaulupeau.apps.Poche.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.TagListAdapter;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;

import static fr.gaulupeau.apps.Poche.data.dao.entities.Tag.sortTagListByLabel;

public class ManageArticleTagsActivity extends BaseActionBarActivity {

    public static final String PARAM_ARTICLE_ID = "article_id";

    private static final String TAG = "ManageArticleTagsA";

    private static final String STATE_NEW_TAGS = "new_tags";
    private static final String STATE_CURRENT_TAGS = "selected_tags";
    private static final String STATE_CURRENT_TEXT = "current_text";

    private Article article;
    private List<Tag> allTags;
    private List<Tag> newTags = new ArrayList<>();

    private List<Tag> suggestedTags = new ArrayList<>();
    private List<Tag> currentTags = new ArrayList<>();

    private TagListAdapter suggestedTagsAdapter;
    private TagListAdapter currentTagsAdapter;

    private EditText editText;
    private RecyclerView suggestedTagsView;
    private TextView currentTagsLabel;
    private RecyclerView currentTagsView;

    private String currentText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate() started");

        setContentView(R.layout.activity_manage_article_tags);
        setTitle(R.string.manageTags_title);

        Intent intent = getIntent();

        String[] newTagsArray = null;
        String[] currentTagsArray = null;
        String text = null;
        if(savedInstanceState != null) {
            newTagsArray = savedInstanceState.getStringArray(STATE_NEW_TAGS);
            currentTagsArray = savedInstanceState.getStringArray(STATE_CURRENT_TAGS);
            text = savedInstanceState.getString(STATE_CURRENT_TEXT);
        }

        int articleID = intent.getIntExtra(PARAM_ARTICLE_ID, -1);
        if(articleID == -1) {
            Log.w(TAG, "onCreate() articleID is not set");
            finish();
            return;
        }

        article = DbConnection.getSession().getArticleDao().queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleID))
                .unique();

        if(article == null) {
            Log.w(TAG, "onCreate() article is not found");
            finish();
            return;
        }

        allTags = DbConnection.getSession().getTagDao().queryBuilder()
                .orderAsc(TagDao.Properties.Label).list();

        if(newTagsArray != null) {
            for(String tag: newTagsArray) {
                Tag t = new Tag(null, null, tag);
                newTags.add(t);
                allTags.add(t);
            }
        }
        sortTagListByLabel(allTags);

        List<Tag> newCurrentList;
        if(currentTagsArray != null) {
            newCurrentList = new ArrayList<>();
            for(String tagLabel: currentTagsArray) {
                Tag tag = null;
                for(Tag t: allTags) {
                    if(TextUtils.equals(t.getLabel(), tagLabel)) {
                        tag = t;
                        break;
                    }
                }

                if(tag != null) {
                    newCurrentList.add(tag);
                } else {
                    Log.w(TAG, "onCreate() previously selected tag not found: " + tagLabel);
                }
            }
        } else {
            newCurrentList = new ArrayList<>(article.getTags());
        }

        suggestedTagsAdapter = new TagListAdapter(suggestedTags,
                new TagListAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(int position) {
                        suggestedTagClicked(position);
                    }
                }
        );

        currentTagsAdapter = new TagListAdapter(
                R.layout.tag_list_removable_item, currentTags,
                new TagListAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(int position) {
                        currentTagClicked(position);
                    }
                },
                new TagListAdapter.OnItemButtonClickListener() {
                    @Override
                    public void onItemButtonClick(int position) {
                        currentTagRemoveClicked(position);
                    }
                }
        );

        editText = (EditText)findViewById(R.id.tag_edit_text);
        if(editText != null) {
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable editable) {
                    textChanged(editable.toString());
                }
            });
        }

        Button addButton = (Button)findViewById(R.id.tag_add_button);
        if(addButton != null) {
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addButtonPressed();
                }
            });
        }

        suggestedTagsView = (RecyclerView)findViewById(R.id.manageTags_suggestionList);
        if(suggestedTagsView != null) {
            suggestedTagsView.setLayoutManager(new LinearLayoutManager(this));
            suggestedTagsView.setAdapter(suggestedTagsAdapter);
        }

        currentTagsView = (RecyclerView)findViewById(R.id.manageTags_currentList);
        if(currentTagsView != null) {
            currentTagsView.setLayoutManager(new LinearLayoutManager(this));
            currentTagsView.setAdapter(currentTagsAdapter);
        }

        currentTagsLabel = (TextView)findViewById(R.id.manageTags_currentList_label);

        updateCurrentTagList(newCurrentList);

        if(!TextUtils.isEmpty(text)) {
            setEditText(text);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.d(TAG, "onSaveInstanceState() started");

        if(newTags != null && !newTags.isEmpty()) {
            outState.putStringArray(STATE_NEW_TAGS, tagListToStringArray(newTags));
        }
        if(currentTags != null && !currentTags.isEmpty()) {
            outState.putStringArray(STATE_CURRENT_TAGS, tagListToStringArray(currentTags));
        }
        if(!TextUtils.isEmpty(currentText)) {
            outState.putString(STATE_CURRENT_TEXT, currentText);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        Log.d(TAG, "onCreateOptionsMenu() started");

        getMenuInflater().inflate(R.menu.activity_manage_tags, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_manageTags_save:
                save();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void save() {
        Log.d(TAG, "save() started");
        OperationsHelper.setArticleTags(this, article.getArticleId(), currentTags, this::finish);
    }

    private void textChanged(String text) {
        Log.d(TAG, "textChanged() text: " + text);

        currentText = text;

        updateSuggestedTagList();
    }

    private void updateSuggestedTagList() {
        if(TextUtils.isEmpty(currentText) && suggestedTags.isEmpty()) return;

        List<Tag> filteredList;
        if(TextUtils.isEmpty(currentText)) {
            filteredList = new ArrayList<>();
        } else {
            filteredList = filterTagList(currentText, allTags, currentTags);
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new TagListFragment.TagListDiffCallback(suggestedTags, filteredList));

        suggestedTags.clear();
        suggestedTags.addAll(filteredList);

        diffResult.dispatchUpdatesTo(suggestedTagsAdapter);

        if(suggestedTagsView != null) {
            suggestedTagsView.setVisibility(suggestedTags.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void updateCurrentTagList(List<Tag> newList) {
        if(currentTags.isEmpty() && newList.isEmpty()) return;

        sortTagListByLabel(newList);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new TagListFragment.TagListDiffCallback(currentTags, newList));

        currentTags.clear();
        currentTags.addAll(newList);

        diffResult.dispatchUpdatesTo(currentTagsAdapter);

        int visibility = currentTags.isEmpty() ? View.GONE : View.VISIBLE;

        if(currentTagsLabel != null) currentTagsLabel.setVisibility(visibility);
        if(currentTagsView != null) currentTagsView.setVisibility(visibility);

        updateSuggestedTagList();
    }

    private void addButtonPressed() {
        Log.d(TAG, "addButtonPressed() started; currentText: " + currentText);

        if(TextUtils.isEmpty(currentText)) return;

        List<Tag> tagsToAdd = new ArrayList<>();
        for(String label: currentText.split(",")) {
            label = label.trim();

            if(TextUtils.isEmpty(label)) continue;

            Tag tag = findTagByLabel(label, allTags);

            if(tag == null) {
                tag = new Tag(null, null, label);
                newTags.add(tag);
            }

            if(!currentTags.contains(tag)) tagsToAdd.add(tag);
        }

        if(!tagsToAdd.isEmpty()) {
            List<Tag> newTags = new ArrayList<>(currentTags.size() + tagsToAdd.size());
            newTags.addAll(currentTags);
            newTags.addAll(tagsToAdd);

            updateCurrentTagList(newTags);
        }

        setEditText("");
    }

    private void suggestedTagClicked(int position) {
        Log.d(TAG, "suggestedTagClicked() position: " + position);

        List<Tag> newList = new ArrayList<>(currentTags.size() + 1);
        newList.addAll(currentTags);
        newList.add(suggestedTags.get(position));

        updateCurrentTagList(newList);

        setEditText("");
    }

    private void currentTagClicked(int position) {
        Log.d(TAG, "currentTagClicked() position: " + position);

        Tag tag = currentTags.get(position);
        setEditText(tag.getLabel());
    }

    private void currentTagRemoveClicked(int position) {
        Log.d(TAG, "currentTagRemoveClicked() position: " + position);

        List<Tag> newList = new ArrayList<>(currentTags);
        newList.remove(currentTags.get(position));

        updateCurrentTagList(newList);
    }

    private void setEditText(String text) {
        if(editText != null) {
            editText.setText(text);
        }
    }

    private static String[] tagListToStringArray(List<Tag> list) {
        String[] labels = new String[list.size()];
        for(int i = 0; i < labels.length; i++) {
            labels[i] = list.get(i).getLabel();
        }
        return labels;
    }

    private static Tag findTagByLabel(String label, List<Tag> list) {
        if(TextUtils.isEmpty(label)) return null;

        label = label.toLowerCase(Locale.getDefault());

        for(Tag tag: list) {
            String tagLabel = tag.getLabel();
            if(tagLabel != null && label.equals(tagLabel.toLowerCase(Locale.getDefault()))) return tag;
        }

        return null;
    }

    private static List<Tag> filterTagList(String label, List<Tag> src, List<Tag> excludeList) {
        if(TextUtils.isEmpty(label) || src.isEmpty()) {
            return new ArrayList<>(src);
        }

        label = label.toLowerCase(Locale.getDefault());

        List<Tag> result = new ArrayList<>();
        for(Tag tag: src) {
            String tagLabel = tag.getLabel();
            if(tagLabel != null && tagLabel.toLowerCase(Locale.getDefault()).contains(label)) {
                if(excludeList != null && excludeList.contains(tag)) continue;

                result.add(tag);
            }
        }
        return result;
    }

}
