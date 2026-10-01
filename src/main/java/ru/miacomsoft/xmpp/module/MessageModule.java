package ru.miacomsoft.xmpp.module;

import ru.miacomsoft.xmpp.event.MessageEvent;
import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class MessageModule extends XmppModule {

    @Override
    public String getName() { return "message"; }

    @Override
    public boolean handle(Element stanza) {
        if (!"message".equals(stanza.getLocalName())) return false;

        String type = XmlUtil.attr(stanza, "type");
        if ("error".equals(type) || "groupchat".equals(type)) return false;

        String from = stanza.getAttribute("from");
        String bareFrom = from.contains("/") ? from.substring(0, from.indexOf('/')) : from;

        // 1. Receipt — собеседник получил сообщение
        Element received = XmlUtil.firstChild(stanza, "received");
        if (received != null) {
            String id = received.getAttribute("id");
            client.getEventBus().publish(new MessageEvent(
                    MessageEvent.Kind.DELIVERED, null, bareFrom));
            client.markMessageStatus(bareFrom, id, MessageStatus.DELIVERED);
            return true;
        }

        // 2. Displayed — собеседник прочитал
        Element displayed = XmlUtil.firstChild(stanza, "displayed");
        if (displayed != null) {
            String id = displayed.getAttribute("id");
            client.markMessageStatus(bareFrom, id, MessageStatus.READ);
            client.getEventBus().publish(new MessageEvent(
                    MessageEvent.Kind.READ, null, bareFrom));
            return true;
        }

        // 3. Chat states — печатает
        NodeList children = stanza.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Element.ELEMENT_NODE) continue;
            Element c = (Element) children.item(i);
            String local = c.getLocalName();
            if ("composing".equals(local)) {
                client.getEventBus().publish(new MessageEvent(
                        MessageEvent.Kind.COMPOSING, null, bareFrom));
                return true;
            } else if ("paused".equals(local) || "active".equals(local)) {
                client.getEventBus().publish(new MessageEvent(
                        MessageEvent.Kind.PAUSED, null, bareFrom));
            }
        }

        // 4. Обычное сообщение с body
        Element bodyEl = XmlUtil.firstChild(stanza, "body");
        if (bodyEl == null) return false;
        String body = bodyEl.getTextContent();
        if (body == null || body.isEmpty()) return false;

        String id = stanza.getAttribute("id");
        if (id.isEmpty()) id = bareFrom + "_" + System.currentTimeMillis();

        // Отправляем receipt (получатель подтверждает доставку)
        sendReceipt(bareFrom, id);

        // Создаём сообщение
        ChatMessage msg = new ChatMessage(
                id, bareFrom, client.getJid(), body, System.currentTimeMillis());
        msg.setStatus(MessageStatus.RECEIVED);
        client.storeMessage(bareFrom, msg);

        // Если чат активен — отправляем displayed (прочитано)
        if (client.getCurrentChat() != null
                && client.getCurrentChat().equals(bareFrom)) {
            sendDisplayed(bareFrom, id);
        }

        client.getEventBus().publish(new MessageEvent(
                MessageEvent.Kind.RECEIVED, msg, bareFrom));
        return true;
    }

    public void sendMessage(String to, String body) {
        String id = client.getBareJid() + "_" + System.currentTimeMillis();
        String xml = "<message to=\"" + XmlUtil.escape(to) + "\" type=\"chat\" "
                + "id=\"" + XmlUtil.escape(id) + "\" xmlns=\"jabber:client\">"
                + "<body>" + XmlUtil.escape(body) + "</body>"
                + "<request xmlns=\"urn:xmpp:receipts\"/>"
                + "<markable xmlns=\"urn:xmpp:chat-markers:0\"/>"
                + "</message>";

        try {
            client.sendRaw(xml);
            ChatMessage msg = new ChatMessage(
                    id, client.getJid(), to, body, System.currentTimeMillis());
            msg.setStatus(MessageStatus.SENT);
            client.storeMessage(to, msg);
            client.getEventBus().publish(new MessageEvent(
                    MessageEvent.Kind.SENT, msg, to));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка отправки сообщения", e);
        }
    }

    public void sendReceipt(String to, String id) {
        String xml = "<message to=\"" + XmlUtil.escape(to)
                + "\" type=\"chat\" xmlns=\"jabber:client\">"
                + "<received xmlns=\"urn:xmpp:receipts\" id=\"" + XmlUtil.escape(id) + "\"/>"
                + "</message>";
        try { client.sendRaw(xml); } catch (Exception ignored) {}
    }

    public void sendDisplayed(String to, String id) {
        String xml = "<message to=\"" + XmlUtil.escape(to)
                + "\" type=\"chat\" xmlns=\"jabber:client\">"
                + "<displayed xmlns=\"urn:xmpp:chat-markers:0\" id=\""
                + XmlUtil.escape(id) + "\"/>"
                + "</message>";
        try { client.sendRaw(xml); } catch (Exception ignored) {}
    }

    public void sendComposing(String to, boolean composing) {
        String state = composing ? "composing" : "paused";
        String xml = "<message to=\"" + XmlUtil.escape(to)
                + "\" type=\"chat\" xmlns=\"jabber:client\">"
                + "<" + state + " xmlns=\"http://jabber.org/protocol/chatstates\"/>"
                + "</message>";
        try { client.sendRaw(xml); } catch (Exception ignored) {}
    }
}