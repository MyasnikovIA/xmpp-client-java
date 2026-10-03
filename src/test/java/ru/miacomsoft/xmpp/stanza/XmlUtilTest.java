package ru.miacomsoft.xmpp.stanza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.*;

class XmlUtilTest {

    // ---------- escape ----------

    @Test
    @DisplayName("escape: амперсанд и угловые скобки")
    void escapeAmpAndBrackets() {
        assertEquals("a&amp;b&lt;c&gt;d", XmlUtil.escape("a&b<c>d"));
    }

    @Test
    @DisplayName("escape: кавычки одинарные и двойные")
    void escapeQuotes() {
        assertEquals("&quot;hi&quot; &apos;x&apos;", XmlUtil.escape("\"hi\" 'x'"));
    }

    @Test
    @DisplayName("escape: пустая строка → пустая")
    void escapeEmpty() {
        assertEquals("", XmlUtil.escape(""));
    }

    // ---------- localName ----------

    @Test
    @DisplayName("localName: элемент с namespace")
    void localNameWithNs() throws Exception {
        Document d = XmlUtil.parse("<stream:features xmlns:stream='x'/>");
        Element el = d.getDocumentElement();
        assertEquals("features", XmlUtil.localName(el));
    }

    @Test
    @DisplayName("localName: элемент без namespace")
    void localNameWithoutNs() throws Exception {
        Document d = XmlUtil.parse("<message/>");
        assertEquals("message", XmlUtil.localName(d.getDocumentElement()));
    }

    @Test
    @DisplayName("localName: null → пустая строка")
    void localNameNull() {
        assertEquals("", XmlUtil.localName(null));
    }

    // ---------- attr ----------

    @Test
    @DisplayName("attr: атрибут есть")
    void attrPresent() throws Exception {
        Document d = XmlUtil.parse("<iq type='set' id='1'/>");
        assertEquals("set", XmlUtil.attr(d.getDocumentElement(), "type"));
    }

    @Test
    @DisplayName("attr: атрибута нет → null")
    void attrAbsent() throws Exception {
        Document d = XmlUtil.parse("<iq type='set'/>");
        assertNull(XmlUtil.attr(d.getDocumentElement(), "id"));
    }

    @Test
    @DisplayName("attr: пустое значение атрибута возвращается как ''")
    void attrEmpty() throws Exception {
        Document d = XmlUtil.parse("<iq type=''/>");
        assertEquals("", XmlUtil.attr(d.getDocumentElement(), "type"));
    }

    // ---------- firstChild ----------

    @Test
    @DisplayName("firstChild: находит прямого ребёнка")
    void firstChildDirect() throws Exception {
        Document d = XmlUtil.parse("<iq><query xmlns='x'/></iq>");
        Element q = XmlUtil.firstChild(d.getDocumentElement(), "query");
        assertNotNull(q);
        assertEquals("x", q.getAttribute("xmlns"));
    }

    @Test
    @DisplayName("firstChild: НЕ находит вложенного внука (главный фикс)")
    void firstChildDoesNotSearchDeep() throws Exception {
        Document d = XmlUtil.parse("<iq><a><result/></a></iq>");
        assertNull(XmlUtil.firstChild(d.getDocumentElement(), "result"));
    }

    @Test
    @DisplayName("firstChild: null parent → null")
    void firstChildNullParent() {
        assertNull(XmlUtil.firstChild(null, "query"));
    }

    @Test
    @DisplayName("firstChild: нет такого ребёнка → null")
    void firstChildMissing() throws Exception {
        Document d = XmlUtil.parse("<iq><a/></iq>");
        assertNull(XmlUtil.firstChild(d.getDocumentElement(), "b"));
    }

    // ---------- parse ----------

    @Test
    @DisplayName("parse: корректный XML")
    void parseValid() throws Exception {
        Document d = XmlUtil.parse("<a><b/></a>");
        assertEquals("a", d.getDocumentElement().getNodeName());
    }

    @Test
    @DisplayName("parse: битый XML → исключение")
    void parseInvalid() {
        assertThrows(Exception.class, () -> XmlUtil.parse("<a><b></a>"));
    }

    @Test
    @DisplayName("parse: namespaceAware=true — getNamespaceURI работает")
    void parseNamespaceAware() throws Exception {
        Document d = XmlUtil.parse("<query xmlns='jabber:iq:register'/>");
        assertEquals("jabber:iq:register",
                d.getDocumentElement().getNamespaceURI());
    }
}