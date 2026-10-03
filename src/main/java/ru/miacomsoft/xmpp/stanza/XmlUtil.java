package ru.miacomsoft.xmpp.stanza;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class XmlUtil {
    public static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static String attr(Element el, String name) {
        return el.hasAttribute(name) ? el.getAttribute(name) : null;
    }

    /**
     * Возвращает ПЕРВОГО прямого ребёнка-элемента с указанным localName.
     * Не ищет во всём поддереве (в отличие от getElementsByTagName).
     */
    public static Element firstChild(Element parent, String name) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String local = el.getLocalName();
            if (local == null) {
                // namespace-aware парсер может вернуть null для элементов без namespace
                local = el.getNodeName();
                int colon = local.indexOf(':');
                if (colon >= 0) local = local.substring(colon + 1);
            }
            if (name.equals(local)) {
                return el;
            }
        }
        return null;
    }
    /**
     * Безопасно возвращает localName элемента (без namespace-префикса).
     * Никогда не возвращает null.
     */
    public static String localName(Element el) {
        if (el == null) return "";
        String local = el.getLocalName();
        if (local == null || local.isEmpty()) {
            local = el.getNodeName();
            int colon = local.indexOf(':');
            if (colon >= 0) local = local.substring(colon + 1);
        }
        return local;
    }

}