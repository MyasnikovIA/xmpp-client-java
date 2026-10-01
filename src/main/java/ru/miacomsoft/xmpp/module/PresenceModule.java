package ru.miacomsoft.xmpp.module;

import ru.miacomsoft.xmpp.XmppException;
import ru.miacomsoft.xmpp.event.PresenceEvent;
import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.w3c.dom.Element;

public class PresenceModule extends XmppModule {

    @Override
    public String getName() { return "presence"; }

    @Override
    public boolean handle(Element stanza) throws XmppException {
        if (!"presence".equals(stanza.getLocalName())) return false;

        String from = stanza.getAttribute("from");
        if (from == null || from.isEmpty()) return false;
        String bareFrom = from.contains("/") ? from.substring(0, from.indexOf('/')) : from;

        String type = XmlUtil.attr(stanza, "type");
        if ("error".equals(type)) return true;

        if ("subscribe".equals(type)) {
            client.sendRaw("<presence to=\"" + XmlUtil.escape(bareFrom)
                    + "\" type=\"subscribed\"/>");
            client.sendRaw("<presence to=\"" + XmlUtil.escape(bareFrom)
                    + "\" type=\"subscribe\"/>");
            client.getRosterModule().addContact(bareFrom);
            return true;
        }

        boolean online = !"unavailable".equals(type);
        client.getRosterModule().updatePresence(bareFrom, online);
        client.getEventBus().publish(new PresenceEvent(bareFrom, online));
        return true;
    }

    public void sendInitialPresence() {
        try { client.sendRaw("<presence/>"); } catch (Exception ignored) {}
    }

    public void sendUnavailable() {
        try { client.sendRaw("<presence type=\"unavailable\"/>"); } catch (Exception ignored) {}
    }
}