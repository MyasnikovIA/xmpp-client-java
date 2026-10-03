package ru.miacomsoft.xmpp.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.miacomsoft.xmpp.event.ConnectionEvent;
import ru.miacomsoft.xmpp.event.MessageEvent;
import ru.miacomsoft.xmpp.event.PresenceEvent;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    // ---------- доставка ----------

    @Test
    @DisplayName("EventBus: messageListener получает только MessageEvent")
    void messageListenerReceivesMessage() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.addMessageListener(e -> count.incrementAndGet());

        bus.publish(new MessageEvent(MessageEvent.Kind.RECEIVED, null, "a@b"));
        bus.publish(new PresenceEvent("a@b", true));
        bus.publish(new ConnectionEvent(ConnectionEvent.Kind.CONNECTED, "ok"));

        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("EventBus: presenceListener получает только PresenceEvent")
    void presenceListenerReceivesPresence() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.addPresenceListener(e -> count.incrementAndGet());

        bus.publish(new PresenceEvent("a@b", true));
        bus.publish(new MessageEvent(MessageEvent.Kind.RECEIVED, null, "a@b"));
        bus.publish(new PresenceEvent("a@b", false));

        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("EventBus: connectionListener получает только ConnectionEvent")
    void connectionListenerReceivesConnection() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.addConnectionListener(e -> count.incrementAndGet());

        bus.publish(new ConnectionEvent(ConnectionEvent.Kind.CONNECTED, "ok"));
        bus.publish(new MessageEvent(MessageEvent.Kind.SENT, null, "a@b"));
        bus.publish(new ConnectionEvent(ConnectionEvent.Kind.DISCONNECTED, "bye"));

        assertEquals(2, count.get());
    }

    // ---------- global ----------

    @Test
    @DisplayName("EventBus: globalListener получает все события")
    void globalListenerGetsAll() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.addListener(e -> count.incrementAndGet());

        bus.publish(new MessageEvent(MessageEvent.Kind.SENT, null, "a@b"));
        bus.publish(new PresenceEvent("a@b", true));
        bus.publish(new ConnectionEvent(ConnectionEvent.Kind.CONNECTED, "ok"));

        assertEquals(3, count.get());
    }

    // ---------- устойчивость ----------

    @Test
    @DisplayName("EventBus: упавший listener не мешает остальным (главный фикс)")
    void brokenListenerDoesNotStopOthers() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.addMessageListener(e -> { throw new RuntimeException("boom"); });
        bus.addMessageListener(e -> count.incrementAndGet());

        assertDoesNotThrow(() ->
                bus.publish(new MessageEvent(MessageEvent.Kind.SENT, null, "a@b")));
        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("EventBus: несколько упавших listener'ов — все остальные вызваны")
    void multipleBrokenListeners() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.addListener(e -> { throw new RuntimeException("boom1"); });
        bus.addListener(e -> count.incrementAndGet());
        bus.addListener(e -> { throw new RuntimeException("boom2"); });
        bus.addListener(e -> count.incrementAndGet());

        bus.publish(new PresenceEvent("a@b", true));
        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("EventBus: пустой publish не падает")
    void emptyPublish() {
        EventBus bus = new EventBus();
        assertDoesNotThrow(() ->
                bus.publish(new ConnectionEvent(ConnectionEvent.Kind.CONNECTED, "x")));
    }
}