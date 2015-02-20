package org.runnerup.content.db.provider.audioschemes;

import org.runnerup.content.db.provider.base.BaseModel;

import java.util.Date;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Definition of the audio cue scheme
 */
public interface AudioschemesModel extends BaseModel {

    /**
     * Name of the scheme
     * Cannot be {@code null}.
     */
    @NonNull
    String getName();

    /**
     * The order of the scheme
     */
    int getSortOrder();
}
