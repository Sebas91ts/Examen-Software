package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.modules.ai.application.dto.BpmnGenerationResponseDto;
import com.systembpm.system.modules.ai.application.dto.DiagramFlowDto;
import com.systembpm.system.modules.ai.application.dto.DiagramGatewayDto;
import com.systembpm.system.modules.ai.application.dto.DiagramResponseDto;
import com.systembpm.system.modules.ai.application.dto.DiagramTaskDto;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BpmnGeneratorService {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DC_NS = "http://www.omg.org/spec/DD/20100524/DC";
    private static final String DI_NS = "http://www.omg.org/spec/DD/20100524/DI";
    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";
    private static final String CUSTOM_NS = "http://systembpm.com/schema";

    public BpmnGenerationResponseDto generate(DiagramResponseDto diagram, String preferredProcessKey) {
        String processName = cleanText(diagram.getProcessName(), "Proceso generado con IA");
        String processKey = cleanText(preferredProcessKey, slugify(processName));
        String xml = buildXml(processName, processKey, diagram);
        return BpmnGenerationResponseDto.builder()
                .processName(processName)
                .processKey(processKey)
                .xml(xml)
                .build();
    }

    private String buildXml(String processName, String processKey, DiagramResponseDto diagram) {
        List<DiagramTaskDto> tasks = safeList(diagram.getTasks());
        List<DiagramGatewayDto> gateways = safeList(diagram.getGateways());
        List<DiagramFlowDto> flows = safeList(diagram.getFlows());
        List<String> areas = safeList(diagram.getAreas());

        Map<String, NodeBox> boxes = new LinkedHashMap<>();
        double x = 240;
        double y = 160;
        double laneTop = 70;
        double laneHeight = areas.isEmpty() ? 260 : Math.max(160, areas.size() * 160.0);
        double participantWidth = Math.max(920, 140 + Math.max(1, tasks.size() + gateways.size()) * 170);

        boxes.put("start", new NodeBox("start", 80, 80, x, y));
        x += 160;

        for (DiagramTaskDto task : tasks) {
            boxes.put(task.getId(), new NodeBox(task.getId(), 110, 80, x, y));
            x += 170;
        }

        for (DiagramGatewayDto gateway : gateways) {
            boxes.put(gateway.getId(), new NodeBox(gateway.getId(), 50, 50, x, y));
            x += 170;
        }

        boxes.put("end", new NodeBox("end", 80, 80, x, y));

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
        xml.append("xmlns:bpmn=\"").append(BPMN_NS).append("\" ");
        xml.append("xmlns:bpmndi=\"").append(BPMNDI_NS).append("\" ");
        xml.append("xmlns:dc=\"").append(DC_NS).append("\" ");
        xml.append("xmlns:di=\"").append(DI_NS).append("\" ");
        xml.append("xmlns:camunda=\"").append(CAMUNDA_NS).append("\" ");
        xml.append("xmlns:custom=\"").append(CUSTOM_NS).append("\" ");
        xml.append("id=\"Definitions_").append(processKey).append("\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n");
        xml.append("  <bpmn:collaboration id=\"Collaboration_").append(processKey).append("\">\n");
        xml.append("    <bpmn:participant id=\"Participant_").append(processKey).append("\" name=\"")
                .append(escape(processName)).append("\" processRef=\"").append(processKey).append("\"/>\n");
        xml.append("  </bpmn:collaboration>\n");
        xml.append("  <bpmn:process id=\"").append(processKey).append("\" name=\"")
                .append(escape(processName)).append("\" isExecutable=\"true\">\n");
        xml.append("    <bpmn:startEvent id=\"start\" name=\"Inicio\" />\n");

        for (DiagramTaskDto task : tasks) {
            xml.append("    <bpmn:userTask id=\"").append(task.getId()).append("\" name=\"")
                    .append(escape(task.getName())).append("\" />\n");
        }

        for (DiagramGatewayDto gateway : gateways) {
            xml.append("    <bpmn:exclusiveGateway id=\"").append(gateway.getId()).append("\" name=\"")
                    .append(escape(cleanText(gateway.getName(), cleanText(gateway.getCondition(), gateway.getType()))))
                    .append("\" />\n");
        }

        xml.append("    <bpmn:endEvent id=\"end\" name=\"Fin\" />\n");

        for (DiagramFlowDto flow : flows) {
            String flowId = flowId(flow);
            xml.append("    <bpmn:sequenceFlow id=\"").append(flowId).append("\" sourceRef=\"")
                    .append(escape(flow.getFrom())).append("\" targetRef=\"")
                    .append(escape(flow.getTo())).append("\"");
            if (hasText(flow.getCondition())) {
                xml.append(">\n");
                xml.append("      <bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">")
                        .append(escape(flow.getCondition()))
                        .append("</bpmn:conditionExpression>\n");
                xml.append("    </bpmn:sequenceFlow>\n");
            } else {
                xml.append(" />\n");
            }
        }

        if (!areas.isEmpty() && !tasks.isEmpty()) {
            xml.append("    <bpmn:laneSet id=\"LaneSet_").append(processKey).append("\">\n");
            Map<String, List<DiagramTaskDto>> tasksByArea = tasks.stream()
                    .collect(Collectors.groupingBy(
                            task -> cleanText(task.getArea(), "Sin area"),
                            LinkedHashMap::new,
                            Collectors.toList()));

            int laneIndex = 0;
            for (String area : areas) {
                xml.append("      <bpmn:lane id=\"Lane_").append(laneIndex).append("\" name=\"")
                        .append(escape(area)).append("\">\n");
                xml.append("        <bpmn:extensionElements>\n");
                xml.append("          <custom:areaRef>").append(escape(area)).append("</custom:areaRef>\n");
                xml.append("        </bpmn:extensionElements>\n");
                for (DiagramTaskDto task : tasksByArea.getOrDefault(area, List.of())) {
                    xml.append("        <bpmn:flowNodeRef>").append(task.getId()).append("</bpmn:flowNodeRef>\n");
                }
                xml.append("      </bpmn:lane>\n");
                laneIndex++;
            }
            xml.append("    </bpmn:laneSet>\n");
        }

        xml.append("  </bpmn:process>\n");
        xml.append("  <bpmndi:BPMNDiagram id=\"BPMNDiagram_").append(processKey).append("\">\n");
        xml.append("    <bpmndi:BPMNPlane id=\"BPMNPlane_").append(processKey).append("\" bpmnElement=\"Collaboration_")
                .append(processKey).append("\">\n");
        xml.append("      <bpmndi:BPMNShape id=\"Participant_").append(processKey).append("_di\" bpmnElement=\"Participant_")
                .append(processKey).append("\" isHorizontal=\"true\">\n");
        xml.append("        <dc:Bounds x=\"140\" y=\"70\" width=\"").append(participantWidth).append("\" height=\"")
                .append(laneHeight).append("\" />\n");
        xml.append("      </bpmndi:BPMNShape>\n");

        if (!areas.isEmpty()) {
            int laneIndex = 0;
            double currentLaneTop = laneTop;
            for (String ignored : areas) {
                xml.append("      <bpmndi:BPMNShape id=\"Lane_").append(laneIndex).append("_di\" bpmnElement=\"Lane_")
                        .append(laneIndex).append("\" isHorizontal=\"true\">\n");
                xml.append("        <dc:Bounds x=\"170\" y=\"").append(currentLaneTop).append("\" width=\"")
                        .append(participantWidth - 30).append("\" height=\"160\" />\n");
                xml.append("        <bpmndi:BPMNLabel />\n");
                xml.append("      </bpmndi:BPMNShape>\n");
                currentLaneTop += 160;
                laneIndex++;
            }
        }

        for (NodeBox box : boxes.values()) {
            xml.append("      <bpmndi:BPMNShape id=\"").append(box.id()).append("_di\" bpmnElement=\"")
                    .append(box.id()).append("\">\n");
            xml.append("        <dc:Bounds x=\"").append(box.x()).append("\" y=\"").append(box.y())
                    .append("\" width=\"").append(box.width()).append("\" height=\"").append(box.height())
                    .append("\" />\n");
            xml.append("      </bpmndi:BPMNShape>\n");
        }

        for (DiagramFlowDto flow : flows) {
            xml.append("      <bpmndi:BPMNEdge id=\"").append(flowId(flow)).append("_di\" bpmnElement=\"")
                    .append(flowId(flow)).append("\">\n");
            NodeBox source = boxes.get(flow.getFrom());
            NodeBox target = boxes.get(flow.getTo());
            if (source != null) {
                xml.append("        <di:waypoint x=\"").append(source.x() + source.width()).append("\" y=\"")
                        .append(source.y() + source.height() / 2.0).append("\" />\n");
            }
            if (target != null) {
                xml.append("        <di:waypoint x=\"").append(target.x()).append("\" y=\"")
                        .append(target.y() + target.height() / 2.0).append("\" />\n");
            }
            xml.append("      </bpmndi:BPMNEdge>\n");
        }

        xml.append("    </bpmndi:BPMNPlane>\n");
        xml.append("  </bpmndi:BPMNDiagram>\n");
        xml.append("</bpmn:definitions>\n");

        return xml.toString();
    }

    private String flowId(DiagramFlowDto flow) {
        return "Flow_" + cleanText(flow.getFrom(), "from") + "_" + cleanText(flow.getTo(), "to");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String cleanText(String value, String fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        return value.trim();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String slugify(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return hasText(normalized) ? normalized : "proceso_ia";
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private record NodeBox(String id, double width, double height, double x, double y) {}
}
