package org.runnerup.content.db.provider.account;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.runnerup.content.db.provider.base.AbstractSelection;

/**
 * Selection for the {@code account} table.
 */
public class AccountSelection extends AbstractSelection<AccountSelection> {
    @Override
    protected Uri baseUri() {
        return AccountColumns.CONTENT_URI;
    }

    /**
     * Query the given content resolver using this selection.
     *
     * @param contentResolver The content resolver to query.
     * @param projection A list of which columns to return. Passing null will return all columns, which is inefficient.
     * @param sortOrder How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort
     *            order, which may be unordered.
     * @return A {@code AccountCursor} object, which is positioned before the first entry, or null.
     */
    public AccountCursor query(ContentResolver contentResolver, String[] projection, String sortOrder) {
        Cursor cursor = contentResolver.query(uri(), projection, sel(), args(), sortOrder);
        if (cursor == null) return null;
        return new AccountCursor(cursor);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null)}.
     */
    public AccountCursor query(ContentResolver contentResolver, String[] projection) {
        return query(contentResolver, projection, null);
    }

    /**
     * Equivalent of calling {@code query(contentResolver, projection, null, null)}.
     */
    public AccountCursor query(ContentResolver contentResolver) {
        return query(contentResolver, null, null);
    }


    public AccountSelection id(long... value) {
        addEquals("account." + AccountColumns._ID, toObjectArray(value));
        return this;
    }

    public AccountSelection name(String... value) {
        addEquals(AccountColumns.NAME, value);
        return this;
    }

    public AccountSelection nameNot(String... value) {
        addNotEquals(AccountColumns.NAME, value);
        return this;
    }

    public AccountSelection nameLike(String... value) {
        addLike(AccountColumns.NAME, value);
        return this;
    }

    public AccountSelection nameContains(String... value) {
        addContains(AccountColumns.NAME, value);
        return this;
    }

    public AccountSelection nameStartsWith(String... value) {
        addStartsWith(AccountColumns.NAME, value);
        return this;
    }

    public AccountSelection nameEndsWith(String... value) {
        addEndsWith(AccountColumns.NAME, value);
        return this;
    }

    public AccountSelection description(String... value) {
        addEquals(AccountColumns.DESCRIPTION, value);
        return this;
    }

    public AccountSelection descriptionNot(String... value) {
        addNotEquals(AccountColumns.DESCRIPTION, value);
        return this;
    }

    public AccountSelection descriptionLike(String... value) {
        addLike(AccountColumns.DESCRIPTION, value);
        return this;
    }

    public AccountSelection descriptionContains(String... value) {
        addContains(AccountColumns.DESCRIPTION, value);
        return this;
    }

    public AccountSelection descriptionStartsWith(String... value) {
        addStartsWith(AccountColumns.DESCRIPTION, value);
        return this;
    }

    public AccountSelection descriptionEndsWith(String... value) {
        addEndsWith(AccountColumns.DESCRIPTION, value);
        return this;
    }

    public AccountSelection url(String... value) {
        addEquals(AccountColumns.URL, value);
        return this;
    }

    public AccountSelection urlNot(String... value) {
        addNotEquals(AccountColumns.URL, value);
        return this;
    }

    public AccountSelection urlLike(String... value) {
        addLike(AccountColumns.URL, value);
        return this;
    }

    public AccountSelection urlContains(String... value) {
        addContains(AccountColumns.URL, value);
        return this;
    }

    public AccountSelection urlStartsWith(String... value) {
        addStartsWith(AccountColumns.URL, value);
        return this;
    }

    public AccountSelection urlEndsWith(String... value) {
        addEndsWith(AccountColumns.URL, value);
        return this;
    }

    public AccountSelection format(String... value) {
        addEquals(AccountColumns.FORMAT, value);
        return this;
    }

    public AccountSelection formatNot(String... value) {
        addNotEquals(AccountColumns.FORMAT, value);
        return this;
    }

    public AccountSelection formatLike(String... value) {
        addLike(AccountColumns.FORMAT, value);
        return this;
    }

    public AccountSelection formatContains(String... value) {
        addContains(AccountColumns.FORMAT, value);
        return this;
    }

    public AccountSelection formatStartsWith(String... value) {
        addStartsWith(AccountColumns.FORMAT, value);
        return this;
    }

    public AccountSelection formatEndsWith(String... value) {
        addEndsWith(AccountColumns.FORMAT, value);
        return this;
    }

    public AccountSelection defaultSend(int... value) {
        addEquals(AccountColumns.FLAGS, toObjectArray(value));
        return this;
    }

    public AccountSelection defaultSendNot(int... value) {
        addNotEquals(AccountColumns.FLAGS, toObjectArray(value));
        return this;
    }

    public AccountSelection defaultSendGt(int value) {
        addGreaterThan(AccountColumns.FLAGS, value);
        return this;
    }

    public AccountSelection defaultSendGtEq(int value) {
        addGreaterThanOrEquals(AccountColumns.FLAGS, value);
        return this;
    }

    public AccountSelection defaultSendLt(int value) {
        addLessThan(AccountColumns.FLAGS, value);
        return this;
    }

    public AccountSelection defaultSendLtEq(int value) {
        addLessThanOrEquals(AccountColumns.FLAGS, value);
        return this;
    }

    public AccountSelection enabled(boolean value) {
        addEquals(AccountColumns.ENABLED, toObjectArray(value));
        return this;
    }

    public AccountSelection authMethod(String... value) {
        addEquals(AccountColumns.AUTH_METHOD, value);
        return this;
    }

    public AccountSelection authMethodNot(String... value) {
        addNotEquals(AccountColumns.AUTH_METHOD, value);
        return this;
    }

    public AccountSelection authMethodLike(String... value) {
        addLike(AccountColumns.AUTH_METHOD, value);
        return this;
    }

    public AccountSelection authMethodContains(String... value) {
        addContains(AccountColumns.AUTH_METHOD, value);
        return this;
    }

    public AccountSelection authMethodStartsWith(String... value) {
        addStartsWith(AccountColumns.AUTH_METHOD, value);
        return this;
    }

    public AccountSelection authMethodEndsWith(String... value) {
        addEndsWith(AccountColumns.AUTH_METHOD, value);
        return this;
    }

    public AccountSelection authConfig(String... value) {
        addEquals(AccountColumns.AUTH_CONFIG, value);
        return this;
    }

    public AccountSelection authConfigNot(String... value) {
        addNotEquals(AccountColumns.AUTH_CONFIG, value);
        return this;
    }

    public AccountSelection authConfigLike(String... value) {
        addLike(AccountColumns.AUTH_CONFIG, value);
        return this;
    }

    public AccountSelection authConfigContains(String... value) {
        addContains(AccountColumns.AUTH_CONFIG, value);
        return this;
    }

    public AccountSelection authConfigStartsWith(String... value) {
        addStartsWith(AccountColumns.AUTH_CONFIG, value);
        return this;
    }

    public AccountSelection authConfigEndsWith(String... value) {
        addEndsWith(AccountColumns.AUTH_CONFIG, value);
        return this;
    }

    public AccountSelection icon(int... value) {
        addEquals(AccountColumns.ICON, toObjectArray(value));
        return this;
    }

    public AccountSelection iconNot(int... value) {
        addNotEquals(AccountColumns.ICON, toObjectArray(value));
        return this;
    }

    public AccountSelection iconGt(int value) {
        addGreaterThan(AccountColumns.ICON, value);
        return this;
    }

    public AccountSelection iconGtEq(int value) {
        addGreaterThanOrEquals(AccountColumns.ICON, value);
        return this;
    }

    public AccountSelection iconLt(int value) {
        addLessThan(AccountColumns.ICON, value);
        return this;
    }

    public AccountSelection iconLtEq(int value) {
        addLessThanOrEquals(AccountColumns.ICON, value);
        return this;
    }
}
