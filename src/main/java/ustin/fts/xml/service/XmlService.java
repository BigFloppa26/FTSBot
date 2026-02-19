package ustin.fts.xml.service;

import org.w3c.dom.Document;
import ustin.fts.xml.model.DTData;

import javax.xml.xpath.XPath;

public interface XmlService {

    DTData parseXml(byte[] xmlData) throws Exception;

    String getXmlValue(Document doc, XPath xpath, String xpathExpr, String tagName);

    String getValueByXPath(Document doc, XPath xpath, String expression);

    String getValueByTagName(Document doc, String tagName);

}