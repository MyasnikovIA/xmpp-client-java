package ru.miacomsoft.xmpp.module;

import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RosterModule extends XmppModule {

    private final Map<String, Contact> contacts = new ConcurrentHashMap<>();

    @Override
    public String getName() { return "roster"; }

    @Override
    public boolean handle(Element stanza) {
        if (!"iq".equals(stanza.getLocalName())) return false;

        Element query = XmlUtil.firstChild(stanza, "query");
        if (query == null) return false;
        if (!"jabber:iq:roster".equals(query.getAttribute("xmlns"))) return false;

        NodeList items = query.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String jid = item.getAttribute("jid");
            if (jid == null || jid.isEmpty()) continue;
            if (jid.equals(client.getBareJid())) continue;

            String name = XmlUtil.attr(item, "name");
            Contact c = contacts.computeIfAbsent(jid, Contact::new);
            if (name != null) c.setName(name);
        }
        return true;
    }

    public void requestRoster() {
        try {
            String xml = "<iq type=\"get\" id=\"roster_" + System.currentTimeMillis()
                    + "\" xmlns=\"jabber:client\">"
                    + "<query xmlns=\"jabber:iq:roster\"/></iq>";
            client.sendRaw(xml);
        } catch (Exception ignored) {}
    }

    public void addContact(String jid) {
        contacts.computeIfAbsent(jid, Contact::new);
    }

    public void updatePresence(String jid, boolean online) {
        Contact c = contacts.computeIfAbsent(jid, Contact::new);
        c.setOnline(online);
    }

    public Map<String, Contact> getContacts() { return contacts; }
}