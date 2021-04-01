package fr.gaulupeau.apps.Poche.utils;

import android.text.TextUtils;

import androidx.core.text.HtmlCompat;

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

    public static String unescapeHtml(String s) {
        if (s == null) return null;

        return HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
    }

}
