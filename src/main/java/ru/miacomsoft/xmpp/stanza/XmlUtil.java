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

    public static Element firstChild(Element parent, String name) {
        NodeList list = parent.getElementsByTagName(name);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }
}