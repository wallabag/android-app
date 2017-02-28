package fr.gaulupeau.apps.Poche.ui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;

import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_ARCHIVED;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_FAVORITES;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_UNREAD;

class ArticleListsPagerAdapter extends CachingPagerAdapter {

    // TODO: private; configurable
    static int[] PAGES = {
            LIST_TYPE_FAVORITES,
            LIST_TYPE_UNREAD,
            LIST_TYPE_ARCHIVED
    };

    private String tag;

    ArticleListsPagerAdapter(FragmentManager fm) {
        this(fm, null);
    }

    ArticleListsPagerAdapter(FragmentManager fm, String tag) {
        super(fm, PAGES.length);
        this.tag = tag;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch(PAGES[position]) {
            case LIST_TYPE_FAVORITES:
                return App.getInstance().getString(R.string.feedName_favorites);
            case LIST_TYPE_ARCHIVED:
                return App.getInstance().getString(R.string.feedName_archived);
            default:
                return App.getInstance().getString(R.string.feedName_unread);
        }
    }

    static int positionByFeedType(FeedsChangedEvent.FeedType feedType) {
        if(feedType == null) return -1;

        int listType;
        switch(feedType) {
            case FAVORITE:
                listType = LIST_TYPE_FAVORITES;
                break;
            case ARCHIVE:
                listType = LIST_TYPE_ARCHIVED;
                break;
            default:
                listType = LIST_TYPE_UNREAD;
                break;
        }

        for(int i = 0; i < PAGES.length; i++) {
            if(listType == PAGES[i]) return i;
        }

        return -1;
    }

    @Override
    public Fragment instantiateFragment(int position) {
        return ArticleListFragment.newInstance(PAGES[position], tag);
    }

    @Override
    ArticleListFragment getCachedFragment(int position) {
        Fragment f = super.getCachedFragment(position);
        return f instanceof ArticleListFragment ? (ArticleListFragment)f : null;
    }

}
