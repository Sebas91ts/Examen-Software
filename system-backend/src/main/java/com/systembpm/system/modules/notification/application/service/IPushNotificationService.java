package com.systembpm.system.modules.notification.application.service;

import com.systembpm.system.modules.user.domain.Usuario;

import java.util.Map;

public interface IPushNotificationService {
    void sendToUser(Usuario usuario, String title, String message, Map<String, String> data);
}
