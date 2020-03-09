package fr.gaulupeau.apps.Poche.data.dao.entities;

import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ArticleTagsDeleteItem extends ArticleIdItem<ArticleTagsDeleteItem> {

    private static final String DELIMITER = ",";

    ArticleTagsDeleteItem(QueueItem queueItem) {
        super(queueItem);
    }

    @Override
    ArticleTagsDeleteItem self() {
        return this;
    }

    public Set<String> getTagIds() {
        return new HashSet<>(Arrays.asList(queueItem.getExtra().split(DELIMITER)));
    }

    public ArticleTagsDeleteItem setTagIds(Iterable<String> tagIds) {
        queueItem.setExtra(TextUtils.join(DELIMITER, tagIds));
        return this;
    }

}
