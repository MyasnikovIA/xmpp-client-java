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
        for (Consumer<XmppEvent> l : globalListeners) {
            try {
                l.accept(e);
            } catch (Exception ex) {
                log.warn("Ошибка в global listener: {}", ex.getMessage(), ex);
            }
        }
        if (e instanceof MessageEvent) {
            MessageEvent me = (MessageEvent) e;
            for (Consumer<MessageEvent> l : messageListeners) {
                try {
                    l.accept(me);
                } catch (Exception ex) {
                    log.warn("Ошибка в message listener: {}", ex.getMessage(), ex);
                }
            }
        } else if (e instanceof PresenceEvent) {
            PresenceEvent pe = (PresenceEvent) e;
            for (Consumer<PresenceEvent> l : presenceListeners) {
                try {
                    l.accept(pe);
                } catch (Exception ex) {
                    log.warn("Ошибка в presence listener: {}", ex.getMessage(), ex);
                }
            }
        } else if (e instanceof ConnectionEvent) {
            ConnectionEvent ce = (ConnectionEvent) e;
            for (Consumer<ConnectionEvent> l : connectionListeners) {
                try {
                    l.accept(ce);
                } catch (Exception ex) {
                    log.warn("Ошибка в connection listener: {}", ex.getMessage(), ex);
                }
            }
        }
    }
}