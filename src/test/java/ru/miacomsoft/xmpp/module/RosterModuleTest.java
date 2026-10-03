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

class RosterModuleTest {

    private RosterModule module;
    private XmppClient client;

    @BeforeEach
    void setUp() {
        module = new RosterModule();
        client = mock(XmppClient.class);
        when(client.getBareJid()).thenReturn("me@example.com");
        module.setClient(client);
    }

    private Element parse(String xml) throws Exception {
        Document d = XmlUtil.parse("<w>" + xml + "</w>");
        return (Element) d.getDocumentElement().getFirstChild();
    }

    // ============================================================
    // handle: разбор roster
    // ============================================================

    @Test
    @DisplayName("handle: разбирает roster с несколькими item")
    void handleRoster() throws Exception {
        Element e = parse("<iq type='result' id='r1'>"
                + "<query xmlns='jabber:iq:roster'>"
                + "<item jid='a@example.com' name='Alice'/>"
                + "<item jid='b@example.com' name='Bob'/>"
                + "</query></iq>");

        assertTrue(module.handle(e));
        assertEquals(2, module.getContacts().size());
        assertEquals("Alice", module.getContacts().get("a@example.com").getName());
        assertEquals("Bob", module.getContacts().get("b@example.com").getName());
    }

    @Test
    @DisplayName("handle: пропускает собственный JID")
    void handleSkipsSelf() throws Exception {
        Element e = parse("<iq type='result' id='r1'>"
                + "<query xmlns='jabber:iq:roster'>"
                + "<item jid='me@example.com'/>"
                + "<item jid='b@example.com'/>"
                + "</query></iq>");

        assertTrue(module.handle(e));
        assertFalse(module.getContacts().containsKey("me@example.com"));
        assertTrue(module.getContacts().containsKey("b@example.com"));
    }

    @Test
    @DisplayName("handle: без name → name=jid")
    void handleNoName() throws Exception {
        Element e = parse("<iq type='result' id='r1'>"
                + "<query xmlns='jabber:iq:roster'>"
                + "<item jid='b@example.com'/>"
                + "</query></iq>");
        assertTrue(module.handle(e));
        assertEquals("b@example.com",
                module.getContacts().get("b@example.com").getName());
    }

    @Test
    @DisplayName("handle: item без jid → пропускается")
    void handleItemWithoutJid() throws Exception {
        Element e = parse("<iq type='result' id='r1'>"
                + "<query xmlns='jabber:iq:roster'>"
                + "<item name='no-jid'/>"
                + "</query></iq>");
        assertTrue(module.handle(e));
        assertTrue(module.getContacts().isEmpty());
    }

    @Test
    @DisplayName("handle: не-roster IQ → false")
    void handleNonRosterIq() throws Exception {
        Element e = parse("<iq type='result' id='r1'>"
                + "<query xmlns='jabber:iq:version'/></iq>");
        assertFalse(module.handle(e));
    }

    @Test
    @DisplayName("handle: не IQ → false")
    void handleNonIq() throws Exception {
        Element e = parse("<message/>");
        assertFalse(module.handle(e));
    }

    @Test
    @DisplayName("handle: IQ без query → false")
    void handleIqWithoutQuery() throws Exception {
        Element e = parse("<iq type='result' id='r1'/>");
        assertFalse(module.handle(e));
    }

    // ============================================================
    // requestRoster
    // ============================================================

    @Test
    @DisplayName("requestRoster: формирует корректный IQ")
    void requestRosterXml() throws XmppException {
        module.requestRoster();
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(client).sendRaw(cap.capture());
        String xml = cap.getValue();
        assertTrue(xml.contains("type=\"get\""));
        assertTrue(xml.contains("jabber:iq:roster"));
    }

    @Test
    @DisplayName("requestRoster: id уникален между вызовами")
    void requestRosterIdUnique() throws XmppException {
        module.requestRoster();
        module.requestRoster();
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).sendRaw(cap.capture());
        String a = cap.getAllValues().get(0);
        String b = cap.getAllValues().get(1);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("requestRoster: name-атрибут запроса отсутствует")
    void requestRosterNoExtras() throws XmppException {
        module.requestRoster();
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(client).sendRaw(cap.capture());
        assertFalse(cap.getValue().contains("<item"));
    }

    // ============================================================
    // addContact
    // ============================================================

    @Test
    @DisplayName("addContact: добавляет контакт")
    void addContact() {
        module.addContact("x@example.com");
        assertTrue(module.getContacts().containsKey("x@example.com"));
    }

    @Test
    @DisplayName("addContact: не перезаписывает существующий")
    void addContactIdempotent() {
        module.addContact("x@example.com");
        Contact c1 = module.getContacts().get("x@example.com");
        module.addContact("x@example.com");
        assertSame(c1, module.getContacts().get("x@example.com"));
    }

    @Test
    @DisplayName("addContact: контакт по умолчанию offline и без unread")
    void addContactDefaults() {
        module.addContact("x@example.com");
        Contact c = module.getContacts().get("x@example.com");
        assertFalse(c.isOnline());
        assertEquals(0, c.getUnread());
        assertEquals("x@example.com", c.getName());
    }

    // ============================================================
    // updatePresence
    // ============================================================

    @Test
    @DisplayName("updatePresence: помечает online")
    void updatePresenceOnline() {
        module.updatePresence("x@example.com", true);
        assertTrue(module.getContacts().get("x@example.com").isOnline());
    }

    @Test
    @DisplayName("updatePresence: помечает offline")
    void updatePresenceOffline() {
        module.updatePresence("x@example.com", false);
        assertFalse(module.getContacts().get("x@example.com").isOnline());
    }

    @Test
    @DisplayName("updatePresence: создаёт контакт, если его не было")
    void updatePresenceCreates() {
        module.updatePresence("new@example.com", true);
        assertTrue(module.getContacts().containsKey("new@example.com"));
        assertTrue(module.getContacts().get("new@example.com").isOnline());
    }

    // ============================================================
    // getContacts / getName
    // ============================================================

    @Test
    @DisplayName("getContacts: изначально пустой")
    void getContactsEmpty() {
        assertTrue(module.getContacts().isEmpty());
    }

    @Test
    @DisplayName("getName: 'roster'")
    void getName() {
        assertEquals("roster", module.getName());
    }
}