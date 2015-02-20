package org.runnerup.content.db.provider.base;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.ContentResolver;
import android.net.Uri;

public abstract class AbstractSelection<T extends AbstractSelection<?>> {
    private static final String EQ = "=?";
    private static final String PAREN_OPEN = "(";
    private static final String PAREN_CLOSE = ")";
    private static final String AND = " AND ";
    private static final String OR = " OR ";
    private static final String IS_NULL = " IS NULL";
    private static final String IS_NOT_NULL = " IS NOT NULL";
    private static final String IN = " IN (";
    private static final String NOT_IN = " NOT IN (";
    private static final String COMMA = ",";
    private static final String GT = ">?";
    private static final String LT = "<?";
    private static final String GT_EQ = ">=?";
    private static final String LT_EQ = "<=?";
    private static final String NOT_EQ = "<>?";
    private static final String LIKE = " LIKE ?";
    private static final String CONTAINS = " LIKE '%' || ? || '%'";
    private static final String STARTS = " LIKE ? || '%'";
    private static final String ENDS = " LIKE '%' || ?";

    private final StringBuilder mSelection = new StringBuilder();
    private final List<String> mSelectionArgs = new ArrayList<String>(5);

    Boolean mNotify;
    String mGroupBy;
    String mHaving;
    Integer mLimit;

    protected void addEquals(String column, Object[] value) {
        mSelection.append(column);

        if (value == null) {
            // Single null value
            mSelection.append(IS_NULL);
        } else if (value.length > 1) {
            // Multiple values ('in' clause)
            mSelection.append(IN);
            for (int i = 0; i < value.length; i++) {
                mSelection.append("?");
                if (i < value.length - 1) {
                    mSelection.append(COMMA);
                }
                mSelectionArgs.add(valueOf(value[i]));
            }
            mSelection.append(PAREN_CLOSE);
        } else {
            // Single value
            if (value[0] == null) {
                // Single null value
                mSelection.append(IS_NULL);
            } else {
                // Single not null value
                mSelection.append(EQ);
                mSelectionArgs.add(valueOf(value[0]));
            }
        }
    }

    protected void addNotEquals(String column, Object[] value) {
        mSelection.append(column);

        if (value == null) {
            // Single null value
            mSelection.append(IS_NOT_NULL);
        } else if (value.length > 1) {
            // Multiple values ('in' clause)
            mSelection.append(NOT_IN);
            for (int i = 0; i < value.length; i++) {
                mSelection.append("?");
                if (i < value.length - 1) {
                    mSelection.append(COMMA);
                }
                mSelectionArgs.add(valueOf(value[i]));
            }
            mSelection.append(PAREN_CLOSE);
        } else {
            // Single value
            if (value[0] == null) {
                // Single null value
                mSelection.append(IS_NOT_NULL);
            } else {
                // Single not null value
                mSelection.append(NOT_EQ);
                mSelectionArgs.add(valueOf(value[0]));
            }
        }
    }

    protected void addLike(String column, String[] values) {
        mSelection.append(PAREN_OPEN);
        for (int i = 0; i < values.length; i++) {
            mSelection.append(column);
            mSelection.append(LIKE);
            mSelectionArgs.add(values[i]);
            if (i < values.length - 1) {
                mSelection.append(OR);
            }
        }
        mSelection.append(PAREN_CLOSE);
    }

    protected void addContains(String column, String[] values) {
        mSelection.append(PAREN_OPEN);
        for (int i = 0; i < values.length; i++) {
            mSelection.append(column);
            mSelection.append(CONTAINS);
            mSelectionArgs.add(values[i]);
            if (i < values.length - 1) {
                mSelection.append(OR);
            }
        }
        mSelection.append(PAREN_CLOSE);
    }

    protected void addStartsWith(String column, String[] values) {
        mSelection.append(PAREN_OPEN);
        for (int i = 0; i < values.length; i++) {
            mSelection.append(column);
            mSelection.append(STARTS);
            mSelectionArgs.add(values[i]);
            if (i < values.length - 1) {
                mSelection.append(OR);
            }
        }
        mSelection.append(PAREN_CLOSE);
    }

    protected void addEndsWith(String column, String[] values) {
        mSelection.append(PAREN_OPEN);
        for (int i = 0; i < values.length; i++) {
            mSelection.append(column);
            mSelection.append(ENDS);
            mSelectionArgs.add(values[i]);
            if (i < values.length - 1) {
                mSelection.append(OR);
            }
        }
        mSelection.append(PAREN_CLOSE);
    }

    protected void addGreaterThan(String column, Object value) {
        mSelection.append(column);
        mSelection.append(GT);
        mSelectionArgs.add(valueOf(value));
    }

    protected void addGreaterThanOrEquals(String column, Object value) {
        mSelection.append(column);
        mSelection.append(GT_EQ);
        mSelectionArgs.add(valueOf(value));
    }

    protected void addLessThan(String column, Object value) {
        mSelection.append(column);
        mSelection.append(LT);
        mSelectionArgs.add(valueOf(value));
    }

    protected void addLessThanOrEquals(String column, Object value) {
        mSelection.append(column);
        mSelection.append(LT_EQ);
        mSelectionArgs.add(valueOf(value));
    }

    public void addRaw(String raw, Object... args) {
        mSelection.append(" ");
        mSelection.append(raw);
        mSelection.append(" ");
        for (Object arg : args) {
            mSelectionArgs.add(valueOf(arg));
        }
    }

    private String valueOf(Object obj) {
        if (obj instanceof Date) {
            return String.valueOf(((Date) obj).getTime());
        } else if (obj instanceof Boolean) {
            return (Boolean) obj ? "1" : "0";
        } else if (obj instanceof Enum) {
            return String.valueOf(((Enum<?>) obj).ordinal());
        }
        return String.valueOf(obj);
    }

    @SuppressWarnings("unchecked")
    public T openParen() {
        mSelection.append(PAREN_OPEN);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T closeParen() {
        mSelection.append(PAREN_CLOSE);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T and() {
        mSelection.append(AND);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T or() {
        mSelection.append(OR);
        return (T) this;
    }


    protected Object[] toObjectArray(int... array) {
        Object[] res = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            res[i] = array[i];
        }
        return res;
    }

    protected Object[] toObjectArray(long... array) {
        Object[] res = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            res[i] = array[i];
        }
        return res;
    }

    protected Object[] toObjectArray(float... array) {
        Object[] res = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            res[i] = array[i];
        }
        return res;
    }

    protected Object[] toObjectArray(double... array) {
        Object[] res = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            res[i] = array[i];
        }
        return res;
    }

    protected Object[] toObjectArray(Boolean value) {
        return new Object[] { value };
    }


    /**
     * Returns the selection produced by this object.
     */
    public String sel() {
        return mSelection.toString();
    }

    /**
     * Returns the selection arguments produced by this object.
     */
    public String[] args() {
        int size = mSelectionArgs.size();
        if (size == 0) return null;
        return mSelectionArgs.toArray(new String[size]);
    }


    /**
     * Returns the {@code uri} argument to pass to the {@code ContentResolver} methods.
     */
    public Uri uri() {
        Uri uri = baseUri();
        if (mNotify != null) uri = BaseContentProvider.notify(uri, mNotify);
        if (mGroupBy != null) uri = BaseContentProvider.groupBy(uri, mGroupBy);
        if (mHaving != null) uri = BaseContentProvider.having(uri, mHaving);
        if (mLimit != null) uri = BaseContentProvider.limit(uri, String.valueOf(mLimit));
        return uri;
    }

    protected abstract Uri baseUri();

    /**
     * Deletes row(s) specified by this selection.
     *
     * @param contentResolver The content resolver to use.
     * @return The number of rows deleted.
     */
    public int delete(ContentResolver contentResolver) {
        return contentResolver.delete(uri(), sel(), args());
    }

    @SuppressWarnings("unchecked")
    public T notify(boolean notify) {
        mNotify = notify;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T groupBy(String groupBy) {
        mGroupBy = groupBy;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T having(String having) {
        mHaving = having;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T limit(int limit) {
        mLimit = limit;
        return (T) this;
    }
}
