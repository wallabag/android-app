package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import java.util.Map;
import java.util.WeakHashMap;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;

public class Themes {

    private static Theme theme;

    private static Map<Activity, Theme> appliedThemes = new WeakHashMap<>();

    static {
        init();
    }

    public static void init() {
        Themes.theme = App.getInstance().getSettings().getTheme();
    }

    public static Theme getCurrentTheme() {
        return theme;
    }

    public static void applyTheme(Activity activity) {
        applyTheme(activity, true);
    }

    public static void applyTheme(Activity activity, boolean actionBar) {
        activity.setTheme(actionBar ? theme.getResId() : theme.getNoActionBarResId());
        appliedThemes.put(activity, theme);
    }

    public static void applyProxyTheme(Activity activity) {
        activity.setTheme(theme.getProxyResId());
        appliedThemes.put(activity, theme);
    }

    public static void checkTheme(Activity activity) {
        Theme appliedTheme = appliedThemes.get(activity);
        if(appliedTheme != theme) activity.recreate();
    }

    public enum Theme {
        LIGHT(
                R.string.themeName_light,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight
        ),

        LIGHT_CONTRAST(
                R.string.themeName_light_contrast,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight
        ),

        E_INK(
                R.string.themeName_eink,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight
        ),

        DARK(
                R.string.themeName_dark,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight
        ),

        DARK_CONTRAST(
                R.string.themeName_dark_contrast,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight
        ),

        SOLARIZED(
                R.string.themeName_solarized,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight,
                R.style.MyTheme_DayNight
        );

        private int nameId;
        private int resId;
        private int noActionBarResId;
        private int proxyResId;

        Theme(@StringRes int nameId, @StyleRes int resId,
              @StyleRes int noActionBarResId, @StyleRes int dialogResId) {
            this.nameId = nameId;
            this.resId = resId;
            this.noActionBarResId = noActionBarResId;
            this.proxyResId = dialogResId;
        }

        public @StringRes int getNameId() {
            return nameId;
        }

        public @StyleRes int getResId() {
            return resId;
        }

        public @StyleRes int getNoActionBarResId() {
            return noActionBarResId;
        }

        public @StyleRes int getProxyResId() {
            return proxyResId;
        }

        public boolean isDark() {
            return this == Themes.Theme.DARK || this == Themes.Theme.DARK_CONTRAST;
        }

    }

}
