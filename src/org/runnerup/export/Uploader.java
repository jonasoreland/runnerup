/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
