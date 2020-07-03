package fr.gaulupeau.apps.Poche.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

public class TagListAdapter extends RecyclerView.Adapter<TagListAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemButtonClickListener {
        void onItemButtonClick(int position);
    }

    private final List<Tag> tags;
    private final OnItemClickListener listener;
    private final OnItemButtonClickListener buttonClickListener;

    public TagListAdapter(List<Tag> tags, OnItemClickListener listener) {
        this(tags, listener, null);
    }

    public TagListAdapter(List<Tag> tags, OnItemClickListener listener,
                          OnItemButtonClickListener buttonClickListener) {
        this.tags = tags;
        this.listener = listener;
        this.buttonClickListener = buttonClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tag_list_removable_item, parent, false);
        return new ViewHolder(view, listener, buttonClickListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bind(tags.get(position));
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        OnItemClickListener listener;
        OnItemButtonClickListener buttonClickListener;

        TextView label;
        ImageButton button;

        ViewHolder(View itemView, OnItemClickListener listener,
                   OnItemButtonClickListener buttonClickListener) {
            super(itemView);

            this.listener = listener;
            this.buttonClickListener = buttonClickListener;

            label = itemView.findViewById(R.id.tag_label);

            itemView.setOnClickListener(this);

            button = itemView.findViewById(R.id.tag_remove_button);
            if (button != null) {
                if (buttonClickListener != null) {
                    button.setOnClickListener(this);
                } else {
                    button.setVisibility(View.GONE);
                }
            }
        }

        void bind(Tag tag) {
            label.setText(tag.getLabel());
        }

        @Override
        public void onClick(View v) {
            int index = getAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;

            if (button != null && v == button) {
                if (buttonClickListener != null) {
                    buttonClickListener.onItemButtonClick(index);
                }
            } else {
                listener.onItemClick(index);
            }
        }

    }

}
