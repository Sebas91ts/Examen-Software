package com.systembpm.system.modules.document.domain;

public class DocumentRequesterNotFoundException extends RuntimeException {

    public DocumentRequesterNotFoundException() {
        super("No se pudo resolver el usuario autenticado");
    }
}
