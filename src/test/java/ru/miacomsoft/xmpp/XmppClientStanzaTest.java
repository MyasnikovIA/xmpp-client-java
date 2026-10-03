package ru.miacomsoft.xmpp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.miacomsoft.xmpp.event.MessageEvent;
import ru.miacomsoft.xmpp.listener.EventBus;
import ru.miacomsoft.xmpp.module.Contact;
import ru.miacomsoft.xmpp.module.RosterModule;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет dispatch() через рефлексию (private).
 * В модулях используем реальные классы, но без сети.
 */
class XmppClientStanzaTest {

    private XmppClient client;

    @BeforeEach
    void setUp() {
        client = new XmppClient("localhost", 5222);
    }

    private void dispatch(String xml) throws Exception {
        Method m = XmppClient.class.getDeclaredMethod("dispatch", String.class);
        m.setAccessible(true);
        m.invoke(client, xml);
    }

    @Test
    @DisplayName("dispatch: message с body → RECEIVED через MessageModule")
    void dispatchMessage() throws Exception {
        EventBus bus = client.getEventBus();
        final MessageEvent[] holder = new MessageEvent[1];
        bus.addMessageListener(ev -> {
            if (ev.getKind() == MessageEvent.Kind.RECEIVED) holder[0] = ev;
        });

        dispatch("<message from='bob@example.com/phone' id='m1' type='chat'>"
                + "<body>hi</body></message>");

        assertNotNull(holder[0]);
        assertEquals("bob@example.com", holder[0].getFrom());
        assertEquals("hi", holder[0].getMessage().getBody());
    }

    @Test
    @DisplayName("dispatch: roster IQ → RosterModule обновляет контакты")
    void dispatchRoster() throws Exception {
        dispatch("<iq type='result' id='r1'>"
                + "<query xmlns='jabber:iq:roster'>"
                + "<item jid='a@example.com' name='Alice'/>"
                + "</query></iq>");

        RosterModule roster = client.getRosterModule();
        Contact c = roster.getContacts().get("a@example.com");
        assertNotNull(c);
        assertEquals("Alice", c.getName());
    }

    @Test
    @DisplayName("dispatch: битый XML → не падает")
    void dispatchBrokenXml() throws Exception {
        assertDoesNotThrow(() -> dispatch("<not-xml"));
    }

    @Test
    @DisplayName("dispatch: пустая станза → не падает")
    void dispatchEmpty() throws Exception {
        assertDoesNotThrow(() -> dispatch(""));
    }

    @Test
    @DisplayName("dispatch: XML-пролог без станзы → не падает")
    void dispatchOnlyProlog() throws Exception {
        assertDoesNotThrow(() -> dispatch("<?xml version='1.0'?>"));
    }

    @Test
    @DisplayName("dispatch: неизвестная станза → не падает, события нет")
    void dispatchUnknown() throws Exception {
        final boolean[] got = {false};
        client.getEventBus().addListener(ev -> got[0] = true);
        dispatch("<unknown-element/>");
        assertFalse(got[0]);
    }
}