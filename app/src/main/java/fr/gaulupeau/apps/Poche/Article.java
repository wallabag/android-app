package fr.gaulupeau.apps.Poche;

import java.net.URL;

public class Article {
	public String url;
	public String id;
	public String title;
	public String content;
	public String archive;

	private URL m_url = null;

	public Article(String url, String id, String title, String content, String archive) {
		super();
		this.url = url;
		this.id = id;
		this.title = title;
		this.content = content;
		this.archive = archive;

		try {
			this.m_url = new URL(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getHostOfUrl() {
		if (this.m_url != null) {
			return m_url.getHost();
		}
		return "";
	}
}
