package fr.gaulupeau.apps.Poche.data;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.ui.ArticleActionsHelper;

import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_ARCHIVED;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_FAVORITES;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_UNREAD;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private Context context;
    private Settings settings;
    private ArticleActionsHelper articleActionsHelper = new ArticleActionsHelper();

    private List<Article> articles;
    private OnItemClickListener listener;
    private int listType;

    private Article articleWithContextMenu;

    public ListAdapter(Context context, Settings settings,
                       List<Article> articles, OnItemClickListener listener, int listType) {
        this.context = context;
        this.settings = settings;
        this.articles = articles;
        this.listener = listener;
        this.listType = listType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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

    public boolean handleContextItemSelected(Activity activity, MenuItem item) {
        return articleWithContextMenu != null && articleActionsHelper
                .handleContextItemSelected(activity, articleWithContextMenu, item);
    }

    public class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnCreateContextMenuListener {

        OnItemClickListener listener;

        Article article;

        TextView title;
        TextView url;
        ImageView favourite;
        ImageView read;
        TextView readingTime;

        final Drawable defaultBackground;

        ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            this.listener = listener;

            title = itemView.findViewById(R.id.title);
            url = itemView.findViewById(R.id.url);
            favourite = itemView.findViewById(R.id.favourite);
            read = itemView.findViewById(R.id.read);
            readingTime = itemView.findViewById(R.id.estimatedReadingTime);

            itemView.setOnClickListener(this);
            itemView.setOnCreateContextMenuListener(this);

            // must be captured before any bind() clears it: once the background has been set to
            // null on a recycled view, the inflated ?attr/selectableItemBackground is unrecoverable
            defaultBackground = itemView.getBackground();
            url.setCompoundDrawablePadding(Math.round(
                    4 * itemView.getResources().getDisplayMetrics().density));
        }

        void bind(Article article) {
            this.article = article;

            boolean pending = PendingArticles.isPending(article);

            title.setText(PendingArticles.displayTitle(article));
            title.setTypeface(null, pending ? Typeface.ITALIC : Typeface.NORMAL);

            itemView.setAlpha(pending ? 0.6f : 1f);
            itemView.setBackground(pending ? null : defaultBackground);
            itemView.setOnClickListener(pending ? null : this);
            itemView.setClickable(!pending);
            itemView.setOnCreateContextMenuListener(pending ? null : this);

            if (pending) {
                url.setText(R.string.listItem_pendingSync);
                url.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_pending_sync, 0, 0, 0);
            } else {
                url.setText(article.getDomain());
                url.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            }

            boolean showFavourite = false;
            boolean showRead = false;
            switch (listType) {
                case LIST_TYPE_UNREAD:
                case LIST_TYPE_ARCHIVED:
                    showFavourite = Boolean.TRUE.equals(article.getFavorite());
                    break;

                case LIST_TYPE_FAVORITES:
                    showRead = Boolean.TRUE.equals(article.getArchive());
                    break;

                default: // we don't actually use it right now
                    showFavourite = Boolean.TRUE.equals(article.getFavorite());
                    showRead = Boolean.TRUE.equals(article.getArchive());
                    break;
            }
            favourite.setVisibility(showFavourite ? View.VISIBLE : View.GONE);
            read.setVisibility(showRead ? View.VISIBLE : View.GONE);

            if (pending) {
                readingTime.setVisibility(View.GONE);
            } else {
                readingTime.setVisibility(View.VISIBLE);
                readingTime.setText(context.getString(R.string.listItem_estimatedReadingTime,
                        article.getEstimatedReadingTime(settings.getReadingSpeed())));
            }
        }

        @Override
        public void onClick(View v) {
            int index = getAdapterPosition();
            if (index != RecyclerView.NO_POSITION) {
                listener.onItemClick(index);
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            articleWithContextMenu = article;

            if (article == null) return;

            new MenuInflater(context) // not sure about this
                    .inflate(R.menu.article_list_context_menu, menu);

            articleActionsHelper.initMenu(menu, article);
        }

    }

}
