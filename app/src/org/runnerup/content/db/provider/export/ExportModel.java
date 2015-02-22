package org.runnerup.content.db.provider.export;

import org.runnerup.content.db.provider.base.BaseModel;

import java.util.Date;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Informatoin about the export state of an activity
 */
public interface ExportModel extends BaseModel {

    /**
     * Id of the activity that's beeing exported
     */
    int getActivityId();

    /**
     * The account to which the activity has been exported
     * Cannot be {@code null}.
     */
    @NonNull
    String getAccountId();

    /**
     * Status of the export
     * Can be {@code null}.
     */
    @Nullable
    String getStatus();

    /**
     * External Id of the activity
     * Can be {@code null}.
     */
    @Nullable
    String getExtId();

    /**
     * Extra
     */
    int getExtra();
}
