package fr.gaulupeau.apps.Poche.ui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.Log;
import android.view.ViewGroup;

abstract class CachingPagerAdapter extends FragmentPagerAdapter {

    private static final String TAG = CachingPagerAdapter.class.getSimpleName();

    private final Fragment[] fragments;

    CachingPagerAdapter(FragmentManager fm, int size) {
        super(fm);
        fragments = new Fragment[size];
    }

    @Override
    public Fragment getItem(int position) {
        Log.d(TAG, "getItem() position: " + position);
        return getFragment(position);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Log.d(TAG, "instantiateItem() position: " + position);

        Object object = super.instantiateItem(container, position);

        if(object instanceof Fragment) {
            fragments[position] = (Fragment)object;
        }

        return object;
    }

    @Override
    public int getCount() {
        return fragments.length;
    }

    Fragment getCachedFragment(int position) {
        return fragments[position];
    }

    public abstract Fragment instantiateFragment(int position);

    private Fragment getFragment(int position) {
        Log.d(TAG, "getFragment " + position);

        Fragment f = getCachedFragment(position);
        if(f == null) {
            Log.d(TAG, "creating new instance");
            f = instantiateFragment(position);
        }

        return f;
    }

}
