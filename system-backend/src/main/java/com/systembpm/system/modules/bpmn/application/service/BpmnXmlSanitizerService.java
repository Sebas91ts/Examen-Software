package com.systembpm.system.modules.bpmn.application.service;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.io.StringWriter;

@Service
public class BpmnXmlSanitizerService {

    private static final String BPMN_PROCESS_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    public String sanitize(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }

        try {
            Document document = parse(xml);
            stripUnsupportedAttributes(document);
            ensureExecutableProcesses(document);
            return serialize(document);
        } catch (Exception ex) {
            return xml;
        }
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private void stripUnsupportedAttributes(Document document) {
        if (document == null) {
            return;
        }

        for (int i = 0; i < document.getElementsByTagName("*").getLength(); i++) {
            Node node = document.getElementsByTagName("*").item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            NamedNodeMap attributes = element.getAttributes();
            for (int index = attributes.getLength() - 1; index >= 0; index--) {
                Node attribute = attributes.item(index);
                if (attribute == null) {
                    continue;
                }

                String localName = attribute.getLocalName();
                String nodeName = attribute.getNodeName();
                if ("structureType".equals(localName) || "structureType".equals(nodeName)) {
                    element.removeAttributeNode((org.w3c.dom.Attr) attribute);
                }
            }
        }
    }

    private void ensureExecutableProcesses(Document document) {
        if (document == null) {
            return;
        }

        for (int i = 0; i < document.getElementsByTagNameNS(BPMN_PROCESS_NS, "process").getLength(); i++) {
            Node node = document.getElementsByTagNameNS(BPMN_PROCESS_NS, "process").item(i);
            if (node instanceof Element processElement) {
                processElement.setAttribute("isExecutable", "true");
            }
        }
    }

    private String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
