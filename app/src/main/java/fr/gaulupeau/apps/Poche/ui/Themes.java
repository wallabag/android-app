package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import java.util.Map;
import java.util.WeakHashMap;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import android.content.Context;
import android.content.res.Configuration;

public class Themes {

    private static Theme theme;

    private static Map<Activity, Theme> appliedThemes = new WeakHashMap<>();

    static {
        init();
    }

    public static void init() {
        Themes.theme = App.getSettings().getTheme();
    }

    public static Theme getCurrentTheme() {
        return theme;
    }

    public static Theme getResolvedTheme(Context context) {
        if (App.getSettings().isAutoThemeEnabled()) {
            int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                return App.getSettings().getAutoDarkTheme();
            } else {
                return App.getSettings().getAutoLightTheme();
            }
        }
        return theme;
    }

    public static void applyTheme(Activity activity) {
        applyTheme(activity, true);
    }

    public static void applyTheme(Activity activity, boolean actionBar) {
        Theme resolvedTheme = getResolvedTheme(activity);
        activity.setTheme(actionBar ? resolvedTheme.getResId() : resolvedTheme.getNoActionBarResId());
        appliedThemes.put(activity, resolvedTheme);
    }

    public static void applyDialogTheme(Activity activity) {
        Theme resolvedTheme = getResolvedTheme(activity);
        activity.setTheme(resolvedTheme.getDialogResId());
        appliedThemes.put(activity, resolvedTheme);
    }

    public static void checkTheme(Activity activity) {
        Theme appliedTheme = appliedThemes.get(activity);
        Theme resolvedTheme = getResolvedTheme(activity);
        if(appliedTheme != resolvedTheme) activity.recreate();
    }

    public enum Theme {
        LIGHT(
                R.string.themeName_light,
                R.style.LightTheme,
                R.style.LightTheme_NoActionBar,
                R.style.DialogTheme,
                false
        ),

        LIGHT_CONTRAST(
                R.string.themeName_light_contrast,
                R.style.LightThemeContrast,
                R.style.LightThemeContrast_NoActionBar,
                R.style.DialogTheme,
                false
        ),

        E_INK(
                R.string.themeName_eink,
                R.style.LightThemeContrast,
                R.style.LightThemeContrast_NoActionBar,
                R.style.DialogTheme,
                false
        ),

        DARK(
                R.string.themeName_dark,
                R.style.DarkTheme,
                R.style.DarkTheme_NoActionBar,
                R.style.DialogThemeDark,
                true
        ),

        DARK_CONTRAST(
                R.string.themeName_dark_contrast,
                R.style.DarkThemeContrast,
                R.style.DarkThemeContrast_NoActionBar,
                R.style.DialogThemeDark,
                true
        ),

        SOLARIZED(
                R.string.themeName_solarized,
                R.style.SolarizedTheme,
                R.style.SolarizedTheme_NoActionBar,
                R.style.DialogTheme,
                true
        );

        private int nameId;
        private int resId;
        private int noActionBarResId;
        private int dialogResId;
        private boolean isDark;

        Theme(@StringRes int nameId, @StyleRes int resId,
              @StyleRes int noActionBarResId, @StyleRes int dialogResId, boolean isDark) {
            this.nameId = nameId;
            this.resId = resId;
            this.noActionBarResId = noActionBarResId;
            this.dialogResId = dialogResId;
            this.isDark = isDark;
        }

        public boolean isDark() {
            return isDark;
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

        public @StyleRes int getDialogResId() {
            return dialogResId;
        }

    }

}
