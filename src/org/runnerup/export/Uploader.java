package org.runnerup.export;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

public interface Uploader {

	enum Status {
		OK, CANCEL, ERROR, INCORRECT_USAGE;

		public Exception ex = null;
	};

	enum AuthMethod {
		OAUTH2, POST
	};

	/**
	 * 
	 * @return
	 */
	public long getId();

	/**
	 * 
	 * @return name of this uploader
	 */
	public String getName();

	/**
	 * 
	 * @return
	 */
	public AuthMethod getAuthMethod();

	/**
	 * Init uploader
	 * 
	 * @param config
	 */
	public void init(ContentValues config);

	/**
	 * Is uploader configured
	 */
	public boolean isConfigured();

	/**
	 * Configure (i.e password, oauth...)
	 */
	public Intent configure(Activity activity);

	/**
	 * Reset configuration (i.e password, oauth-token...)
	 */
	public void reset();

	/**
	 * Login
	 * 
	 * @param callback
	 * @return true ok false cancel/fail
	 */
	public Status login();

	/**
	 * 
	 * @param mID
	 * @param onUploadCallback
	 */
	public Status upload(SQLiteDatabase db, long mID);

	/**
	 * logout
	 * 
	 * @return
	 */
	public void logout();
}
