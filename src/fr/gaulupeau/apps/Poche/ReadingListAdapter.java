package fr.gaulupeau.apps.Poche;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class ReadingListAdapter extends BaseAdapter {
    private Context context;
    private List<Article> listArticles;

    public ReadingListAdapter(Context context, List<Article> listArticles) {
            this.context = context;
            this.listArticles = listArticles;
    }


    public int getCount() {
            return listArticles.size();
    }

    public Object getItem(int position) {
            return listArticles.get(position);
    }

    public long getItemId(int position) {
            return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
            Article entry = listArticles.get(position);
            if (convertView == null) {
                    LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = inflater.inflate(R.layout.article_list, null);
            }
            TextView tvTitle = (TextView) convertView.findViewById(R.id.listitem_titre);
            Log.e("title", entry.title);
            tvTitle.setText(entry.title);

            return convertView;
    }




}
