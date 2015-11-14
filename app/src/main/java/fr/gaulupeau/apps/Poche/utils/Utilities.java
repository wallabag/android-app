package fr.gaulupeau.apps.Poche.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

/**
 * Created by tcit on 14/11/15.
 */
public class Utilities {
    public static Double estimatedTime(String content){
        String clearContent = Jsoup.clean(content, Whitelist.basicWithImages());
        Integer numberOfWords = clearContent.split("\\s+").length;
        return Math.floor(numberOfWords / 200);
    }
}
