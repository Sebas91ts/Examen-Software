package com.systembpm.system.modules.document.domain;

public class DocumentSizeExceededException extends RuntimeException {

    public DocumentSizeExceededException(long maxFileSizeBytes) {
        super("El archivo excede el tamano maximo permitido de " + maxFileSizeBytes + " bytes");
    }
}
