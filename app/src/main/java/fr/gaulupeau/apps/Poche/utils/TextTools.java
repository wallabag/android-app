package fr.gaulupeau.apps.Poche.utils;

import android.os.Build;
import android.text.Html;
import android.text.TextUtils;

import androidx.core.text.TextUtilsCompat;

public class TextTools {

    /**
     * Returns true if both arguments are equal or empty.
     * {@code null} and empty sequences are considered equal.
     *
     * @param s1 first char sequence
     * @param s2 second char sequence
     * @return true if arguments are considered equal
     */
    public static boolean equalOrEmpty(CharSequence s1, CharSequence s2) {
        return (TextUtils.isEmpty(s1) && TextUtils.isEmpty(s2))
                || TextUtils.equals(s1, s2);
    }

    public static String escapeHtml(String s) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return Html.escapeHtml(s);
        } else {
            return TextUtilsCompat.htmlEncode(s); // not sure
        }
    }

    public static String unescapeHtml(String s) {
        if (s == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            return Html.fromHtml(s).toString();
        }
    }

}
