package ru.miacomsoft.xmpp.event;

import ru.miacomsoft.xmpp.module.ChatMessage;

public class MessageEvent extends XmppEvent {
    public enum Kind { RECEIVED, SENT, DELIVERED, READ, DELETED, COMPOSING, PAUSED }
    private final Kind kind;
    private final ChatMessage message;
    private final String from;

    public MessageEvent(Kind kind, ChatMessage message, String from) {
        this.kind = kind;
        this.message = message;
        this.from = from;
    }

    public Kind getKind() { return kind; }
    public ChatMessage getMessage() { return message; }
    public String getFrom() { return from; }
}