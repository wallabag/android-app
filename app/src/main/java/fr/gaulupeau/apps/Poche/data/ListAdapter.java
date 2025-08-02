package fr.gaulupeau.apps.Poche.data;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.ui.ArticleActionsHelper;

import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_ARCHIVED;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_FAVORITES;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_UNREAD;

import com.bumptech.glide.Glide;

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
        ImageView previewPicture;
        TextView publishedAt;

        ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            this.listener = listener;

            title = itemView.findViewById(R.id.title);
            url = itemView.findViewById(R.id.url);
            favourite = itemView.findViewById(R.id.favourite);
            read = itemView.findViewById(R.id.read);
            readingTime = itemView.findViewById(R.id.estimatedReadingTime);
            previewPicture = itemView.findViewById(R.id.previewPicture);
            publishedAt= itemView.findViewById(R.id.publishedAt);


            itemView.setOnClickListener(this);
            itemView.setOnCreateContextMenuListener(this);
        }

        void bind(Article article) {
            this.article = article;

            title.setText(article.getTitle());
            url.setText(article.getDomain());

            previewPicture.setVisibility(View.GONE);
            if(settings.getArticleListShowPreviewPicture()) {
                // set picture height from settings
                if (settings.getArticleListPreviewPictureHeight() > 0) {
                    previewPicture.getLayoutParams().height = settings.getArticleListPreviewPictureHeight();
                }

                previewPicture.setVisibility(View.GONE);
                if (article.getPreviewPictureURL() != null && !article.getPreviewPictureURL().isEmpty()) {
                    previewPicture.setVisibility(View.VISIBLE);
                    Glide
                            .with(context)
                            .load(article.getPreviewPictureURL())
                            .centerCrop()
                            .into(previewPicture);
                }
            }

            // show author if available in url TextView
            if(settings.getArticleListShowAuthor()) {
                if (article.getAuthors() != null && !article.getAuthors().isEmpty()) {
                    url.setText(url.getText() + " (" + article.getAuthors() + ")");
                }
            }

            boolean showFavourite = false;
            boolean showRead = false;
            switch (listType) {
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

            // show date article in readingTime if active in settings
            publishedAt.setVisibility(View.GONE);
            if(settings.getArticleListShowPublishedAt()) {
                java.util.Date displayDate=null;
                if (article.getPublishedAt() != null) {
                    displayDate=article.getPublishedAt();
                }
                else if(article.getCreationDate()!=null){
                    displayDate=article.getCreationDate();
                }

                if(displayDate!=null) {
                    publishedAt.setVisibility(View.VISIBLE);
                    var stringBuilder = new StringBuilder();
                    stringBuilder.append(android.text.format.DateFormat.getDateFormat(context).format(displayDate));
                    stringBuilder.append(' ');
                    stringBuilder.append(android.text.format.DateFormat.getTimeFormat(context).format(displayDate));
                    publishedAt.setText(stringBuilder.toString());
                }
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
