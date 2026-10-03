package ru.miacomsoft.xmpp.module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ru.miacomsoft.xmpp.XmppClient;
import ru.miacomsoft.xmpp.XmppException;
import ru.miacomsoft.xmpp.stanza.XmlUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MamModuleTest {

    private MamModule module;
    private XmppClient client;

    @BeforeEach
    void setUp() {
        module = new MamModule();
        client = mock(XmppClient.class);
        when(client.getBareJid()).thenReturn("me@example.com");
        when(client.getJid()).thenReturn("me@example.com/phone");
        module.setClient(client);
    }

    private Element parse(String xml) throws Exception {
        Document d = XmlUtil.parse("<w>" + xml + "</w>");
        return (Element) d.getDocumentElement().getFirstChild();
    }

    // ---------- handle ----------

    @Test
    @DisplayName("handle: результат MAM → сохраняет сообщение")
    void handleMamResult() throws Exception {
        Element e = parse("<message id='mam1'>"
                + "<result xmlns='urn:xmpp:mam:2' queryid='q1' id='orig1'>"
                + "<forwarded xmlns='urn:xmpp:forward:0'>"
                + "<delay xmlns='urn:xmpp:delay' stamp='2024-01-01T12:00:00Z'/>"
                + "<message from='bob@example.com/phone' to='me@example.com' id='orig1'>"
                + "<body>old hi</body></message>"
                + "</forwarded></result></message>");

        assertTrue(module.handle(e));
        verify(client, atLeastOnce()).storeMessage(eq("bob@example.com"),
                any(ChatMessage.class));
    }

    @Test
    @DisplayName("handle: результат MAM с собственным сообщением → чат = получатель")
    void handleMamFromSelf() throws Exception {
        Element e = parse("<message id='mam2'>"
                + "<result xmlns='urn:xmpp:mam:2' id='orig2'>"
                + "<forwarded xmlns='urn:xmpp:forward:0'>"
                + "<delay xmlns='urn:xmpp:delay' stamp='2024-01-01T12:00:00Z'/>"
                + "<message from='me@example.com/phone' to='bob@example.com' id='orig2'>"
                + "<body>my old</body></message>"
                + "</forwarded></result></message>");

        assertTrue(module.handle(e));
        verify(client).storeMessage(eq("bob@example.com"), any(ChatMessage.class));
    }

    @Test
    @DisplayName("handle: не message → false")
    void handleNotMessage() throws Exception {
        Element e = parse("<iq id='1'/>");
        assertFalse(module.handle(e));
    }

    @Test
    @DisplayName("handle: message без <result> → false")
    void handleNoResult() throws Exception {
        Element e = parse("<message from='bob@example.com'><body>hi</body></message>");
        assertFalse(module.handle(e));
    }

    @Test
    @DisplayName("handle: result без forwarded → false")
    void handleNoForwarded() throws Exception {
        Element e = parse("<message><result xmlns='urn:xmpp:mam:2'/></message>");
        assertFalse(module.handle(e));
    }

    @Test
    @DisplayName("handle: forwarded без body → false")
    void handleNoBody() throws Exception {
        Element e = parse("<message><result xmlns='urn:xmpp:mam:2'>"
                + "<forwarded xmlns='urn:xmpp:forward:0'>"
                + "<message from='bob@example.com' to='me@example.com'/></forwarded>"
                + "</result></message>");
        assertFalse(module.handle(e));
    }

    // ---------- requestMam ----------

    @Test
    @DisplayName("requestMam: формирует IQ с urn:xmpp:mam:2")
    void requestMamXml() throws XmppException {
        module.requestMam("bob@example.com", 50);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(client).sendRaw(cap.capture());
        String xml = cap.getValue();
        assertTrue(xml.contains("urn:xmpp:mam:2"));
        assertTrue(xml.contains("<value>bob@example.com</value>"));
        assertTrue(xml.contains("<max>50</max>"));
    }

    @Test
    @DisplayName("requestMam: escape JID")
    void requestMamEscapesJid() throws XmppException {
        module.requestMam("a&b@example.com", 10);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(client).sendRaw(cap.capture());
        assertTrue(cap.getValue().contains("a&amp;b@example.com"));
    }

    @Test
    @DisplayName("requestMam: max вставляется в <max>")
    void requestMamMax() throws XmppException {
        module.requestMam("bob@example.com", 123);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(client).sendRaw(cap.capture());
        assertTrue(cap.getValue().contains("<max>123</max>"));
    }

    @Test
    @DisplayName("getName: 'mam'")
    void getName() {
        assertEquals("mam", module.getName());
    }
}