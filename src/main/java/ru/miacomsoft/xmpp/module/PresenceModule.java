package ru.miacomsoft.xmpp.module;

import ru.miacomsoft.xmpp.XmppException;
import ru.miacomsoft.xmpp.event.PresenceEvent;
import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.w3c.dom.Element;

public class PresenceModule extends XmppModule {

    @Override
    public String getName() { return "presence"; }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PresenceModule.class);

    @Override
    public boolean handle(Element stanza) throws XmppException {
        if (!"presence".equals(XmlUtil.localName(stanza))) return false;

        String from = stanza.getAttribute("from");
        if (from == null || from.isEmpty()) return false;
        String bareFrom = from.contains("/") ? from.substring(0, from.indexOf('/')) : from;

        String type = XmlUtil.attr(stanza, "type");
        if ("error".equals(type)) return true;

        // Запрос на подписку — НЕ подтверждаем автоматически.
        // Публикуем событие, решение принимает приложение.
        if ("subscribe".equals(type)) {
            client.getEventBus().publish(new PresenceEvent(
                    bareFrom + "#subscribe", false));
            log.info("Входящий запрос на подписку от {} — ожидает решения", bareFrom);
            return true;
        }

        // Отмена подписки
        if ("unsubscribe".equals(type)) {
            client.getRosterModule().updatePresence(bareFrom, false);
            client.getEventBus().publish(new PresenceEvent(bareFrom, false));
            return true;
        }

        boolean online = !"unavailable".equals(type);
        client.getRosterModule().updatePresence(bareFrom, online);
        client.getEventBus().publish(new PresenceEvent(bareFrom, online));
        return true;
    }

    /**
     * Явное подтверждение подписки — вызывается приложением.
     */
    public void approveSubscription(String bareJid) {
        try {
            client.sendRaw("<presence to=\"" + XmlUtil.escape(bareJid)
                    + "\" type=\"subscribed\"/>");
            client.getRosterModule().addContact(bareJid);
        } catch (Exception e) {
            log.warn("Не удалось подтвердить подписку {}: {}", bareJid, e.getMessage());
        }
    }

    /**
     * Отклонение подписки.
     */
    public void denySubscription(String bareJid) {
        try {
            client.sendRaw("<presence to=\"" + XmlUtil.escape(bareJid)
                    + "\" type=\"unsubscribed\"/>");
        } catch (Exception e) {
            log.warn("Не удалось отклонить подписку {}: {}", bareJid, e.getMessage());
        }
    }

    public void sendInitialPresence() {
        try { client.sendRaw("<presence/>"); } catch (Exception ignored) {}
    }

    public void sendUnavailable() {
        try { client.sendRaw("<presence type=\"unavailable\"/>"); } catch (Exception ignored) {}
    }
}