package ru.miacomsoft.xmpp.module;

import ru.miacomsoft.xmpp.XmppClient;
import ru.miacomsoft.xmpp.XmppException;
import org.w3c.dom.Element;

public abstract class XmppModule {
    public XmppClient client;

    /** Привязать модуль к клиенту. Вызывается из XmppClient.registerModule(). */
    public void setClient(XmppClient client) {
        this.client = client;
    }

    public XmppClient getClient() {
        return client;
    }

    public abstract String getName();

    public void onConnected() {}

    public void onDisconnected() {}

    public boolean handle(Element stanza) throws XmppException { return false; }
}