package fr.gaulupeau.apps.Poche.data;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

public class TagListAdapter extends RecyclerView.Adapter<TagListAdapter.ViewHolder> {

    private List<Tag> tags;
    private OnItemClickListener listener;

    public TagListAdapter(List<Tag> tags, OnItemClickListener listener) {
        this.tags = tags;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tag_list_item, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bind(tags.get(position));
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        OnItemClickListener listener;

        TextView label;

        public ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);

            this.listener = listener;

            label = (TextView)itemView.findViewById(R.id.tag_label);

            itemView.setOnClickListener(this);
        }

        public void bind(Tag tag) {
            label.setText(tag.getLabel());
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
