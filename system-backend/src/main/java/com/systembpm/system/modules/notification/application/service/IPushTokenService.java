package com.systembpm.system.modules.notification.application.service;

public interface IPushTokenService {
    void registrarToken(String userEmail, String token);

    void eliminarToken(String userEmail, String token);
}
