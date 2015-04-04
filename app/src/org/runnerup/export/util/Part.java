package org.runnerup.export.util;

/**
* Created by LFAJER on 2015-04-04.
*/
public class Part<Value extends Writable> {

    String name = null;
    String filename = null;
    String contentType = null;
    String contentTransferEncoding = null;
    Value value = null;

    public Part(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentTransferEncoding(String contentTransferEncoding) {
        this.contentTransferEncoding = contentTransferEncoding;
    }

    public String getContentTransferEncoding() {
        return contentTransferEncoding;
    }

    public Value getValue() {
        return value;
    }
}
