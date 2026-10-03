package ru.miacomsoft.xmpp.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тестирует tryExtractStanza() через рефлексию, без открытия сокета.
 */
class XmppConnectionStanzaTest {

    private XmppConnection newConn() {
        return new XmppConnection("localhost", 5222);
    }

    private String extract(XmppConnection c) throws Exception {
        Method m = XmppConnection.class.getDeclaredMethod("tryExtractStanza");
        m.setAccessible(true);
        return (String) m.invoke(c);
    }

    private void feed(XmppConnection c, String data) throws Exception {
        Field f = XmppConnection.class.getDeclaredField("rawBuffer");
        f.setAccessible(true);
        StringBuilder sb = (StringBuilder) f.get(c);
        sb.append(data);
    }

    private String buffer(XmppConnection c) throws Exception {
        Field f = XmppConnection.class.getDeclaredField("rawBuffer");
        f.setAccessible(true);
        return ((StringBuilder) f.get(c)).toString();
    }

    // ---------- одиночные станзы ----------

    @Test
    @DisplayName("Парсер: одна простая станза")
    void singleSimple() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<message id='1'/>");
        assertEquals("<message id='1'/>", extract(c));
    }

    @Test
    @DisplayName("Парсер: полная станза с телом")
    void singleWithBody() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<message><body>hi</body></message>");
        assertEquals("<message><body>hi</body></message>", extract(c));
    }

    @Test
    @DisplayName("Парсер: две слипшиеся станзы — по одной за вызов")
    void twoStanzasBackToBack() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<a/><b/>");
        assertEquals("<a/>", extract(c));
        assertEquals("<b/>", extract(c));
        assertNull(extract(c));
    }

    // ---------- незавершённые ----------

    @Test
    @DisplayName("Парсер: неполная станза → null, буфер сохраняется")
    void incompleteReturnsNull() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<message><body>hi");
        assertNull(extract(c));
        // Буфер не потерян
        assertTrue(buffer(c).contains("<message>"));
    }

    @Test
    @DisplayName("Парсер: достраивание после частичного чтения")
    void completesAfterSecondFeed() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<message><body>hi");
        assertNull(extract(c));
        feed(c, "</body></message>");
        assertEquals("<message><body>hi</body></message>", extract(c));
    }

    @Test
    @DisplayName("Парсер: XML-пролог отбрасывается")
    void xmlPrologSkipped() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<?xml version='1.0'?><iq id='1'/>");
        assertEquals("<iq id='1'/>", extract(c));
    }

    @Test
    @DisplayName("Парсер: незакрытый stream:stream вычленяется")
    void streamStreamOpen() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<stream:stream xmlns='jabber:client' xmlns:stream='x'>");
        String s = extract(c);
        assertNotNull(s);
        assertTrue(s.startsWith("<stream:stream"));
    }

    @Test
    @DisplayName("Парсер: stream:stream + features — по одной")
    void streamThenFeatures() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<stream:stream xmlns:stream='x'><stream:features>"
                + "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"
                + "</stream:features>");
        String s1 = extract(c);
        assertTrue(s1.startsWith("<stream:stream"));
        String s2 = extract(c);
        assertTrue(s2.contains("<stream:features>"));
    }

    // ---------- кавычки и атрибуты ----------

    @Test
    @DisplayName("Парсер: > внутри атрибута не ломает depth")
    void angleInAttribute() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<msg body='a > b'/>");
        assertEquals("<msg body='a > b'/>", extract(c));
    }

    @Test
    @DisplayName("Парсер: < внутри кавычек не считается тегом")
    void angleInQuotedAttr() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<msg x='<not a tag>'/>");
        assertEquals("<msg x='<not a tag>'/>", extract(c));
    }

    @Test
    @DisplayName("Парсер: двойные кавычки в атрибутах")
    void doubleQuoteAttr() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<iq id=\"1\" type=\"get\"/>");
        assertEquals("<iq id=\"1\" type=\"get\"/>", extract(c));
    }

    // ---------- вложенность ----------

    @Test
    @DisplayName("Парсер: вложенные теги — глубина 2")
    void nestedDepth() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<a><b><c/></b></a>");
        assertEquals("<a><b><c/></b></a>", extract(c));
    }

    @Test
    @DisplayName("Парсер: смешанные открытые/закрытые")
    void mixedOpenClose() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<iq><query><item/></query></iq><msg/>");
        assertEquals("<iq><query><item/></query></iq>", extract(c));
        assertEquals("<msg/>", extract(c));
    }

    // ---------- мусор ----------

    @Test
    @DisplayName("Парсер: мусор до < отбрасывается")
    void garbageBeforeLt() throws Exception {
        XmppConnection c = newConn();
        feed(c, "  \n\t hello <iq id='1'/>");
        assertEquals("<iq id='1'/>", extract(c));
    }

    @Test
    @DisplayName("Парсер: пустой буфер → null")
    void emptyBuffer() throws Exception {
        XmppConnection c = newConn();
        assertNull(extract(c));
    }

    @Test
    @DisplayName("Парсер: только пробелы → null, буфер очищен")
    void onlyWhitespace() throws Exception {
        XmppConnection c = newConn();
        feed(c, "   \n\t   ");
        assertNull(extract(c));
        assertEquals("", buffer(c));
    }

    // ---------- multiple runs ----------

    @Test
    @DisplayName("Парсер: 5 станз подряд — все по одной")
    void fiveStanzas() throws Exception {
        XmppConnection c = newConn();
        feed(c, "<a/><b/><c/><d/><e/>");
        assertEquals("<a/>", extract(c));
        assertEquals("<b/>", extract(c));
        assertEquals("<c/>", extract(c));
        assertEquals("<d/>", extract(c));
        assertEquals("<e/>", extract(c));
        assertNull(extract(c));
    }
}