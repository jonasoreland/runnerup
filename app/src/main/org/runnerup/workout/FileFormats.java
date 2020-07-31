package org.runnerup.workout;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileFormats {

    private final boolean readonly;
    private String formats;

    public static class Format {

        final private String name;
        final private String value;

        Format(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }
        public String getValue() {
            return value;
        }
    }

    public final static Format GPX = new Format("GPX", "gpx");
    public final static Format TCX = new Format("TCX", "tcx");
    public final static List<Format> ALL_FORMATS;
    public final static FileFormats DEFAULT_FORMATS;

    static {
        List<Format> formatList = Arrays.asList(TCX, GPX);
        ALL_FORMATS = Collections.unmodifiableList(formatList);
        DEFAULT_FORMATS = new FileFormats(FileFormats.TCX.getValue(), true);
    }

    public FileFormats() {
        this(null, false);
    }

    public FileFormats(String formats) {
        this(formats, false);
    }

    private FileFormats(String formats, boolean readonly) {
        this.readonly = readonly;
        this.formats = formats == null ? "" : formats;
    }

    public boolean contains(Format format) {
        if (format == null) {
            throw new IllegalArgumentException();
        }
        // search for the format type between 2 word boundaries, anywhere in the string
        return formats.matches(".*\\b" + format.getValue() + "\\b.*");
    }

    public boolean remove(Format format) {
        if (format == null) {
            throw new IllegalArgumentException();
        }
        if (readonly) {
            throw new UnsupportedOperationException();
        }
        if (contains(format)) {
            formats = formats.replaceAll(",?" + format.getValue() + "\\b", "");
            // cleanup commas
            formats = formats.replaceAll(",$", "");
            formats = formats.replaceAll("^,", "");
            return true;
        } else {
            return false;
        }
    }

    public boolean add(Format format) {
        if (format == null) {
            throw new IllegalArgumentException();
        }
        if (readonly) {
            throw new UnsupportedOperationException();
        }
        if (formats.length() == 0) {
            formats = format.getValue();
            return true;
        } else {
            if (contains(format)) {
                return false;
            } else {
                formats += "," + format.getValue();
                // cleanup commas
                formats = formats.replaceAll(",,", ",");
                return true;
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return formats;
    }
}
