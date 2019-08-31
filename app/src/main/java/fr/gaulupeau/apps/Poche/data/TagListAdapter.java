package fr.gaulupeau.apps.Poche.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

public class TagListAdapter extends RecyclerView.Adapter<TagListAdapter.ViewHolder> {

    private final @LayoutRes int itemLayoutResID;
    private final List<Tag> tags;
    private final OnItemClickListener listener;
    private final OnItemButtonClickListener buttonClickListener;

    public TagListAdapter(List<Tag> tags, OnItemClickListener listener) {
        this(R.layout.tag_list_item, tags, listener, null);
    }

    public TagListAdapter(@LayoutRes int itemLayoutResID,
                          List<Tag> tags, OnItemClickListener listener,
                          OnItemButtonClickListener buttonClickListener) {
        this.itemLayoutResID = itemLayoutResID;
        this.tags = tags;
        this.listener = listener;
        this.buttonClickListener = buttonClickListener;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(itemLayoutResID, parent, false);
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

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        OnItemClickListener listener;
        OnItemButtonClickListener buttonClickListener;

        Chip chip;
        MaterialButton button;

        public ViewHolder(View itemView, OnItemClickListener listener,
                          OnItemButtonClickListener buttonClickListener) {
            super(itemView);

            this.listener = listener;
            this.buttonClickListener = buttonClickListener;

            chip = itemView.findViewById(R.id.tag_label);

            itemView.setOnClickListener(this);

            button = itemView.findViewById(R.id.tag_remove_button);
            if(button != null) {
                button.setOnClickListener(this);
            }
        }

        public void bind(Tag tag) {
            chip.setText(tag.getLabel());
        }

        @Override
        public void onClick(View v) {
            if(button != null && v == button) {
                if(buttonClickListener != null) {
                    buttonClickListener.onItemButtonClick(getAdapterPosition());
                }
            } else {
                listener.onItemClick(getAdapterPosition());
            }
        }

    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemButtonClickListener {
        void onItemButtonClick(int position);
    }

}
