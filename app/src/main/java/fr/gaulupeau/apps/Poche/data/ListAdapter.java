package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

import static fr.gaulupeau.apps.Poche.data.ListTypes.*;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {

    private Context context;
    private Settings settings;

    private List<Article> articles;
    private OnItemClickListener listener;
    private int listType = -1;

    public ListAdapter(Context context, Settings settings,
                       List<Article> articles, OnItemClickListener listener, int listType) {
        this.context = context;
        this.settings = settings;
        this.articles = articles;
        this.listener = listener;
        this.listType = listType;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bind(articles.get(position));
    }

    @Override
    public int getItemCount() {
        return articles.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        OnItemClickListener listener;
        TextView title;
        TextView url;
        LinearLayout tagContainer;
        ImageView favourite;
        ImageView read;
        TextView readingTime;
        boolean areTagsAdded;

        public ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            this.listener = listener;
            title = (TextView) itemView.findViewById(R.id.title);
            url = (TextView) itemView.findViewById(R.id.url);
            tagContainer = (LinearLayout) itemView.findViewById(R.id.tagContainer);
            areTagsAdded = false;
            favourite = (ImageView) itemView.findViewById(R.id.favourite);
            read = (ImageView) itemView.findViewById(R.id.read);
            readingTime = (TextView) itemView.findViewById(R.id.estimatedReadingTime);
            itemView.setOnClickListener(this);
        }

        public void bind(Article article) {
            title.setText(article.getTitle());
            url.setText(article.getDomain());
            if (areTagsAdded){
                tagContainer.removeAllViews();
                areTagsAdded = false;
            }
            List<String> tagsAdded = new ArrayList<>(); // do not add duplicate labels
            for (Iterator<Tag> tags = article.getTags().iterator(); tags.hasNext();) {
                Tag t = tags.next();
                if(!areTagsAdded && !tagsAdded.contains(t.getLabel())) {
                    // TODO apply current theme
                    TextView tagText = new TextView(context);
                    tagText.setText(t.getLabel());
                    tagText.setBackground(context.getResources().getDrawable(R.drawable.tag_shape));
                    tagText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    tagText.setTextColor(Color.WHITE);
                    tagText.setPadding(5, 2, 5, 2); // (left, top, right, bottom);
                    LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lllp.setMargins(0, 0, 5, 0);
                    tagText.setLayoutParams(lllp);
                    tagContainer.addView(tagText);
                    tagsAdded.add(t.getLabel());
                }
            }
            areTagsAdded = true;

            boolean showFavourite = false;
            boolean showRead = false;
            switch(listType) {
                case LIST_TYPE_UNREAD:
                case LIST_TYPE_ARCHIVED:
                    showFavourite = article.getFavorite();
                    break;

                case LIST_TYPE_FAVORITES:
                    showRead = article.getArchive();
                    break;

                default: // we don't actually use it right now
                    showFavourite = article.getFavorite();
                    showRead = article.getArchive();
                    break;
            }
            favourite.setVisibility(showFavourite ? View.VISIBLE : View.GONE);
            read.setVisibility(showRead ? View.VISIBLE : View.GONE);
            readingTime.setText(context.getString(R.string.listItem_estimatedReadingTime,
                    article.getEstimatedReadingTime(settings.getReadingSpeed())));
        }

        @Override
        public void onClick(View v) {
            listener.onItemClick(getAdapterPosition());
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }
}
