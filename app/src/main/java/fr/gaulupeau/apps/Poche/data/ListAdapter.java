package fr.gaulupeau.apps.Poche.data;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
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

        // null until the first bind(); see applyPendingAppearance()
        Boolean pendingAppearance;

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
        }

        void bind(Article article) {
            this.article = article;

            boolean pending = PendingArticles.isPending(article);
            applyPendingAppearance(pending);

            // deliberately not in applyPendingAppearance(): DefaultItemAnimator animates this and
            // forces it back to 1 when the animation ends, so a flip-guarded value would be lost
            // for good. setAlpha only invalidates, so re-applying it per bind() costs nothing.
            itemView.setAlpha(pending ? 0.6f : 1f);

            if (pending) {
                // a pending article has no title yet, so its URL is shown instead
                title.setText(article.getGivenUrl());
            } else {
                title.setText(article.getTitle());
                url.setText(article.getDomain());
                readingTime.setText(context.getString(R.string.listItem_estimatedReadingTime,
                        article.getEstimatedReadingTime(settings.getReadingSpeed())));
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
        }

        /**
         * Applies the parts of a row's appearance that depend on nothing but whether the article
         * is pending. Several of the setters below (notably {@code setMaxLines} and the compound
         * drawables) force a layout pass unconditionally, so the state is tracked per holder and
         * only re-applied when it actually flips.
         *
         * <p>Only properties that nothing else mutates belong here: anything the item animator or
         * the framework may change behind our back has to be re-applied on every bind instead.
         */
        private void applyPendingAppearance(boolean pending) {
            if (pendingAppearance != null && pendingAppearance == pending) return;
            pendingAppearance = pending;

            title.setTypeface(null, pending ? Typeface.ITALIC : Typeface.NORMAL);
            // a pending title is a raw URL, which can be arbitrarily long
            title.setMaxLines(pending ? 2 : Integer.MAX_VALUE);
            title.setEllipsize(pending ? TextUtils.TruncateAt.END : null);

            // a pending article can't be opened or acted on; note that clearing the listeners
            // would not disable interaction, since setting one force-enables the matching flag
            itemView.setClickable(!pending);
            itemView.setLongClickable(!pending);

            url.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    pending ? R.drawable.ic_pending_sync : 0, 0, 0, 0);
            if (pending) url.setText(R.string.listItem_pendingSync);

            readingTime.setVisibility(pending ? View.GONE : View.VISIBLE);
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
