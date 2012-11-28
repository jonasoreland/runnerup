package org.runnerup.export.oauth2client;

public interface OAuth2Server {
	/**
	 * Used as title when opening authorization dialog
	 * 
	 * @return
	 */
	public String getName();

	public String getClientId();

	public String getRedirectUri();

	public String getClientSecret();

	public String getAuthUrl();

	public String getTokenUrl();

	public String getRevokeUrl();
};
