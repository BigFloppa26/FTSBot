package ustin.fts.xml.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import ustin.fts.xml.model.DTData;
import ustin.fts.xml.service.XmlService;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmlServiceImpl implements XmlService {

    @Override
    public DTData parseXml(byte[] xmlData) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlData));

        // Извлекаем namespace
        Map<String, String> namespaces = new HashMap<>();
        var attrs = doc.getDocumentElement().getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = (Attr) attrs.item(i);
            var name = attr.getName();
            if (name.startsWith("xmlns:")) {
                namespaces.put(name.substring(6), attr.getValue());
            }
        }

        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {

            @Override
            public String getNamespaceURI(String prefix) {
                if (prefix == null) throw new NullPointerException("Null prefix");
                return namespaces.getOrDefault(prefix, "");
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return Collections.emptyIterator();
            }
        });

        var originCountry = getXmlValue(doc, xpath, "//catESAD_cu:OriginCountryCode", "OriginCountryCode");
        var decisionCode = getXmlValue(doc, xpath, "//catESAD_ru:DecisionCode", "DecisionCode");
        var decisionDate = getXmlValue(doc, xpath, "//catESAD_ru:DateInf", "DateInf");
        var docNumber = getXmlValue(doc, xpath, "//cat_ru:PrDocumentNumber", "PrDocumentNumber");
        var docDate = getXmlValue(doc, xpath, "//cat_ru:PrDocumentDate", "PrDocumentDate");

        return new DTData(originCountry, decisionCode, decisionDate, docNumber, docDate);
    }

    @Override
    public String getXmlValue(Document doc, XPath xpath, String xpathExpr, String tagName) {
        String value = getValueByXPath(doc, xpath, xpathExpr);
        if (value.isEmpty()) {
            value = getValueByTagName(doc, tagName);
        }
        return value;
    }

    @Override
    public String getValueByXPath(Document doc, XPath xpath, String expression) {
        try {
            var node = (Node) xpath.compile(expression).evaluate(doc, XPathConstants.NODE);

            return node != null ? node.getTextContent().trim() : "";
        } catch (Exception e) {
            log.debug("XPath error for {}: {}", expression, e.getMessage());
            return "";
        }
    }

    @Override
    public String getValueByTagName(Document doc, String tagName) {
        // Сначала ищем с namespace
        var nodes = doc.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }

        // Пробуем без namespace
        nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }

        return "";
    }
}