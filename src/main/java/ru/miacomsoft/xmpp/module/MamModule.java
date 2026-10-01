package ru.miacomsoft.xmpp.module;

import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.w3c.dom.Element;

import java.time.Instant;

public class MamModule extends XmppModule {

    private String currentJid;

    @Override
    public String getName() { return "mam"; }

    @Override
    public boolean handle(Element stanza) {
        if (!"message".equals(stanza.getLocalName())) return false;

        Element result = XmlUtil.firstChild(stanza, "result");
        if (result == null) return false;

        Element forwarded = XmlUtil.firstChild(result, "forwarded");
        if (forwarded == null) return false;

        Element msg = XmlUtil.firstChild(forwarded, "message");
        if (msg == null) return false;

        Element delay = XmlUtil.firstChild(forwarded, "delay");
        long ts = delay != null
                ? Instant.parse(delay.getAttribute("stamp")).toEpochMilli()
                : System.currentTimeMillis();

        String from = msg.getAttribute("from");
        String to = msg.getAttribute("to");
        String bareFrom = from.contains("/") ? from.substring(0, from.indexOf('/')) : from;
        String bareTo = to.contains("/") ? to.substring(0, to.indexOf('/')) : to;

        Element bodyEl = XmlUtil.firstChild(msg, "body");
        if (bodyEl == null) return false;
        String body = bodyEl.getTextContent();
        if (body == null || body.isEmpty()) return false;

        String id = msg.getAttribute("id");
        if (id.isEmpty()) id = bareFrom + "_" + ts;

        String chatJid = bareFrom.equals(client.getBareJid()) ? bareTo : bareFrom;
        if (chatJid == null || chatJid.equals(client.getBareJid())) return false;

        ChatMessage cm = new ChatMessage(id, bareFrom, bareTo, body, ts);
        cm.setStatus(MessageStatus.RECEIVED);
        client.storeMessage(chatJid, cm);
        return true;
    }

    public void requestMam(String jid, int max) {
        currentJid = jid;
        String id = "mam_" + System.currentTimeMillis();
        String xml = "<iq type=\"set\" id=\"" + id + "\" xmlns=\"jabber:client\">"
                + "<query xmlns=\"urn:xmpp:mam:2\">"
                + "<x xmlns=\"jabber:x:data\" type=\"submit\">"
                + "<field var=\"FORM_TYPE\" type=\"hidden\"><value>urn:xmpp:mam:2</value></field>"
                + "<field var=\"with\"><value>" + XmlUtil.escape(jid) + "</value></field>"
                + "</x>"
                + "<set xmlns=\"http://jabber.org/protocol/rsm\">"
                + "<before/><max>" + max + "</max></set>"
                + "</query></iq>";
        try { client.sendRaw(xml); } catch (Exception ignored) {}
    }
}