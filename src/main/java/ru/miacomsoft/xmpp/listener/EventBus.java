package ru.miacomsoft.xmpp.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.miacomsoft.xmpp.event.ConnectionEvent;
import ru.miacomsoft.xmpp.event.MessageEvent;
import ru.miacomsoft.xmpp.event.PresenceEvent;
import ru.miacomsoft.xmpp.event.XmppEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final List<Consumer<XmppEvent>> globalListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<MessageEvent>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PresenceEvent>> presenceListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ConnectionEvent>> connectionListeners = new CopyOnWriteArrayList<>();

    public void addListener(Consumer<XmppEvent> l) { globalListeners.add(l); }
    public void addMessageListener(Consumer<MessageEvent> l) { messageListeners.add(l); }
    public void addPresenceListener(Consumer<PresenceEvent> l) { presenceListeners.add(l); }
    public void addConnectionListener(Consumer<ConnectionEvent> l) { connectionListeners.add(l); }

    public void publish(XmppEvent e) {
        try {
            globalListeners.forEach(l -> l.accept(e));
            if (e instanceof MessageEvent) {
                MessageEvent me = (MessageEvent) e;
                messageListeners.forEach(l -> l.accept(me));
            } else if (e instanceof PresenceEvent) {
                PresenceEvent pe = (PresenceEvent) e;
                presenceListeners.forEach(l -> l.accept(pe));
            } else if (e instanceof ConnectionEvent) {
                ConnectionEvent ce = (ConnectionEvent) e;
                connectionListeners.forEach(l -> l.accept(ce));
            }
        } catch (Exception ex) {
            log.warn("Ошибка в listener: {}", ex.getMessage());
        }
    }
}