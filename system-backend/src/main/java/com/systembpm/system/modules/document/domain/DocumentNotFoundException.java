package com.systembpm.system.modules.document.domain;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String documentId) {
        super("No se encontro el documento con id " + documentId);
    }
}
