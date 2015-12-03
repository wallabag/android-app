package fr.gaulupeau.apps.Poche.data;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.URL;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.entity.Article;

import static fr.gaulupeau.apps.Poche.data.ListTypes.*;

/**
 * @author Victor Häggqvist
 * @since 10/19/15
 */
public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {

    private List<Article> articles;
    private OnItemClickListener listener;
    private int listType = -1;

    public ListAdapter(List<Article> articles, OnItemClickListener listener) {
        this.articles = articles;
        this.listener = listener;
    }

    public ListAdapter(List<Article> articles, OnItemClickListener listener, int listType) {
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
        ImageView favourite;
        ImageView read;

        public ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            this.listener = listener;
            title = (TextView) itemView.findViewById(R.id.title);
            url = (TextView) itemView.findViewById(R.id.url);
            favourite = (ImageView) itemView.findViewById(R.id.favourite);
            read = (ImageView) itemView.findViewById(R.id.read);
            itemView.setOnClickListener(this);
        }

        public void bind(Article article) {
            String urlText = article.getUrl();
            try {
                URL url = new URL(urlText);
                urlText = url.getHost();
            } catch (Exception ignored) {}

            title.setText(article.getTitle());
            url.setText(urlText);

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
