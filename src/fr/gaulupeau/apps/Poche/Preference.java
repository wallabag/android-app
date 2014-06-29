package fr.gaulupeau.apps.Poche;

import android.content.Context;
import android.content.SharedPreferences;

public class Preference {
	private static final String PREFS_NAME = "InThePoche";
	private static final String API_USERNAME = "APIUsername";
	private static final String POCHE_URL = "pocheUrl";
	private static final String API_TOKEN = "APIToken";
	private static final String PREVIOUS_UPDATE = "previous_update";

	private String apiUsername;
	private String apiToken;
	private String pocheUrl;
	private SharedPreferences settings;
	private String previousUpdate;

	public Preference(Context context) {
		settings = context.getSharedPreferences(PREFS_NAME, 0);
		pocheUrl = settings.getString(POCHE_URL, "http://");
		apiUsername = settings.getString(API_USERNAME, "");
		apiToken = settings.getString(API_TOKEN, "");
	}

	public String getApiUsername() {
		return apiUsername;
	}

	public void setApiUsername(String apiUsername) {
		this.apiUsername = apiUsername;
	}

	public String getApiToken() {
		return apiToken;
	}

	public void setApiToken(String apiToken) {
		this.apiToken = apiToken;
	}

	public String getPocheUrl() {
		return pocheUrl;
	}

	public void setPocheUrl(String pocheUrl) {
		this.pocheUrl = pocheUrl;
	}

	public void save() {
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(POCHE_URL, pocheUrl);
		editor.putString(API_USERNAME, apiUsername);
		editor.putString(API_TOKEN, apiToken);
		editor.putString(PREVIOUS_UPDATE, previousUpdate);

		editor.commit();
	}

	public void setPreviousUpdate(String previousUpdate) {
		this.previousUpdate = previousUpdate;
	}

	public String getPreviousUpdate() {
		return previousUpdate;
	}
}
