package ru.miacomsoft.xmpp.event;

public abstract class XmppEvent {
    private final long timestamp = System.currentTimeMillis();
    public long getTimestamp() { return timestamp; }
}