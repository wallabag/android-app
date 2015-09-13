package fr.gaulupeau.apps.Poche;

import android.app.Activity;
import android.widget.ImageView;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import fr.gaulupeau.apps.InThePoche.R;

public class Helpers {

	public static final String PREFS_NAME = "InThePoche";
	public final static String zeroUpdate = "2011-01-01 00:00:00";

	public static String InputStreamtoString(InputStream is) {
		String s = "", line = "";
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));
		try {
			for (; ; rd.readLine()) {
				if ((line = rd.readLine()) != null) {
					s += line;
				} else {
					break;
				}
			}
		} catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return s;
	}

	public static String getInputStreamFromUrl(String url) {
		InputStream content;
		String res = "";
		try {
			HttpGet httpGet = new HttpGet(url);
			HttpClient httpclient = new DefaultHttpClient();
			// Execute HTTP Get Request
			HttpResponse response = httpclient.execute(httpGet);
			content = response.getEntity().getContent();
			res = InputStreamtoString(content);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return res;
	}

    public void setNightViewTheme(boolean nightmode, Activity a) {
        if (nightmode) {
            a.setTheme(R.style.app_theme_dark);
        }
    }

    public void setNightViewIcon(boolean nightmode, ImageView imageView) {
        if (nightmode) {
            //invert colors
            imageView.setImageResource(R.drawable.welcome_night);
        }
    }
}
