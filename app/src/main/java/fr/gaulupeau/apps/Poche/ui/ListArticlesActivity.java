package fr.gaulupeau.apps.Poche.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.dao.query.LazyList;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.ListAdapter;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;

public class ListArticlesActivity extends BaseActionBarActivity implements ListAdapter.OnItemClickListener {

    private RecyclerView readList;
    private boolean showAll = false;

	private DaoSession mSession;
    private List<Article> mArticles;
    private ArticleDao mArticleDao;
    private ListAdapter mAdapter;

    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list);

        readList = (RecyclerView) findViewById(R.id.article_list);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        readList.setLayoutManager(layoutManager);


		mSession = DbConnection.getSession();
        mArticleDao = mSession.getArticleDao();

        mArticles = new ArrayList<>();

        mAdapter = new ListAdapter(mArticles, this);
        readList.setAdapter(mAdapter);
	}

    @Override
    protected void onResume() {
        super.onResume();
        updateList();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_list, menu);
		return true;
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menuShowAll).setTitle(getString(showAll ? R.string.menuShowUnread : R.string.menuShowAll));
        return true;
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuShowAll:
                showAll = !showAll;
				updateList();
				return true;
			case R.id.menuWipeDb:
                mSession.getArticleDao().deleteAll();
				updateList();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

    private void updateList() {
        LazyList<Article> articles = mArticleDao.queryBuilder()
                .where(ArticleDao.Properties.Archive.notEq(true))
                .orderDesc(ArticleDao.Properties.UpdateDate)
                .limit(50)
                .listLazy();
        mArticles.clear();
        mArticles.addAll(articles);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(int position) {
        Article article = mArticles.get(position);
        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.putExtra(ReadArticleActivity.EXTRA_ID, article.getId());
        startActivity(intent);
    }

}
