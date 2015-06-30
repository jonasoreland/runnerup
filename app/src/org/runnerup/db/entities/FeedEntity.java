/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.db.entities;

import android.annotation.TargetApi;
import android.database.Cursor;
import android.os.Build;
import android.util.Log;

import org.runnerup.common.util.Constants;

import java.util.ArrayList;

/**
 * Content values wrapper for the {@code feed} table.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class FeedEntity extends AbstractEntity {

    public FeedEntity() {
        super();
    }

    public FeedEntity(Cursor c) {
        super();
        try {
            toContentValues(c);
        } catch (Exception e) {
            Log.e(Constants.LOG, e.getMessage());
        }
    }



    /**
     * Id of the account the feed belongs to
     */
    public void setAccountID(Long value) {
        values().put(Constants.DB.FEED.ACCOUNT_ID, value);
    }

    public Long getAccountID() {
        if (values().containsKey(Constants.DB.FEED.ACCOUNT_ID)) {
            return values().getAsLong(Constants.DB.FEED.ACCOUNT_ID);
        }
        return null;
    }

    /**
     * External Id the feed belongs to
     */
    public void setExternalID(Long value) {
        values().put(Constants.DB.FEED.EXTERNAL_ID, value);
    }

    public Long getExternalID() {
        if (values().containsKey(Constants.DB.FEED.EXTERNAL_ID)) {
            return values().getAsLong(Constants.DB.FEED.EXTERNAL_ID);
        }
        return null;
    }

    public void setType(Integer value) {
        values().put(Constants.DB.FEED.FEED_TYPE, value);
    }

    public Integer getType() {
        if (values().containsKey(Constants.DB.FEED.FEED_TYPE)) {
            return values().getAsInteger(Constants.DB.FEED.FEED_TYPE);
        }
        return null;
    }

    public void setSubtype(Integer value) {
        values().put(Constants.DB.FEED.FEED_SUBTYPE, value);
    }

    public Integer getSubtype() {
        if (values().containsKey(Constants.DB.FEED.FEED_SUBTYPE)) {
            return values().getAsInteger(Constants.DB.FEED.FEED_SUBTYPE);
        }
        return null;
    }

    public void setTypeString(Integer value) {
        values().put(Constants.DB.FEED.FEED_TYPE_STRING, value);
    }

    public Integer getTypeString() {
        if (values().containsKey(Constants.DB.FEED.FEED_TYPE_STRING)) {
            return values().getAsInteger(Constants.DB.FEED.FEED_TYPE_STRING);
        }
        return null;
    }
    public void setStartTime(Integer value) {
        values().put(Constants.DB.FEED.START_TIME, value);
    }

    public Integer getStartTime() {
        if (values().containsKey(Constants.DB.FEED.START_TIME)) {
            return values().getAsInteger(Constants.DB.FEED.START_TIME);
        }
        return null;
    }
    public void setDuration(Integer value) {
        values().put(Constants.DB.FEED.DURATION, value);
    }

    public Integer getDuration() {
        if (values().containsKey(Constants.DB.FEED.DURATION)) {
            return values().getAsInteger(Constants.DB.FEED.DURATION);
        }
        return null;
    }

    public void setDistance(Float value) {
        values().put(Constants.DB.FEED.DISTANCE, value);
    }

    public Float getDistance() {
        if (values().containsKey(Constants.DB.FEED.DISTANCE)) {
            return values().getAsFloat(Constants.DB.FEED.DISTANCE);
        }
        return null;
    }

    public void setUserID(String value) {
        values().put(Constants.DB.FEED.USER_ID, value);
    }

    public String getUserID() {
        if (values().containsKey(Constants.DB.FEED.USER_ID)) {
            return values().getAsString(Constants.DB.FEED.USER_ID);
        }
        return null;
    }
    public void setUserFirstName(String value) {
        values().put(Constants.DB.FEED.USER_FIRST_NAME, value);
    }

    public String getUserFirstName() {
        if (values().containsKey(Constants.DB.FEED.USER_FIRST_NAME)) {
            return values().getAsString(Constants.DB.FEED.USER_FIRST_NAME);
        }
        return null;
    }
    public void setUserLastName(String value) {
        values().put(Constants.DB.FEED.USER_LAST_NAME, value);
    }

    public String getUserLastName() {
        if (values().containsKey(Constants.DB.FEED.USER_LAST_NAME)) {
            return values().getAsString(Constants.DB.FEED.USER_LAST_NAME);
        }
        return null;
    }

    public void setUserImageURL(String value) {
        values().put(Constants.DB.FEED.USER_IMAGE_URL, value);
    }

    public String getUserImageURL() {
        if (values().containsKey(Constants.DB.FEED.USER_IMAGE_URL)) {
            return values().getAsString(Constants.DB.FEED.USER_IMAGE_URL);
        }
        return null;
    }
    public void setNotes(String value) {
        values().put(Constants.DB.FEED.NOTES, value);
    }

    public String getNotes() {
        if (values().containsKey(Constants.DB.FEED.NOTES)) {
            return values().getAsString(Constants.DB.FEED.NOTES);
        }
        return null;
    }
    public void setComments(String value) {
        values().put(Constants.DB.FEED.COMMENTS, value);
    }

    public String getComments() {
        if (values().containsKey(Constants.DB.FEED.COMMENTS)) {
            return values().getAsString(Constants.DB.FEED.COMMENTS);
        }
        return null;
    }
    public void setURL(String value) {
        values().put(Constants.DB.FEED.URL, value);
    }

    public String getURL() {
        if (values().containsKey(Constants.DB.FEED.URL)) {
            return values().getAsString(Constants.DB.FEED.URL);
        }
        return null;
    }
    public void setFlags(String value) {
        values().put(Constants.DB.FEED.FLAGS, value);
    }

    public String getFlags() {
        if (values().containsKey(Constants.DB.FEED.FLAGS)) {
            return values().getAsString(Constants.DB.FEED.FLAGS);
        }
        return null;
    }

    @Override
    public ArrayList<String> getValidColumns() {
        ArrayList<String> columns = new ArrayList<String>();
        columns.add(Constants.DB.PRIMARY_KEY);
        columns.add(Constants.DB.FEED.ACCOUNT_ID);
        columns.add(Constants.DB.FEED.EXTERNAL_ID);
        columns.add(Constants.DB.FEED.FEED_TYPE);
        columns.add(Constants.DB.FEED.FEED_SUBTYPE);
        columns.add(Constants.DB.FEED.FEED_TYPE_STRING);
        columns.add(Constants.DB.FEED.START_TIME);
        columns.add(Constants.DB.FEED.DURATION);
        columns.add(Constants.DB.FEED.DISTANCE);
        columns.add(Constants.DB.FEED.USER_ID);
        columns.add(Constants.DB.FEED.USER_FIRST_NAME);
        columns.add(Constants.DB.FEED.USER_LAST_NAME);
        columns.add(Constants.DB.FEED.USER_IMAGE_URL);
        columns.add(Constants.DB.FEED.NOTES);
        columns.add(Constants.DB.FEED.COMMENTS);
        columns.add(Constants.DB.FEED.URL);
        columns.add(Constants.DB.FEED.FLAGS);
        return columns;
    }

    @Override
    public String getTableName() {
        return Constants.DB.FEED.TABLE;
    }

    @Override
    protected String getNullColumnHack() {
        return null;
    }
}
