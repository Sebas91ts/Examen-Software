package com.systembpm.system.modules.ai.application.service;

import java.util.Map;

public interface AiRoutingContextService {

    Map<String, Object> buildTaskContext(String taskId, String requesterEmail);

    Map<String, Object> buildInstanceContext(String processInstanceId, String requesterEmail);

    Map<String, Object> buildDashboardContext(String requesterEmail);
}
