package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_ARCHIVED;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_FAVORITES;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_UNREAD;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private Context context;
    private Settings settings;

    private List<Article> articles;
    private OnItemClickListener listener;
    private int listType;

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

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        OnItemClickListener listener;
        TextView title;
        TextView url;
        ImageView favourite;
        ImageView read;
        TextView readingTime;

        ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            this.listener = listener;
            title = itemView.findViewById(R.id.title);
            url = itemView.findViewById(R.id.url);
            favourite = itemView.findViewById(R.id.favourite);
            read = itemView.findViewById(R.id.read);
            readingTime = itemView.findViewById(R.id.estimatedReadingTime);
            itemView.setOnClickListener(this);
        }

        void bind(Article article) {
            title.setText(article.getTitle());
            url.setText(article.getDomain());

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
        }

        @Override
        public void onClick(View v) {
            listener.onItemClick(getAdapterPosition());
        }
    }

}
