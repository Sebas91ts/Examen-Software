package com.systembpm.system.modules.realtime.application.service;

import java.util.Map;

public interface IRealtimeEventService {

    void publishNotificationEvent(String userId, String userEmail, String title, String message, String processInstanceId, String taskId);

    void publishTaskAvailableForArea(String areaId, String processInstanceId, String taskId, String taskName);

    void publishTaskCreated(Map<String, Object> taskSnapshot);

    void publishTaskClaimed(Map<String, Object> taskSnapshot, String claimedBy);

    void publishTaskCompleted(Map<String, Object> taskSnapshot, String completedBy);

    void publishProcessUpdated(String processInstanceId, String message, Map<String, Object> payload);
}
