package com.systembpm.system.modules.document.domain;

public class DocumentTenantAccessDeniedException extends RuntimeException {

    public DocumentTenantAccessDeniedException() {
        super("No tienes acceso al tenant del documento");
    }
}
