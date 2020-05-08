package fr.gaulupeau.apps.Poche.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.TagListAdapter;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.LocalArticleReplacedEvent;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.service.workers.OperationsWorker;

import static fr.gaulupeau.apps.Poche.data.dao.entities.Tag.sortTagListByLabel;

public class ManageArticleTagsActivity extends BaseActionBarActivity {

    public static final String PARAM_ARTICLE_ID = "article_id";
    public static final String PARAM_ARTICLE_URL = "article_url";

    private static final String TAG = "ManageArticleTagsA";

    private static final String STATE_DISCOVERED_ARTICLE_ID = "discovered_article_id";
    private static final String STATE_CURRENT_TAGS = "selected_tags";
    private static final String STATE_CURRENT_TEXT = "current_text";

    private int discoveredArticleId = -1;

    private Article article;

    private List<Tag> availableTags;

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

        String[] currentTagsArray = null;
        String text = null;
        if (savedInstanceState != null) {
            discoveredArticleId = savedInstanceState.getInt(STATE_DISCOVERED_ARTICLE_ID, -1);
            currentTagsArray = savedInstanceState.getStringArray(STATE_CURRENT_TAGS);
            text = savedInstanceState.getString(STATE_CURRENT_TEXT);
        }

        if (!loadArticle()) return;

        suggestedTagsAdapter = new TagListAdapter(suggestedTags, this::suggestedTagClicked);

        currentTagsAdapter = new TagListAdapter(
                R.layout.tag_list_removable_item, currentTags,
                this::currentTagClicked,
                this::currentTagRemoveClicked
        );

        editText = findViewById(R.id.tag_edit_text);
        if (editText != null) {
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

        suggestedTagsView = findViewById(R.id.manageTags_suggestionList);
        if (suggestedTagsView != null) {
            suggestedTagsView.setLayoutManager(new LinearLayoutManager(this));
            suggestedTagsView.setAdapter(suggestedTagsAdapter);
        }

        currentTagsView = findViewById(R.id.manageTags_currentList);
        if (currentTagsView != null) {
            currentTagsView.setLayoutManager(new LinearLayoutManager(this));
            currentTagsView.setAdapter(currentTagsAdapter);
        }

        currentTagsLabel = findViewById(R.id.manageTags_currentList_label);

        loadAvailableTags();

        List<Tag> newList;
        if (currentTagsArray != null) {
            newList = toTags(Arrays.asList(currentTagsArray));
        } else {
            newList = new ArrayList<>(article.getTags());
        }

        updateCurrentTagList(newList);

        if (!TextUtils.isEmpty(text)) {
            setEditText(text);
        }

        EventBus.getDefault().register(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState() started");

        if (currentTags != null && !currentTags.isEmpty()) {
            outState.putStringArray(STATE_CURRENT_TAGS, tagListToStringArray(currentTags));
        }
        if (!TextUtils.isEmpty(currentText)) {
            outState.putString(STATE_CURRENT_TEXT, currentText);
        }
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);

        super.onDestroy();
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
        if (item.getItemId() == R.id.menu_manageTags_save) {
            save();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onArticlesChangedEvent(ArticlesChangedEvent event) {
        Log.d(TAG, "onArticlesChangedEvent() started");

        if (article.getArticleId() == null) return;

        if (event.isChanged(article, FeedsChangedEvent.ChangeType.TAG_SET_CHANGED)) {
            reload(true);
        } else if (event.contains(FeedsChangedEvent.ChangeType.TAGS_CHANGED_GLOBALLY)) {
            reload(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLocalArticleReplacedEvent(LocalArticleReplacedEvent event) {
        Log.d(TAG, "onLocalArticleReplacedEvent() started");

        if (article.getArticleId() != null) return;

        if (TextUtils.equals(article.getGivenUrl(), event.getGivenUrl())) {
            discoveredArticleId = event.getArticleId();

            reload(true);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean loadArticle() {
        Intent intent = getIntent();

        int articleId = intent.getIntExtra(PARAM_ARTICLE_ID, -1);
        if (articleId == -1) articleId = discoveredArticleId;

        if (articleId != -1) {
            Log.v(TAG, "loadArticle() loading by articleId");

            article = DbConnection.getSession().getArticleDao().queryBuilder()
                    .where(ArticleDao.Properties.ArticleId.eq(articleId))
                    .unique();
        } else {
            String articleUrl = intent.getStringExtra(PARAM_ARTICLE_URL);

            if (TextUtils.isEmpty(articleUrl)) {
                Log.w(TAG, "loadArticle() neither articleId nor articleUrl is set");
                finish();
                return false;
            }

            Log.v(TAG, "loadArticle() looking up by url");

            article = new OperationsWorker(this).findArticleByUrl(articleUrl);

            if (article != null && article.getArticleId() != null) {
                discoveredArticleId = article.getArticleId();
            }
        }

        if (article == null) {
            Log.w(TAG, "loadArticle() article was not found");
            finish();
            return false;
        }

        return true;
    }

    private void loadAvailableTags() {
        availableTags = DbConnection.getSession().getTagDao().queryBuilder()
                .orderAsc(TagDao.Properties.Label).list();

        sortTagListByLabel(availableTags);
    }

    private void reload(boolean resetRemoved) {
        if (!loadArticle()) {
            Log.w(TAG, "reload() couldn't reload article");
            finish();
            return;
        }

        article.resetTags();

        reloadTags(resetRemoved ? article.getTags() : Collections.emptyList());
    }

    private void reloadTags(List<Tag> baseTags) {
        List<String> copy = Arrays.asList(tagListToStringArray(currentTags));

        loadAvailableTags();

        List<Tag> newList = new ArrayList<>(baseTags);

        newList = addUniqueTags(newList, toTags(copy)).second;

        updateCurrentTagList(newList);

        textChanged(currentText);
    }

    private void save() {
        Log.d(TAG, "save() started");

        OperationsHelper.setArticleTags(this, article, new ArrayList<>(currentTags), this::finish);
    }

    private void textChanged(String text) {
        Log.d(TAG, "textChanged() text: " + text);

        currentText = text;

        updateSuggestedTagList();
    }

    private void updateSuggestedTagList() {
        if (TextUtils.isEmpty(currentText) && suggestedTags.isEmpty()) return;

        List<Tag> filteredList;
        if (TextUtils.isEmpty(currentText)) {
            filteredList = new ArrayList<>();
        } else {
            filteredList = filterTagList(currentText, availableTags, currentTags);
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new TagListFragment.TagListDiffCallback(suggestedTags, filteredList));

        suggestedTags.clear();
        suggestedTags.addAll(filteredList);

        diffResult.dispatchUpdatesTo(suggestedTagsAdapter);

        if (suggestedTagsView != null) {
            suggestedTagsView.setVisibility(suggestedTags.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void updateCurrentTagList(List<Tag> newList) {
        if (currentTags.isEmpty() && newList.isEmpty()) return;

        sortTagListByLabel(newList);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new TagListFragment.TagListDiffCallback(currentTags, newList));

        currentTags.clear();
        currentTags.addAll(newList);

        diffResult.dispatchUpdatesTo(currentTagsAdapter);

        int visibility = currentTags.isEmpty() ? View.GONE : View.VISIBLE;

        if (currentTagsLabel != null) currentTagsLabel.setVisibility(visibility);
        if (currentTagsView != null) currentTagsView.setVisibility(visibility);

        updateSuggestedTagList();
    }

    public void addButtonPressed(View view) {
        Log.d(TAG, "addButtonPressed() started; currentText: " + currentText);

        if (TextUtils.isEmpty(currentText)) return;

        addTagsToCurrentList(toTags(Arrays.asList(currentText.split(","))));

        setEditText("");
    }

    private void addTagsToCurrentList(List<Tag> tagsToAdd) {
        Pair<Boolean, List<Tag>> result = addUniqueTags(currentTags, tagsToAdd);
        if (result.first) updateCurrentTagList(result.second);
    }

    private Pair<Boolean, List<Tag>> addUniqueTags(List<Tag> srcList, List<Tag> tagsToAdd) {
        for (ListIterator<Tag> it = tagsToAdd.listIterator(); it.hasNext(); ) {
            if (containsByLabel(it.next(), srcList)) it.remove();
        }

        if (tagsToAdd.isEmpty()) {
            return new Pair<>(false, srcList);
        }

        List<Tag> newTags = new ArrayList<>(srcList.size() + tagsToAdd.size());
        newTags.addAll(srcList);
        newTags.addAll(tagsToAdd);

        return new Pair<>(true, newTags);
    }

    private List<Tag> toTags(Collection<String> labels) {
        List<Tag> result = new ArrayList<>();

        for (String label : labels) {
            label = label.trim();

            if (TextUtils.isEmpty(label)) continue;

            Tag tag = findTagByLabel(label, availableTags);

            if (tag == null) {
                tag = new Tag(null, null, label);
            }

            result.add(tag);
        }

        return result;
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
        if (editText != null) {
            editText.setText(text);
        }
    }

    private static String[] tagListToStringArray(List<Tag> list) {
        String[] labels = new String[list.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = list.get(i).getLabel();
        }
        return labels;
    }

    private static boolean containsByLabel(Tag tag, Collection<Tag> tags) {
        return findTagByLabel(tag.getLabel(), tags) != null;
    }

    private static Tag findTagByLabel(String label, Collection<Tag> list) {
        if (TextUtils.isEmpty(label)) return null;

        for (Tag tag : list) {
            if (equalLabels(label, tag.getLabel())) return tag;
        }

        return null;
    }

    private static List<Tag> filterTagList(String label, List<Tag> src, List<Tag> excludeList) {
        if (TextUtils.isEmpty(label) || src.isEmpty()) {
            return new ArrayList<>(src);
        }

        label = label.toLowerCase(Locale.getDefault());

        List<Tag> result = new ArrayList<>();
        for (Tag tag : src) {
            String tagLabel = tag.getLabel();
            if (tagLabel != null && tagLabel.toLowerCase(Locale.getDefault()).contains(label)) {
                if (excludeList != null && excludeList.contains(tag)) continue;

                result.add(tag);
            }
        }
        return result;
    }

    private static boolean equalLabels(String s1, String s2) {
        if (s1 != null) s1 = s1.toLowerCase(Locale.getDefault());
        if (s2 != null) s2 = s2.toLowerCase(Locale.getDefault());

        return TextUtils.equals(s1, s2);
    }

}
