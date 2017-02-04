package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;

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
        activity.setTheme(theme.getResId());
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
                R.style.LightTheme,
                R.style.ProxyTheme
        ),

        LIGHT_CONTRAST(
                R.string.themeName_light_contrast,
                R.style.LightThemeContrast,
                R.style.ProxyTheme
        ),

        DARK(
                R.string.themeName_dark,
                R.style.DarkTheme,
                R.style.ProxyThemeDark
        ),

        DARK_CONTRAST(
                R.string.themeName_dark_contrast,
                R.style.DarkThemeContrast,
                R.style.ProxyThemeDark
        ),

        SOLARIZED(
                R.string.themeName_solarized,
                R.style.SolarizedTheme,
                R.style.ProxyTheme
        );

        private int nameId;
        private int resId;
        private int proxyResId;

        Theme(int nameId, int resId, int dialogResId) {
            this.nameId = nameId;
            this.resId = resId;
            this.proxyResId = dialogResId;
        }

        public int getNameId() {
            return nameId;
        }

        public int getResId() {
            return resId;
        }

        public int getProxyResId() {
            return proxyResId;
        }

    }

}
