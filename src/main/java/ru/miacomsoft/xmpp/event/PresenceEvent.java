package ru.miacomsoft.xmpp.event;

public class PresenceEvent extends XmppEvent {
    private final String from;
    private final boolean online;

    public PresenceEvent(String from, boolean online) {
        this.from = from;
        this.online = online;
    }

    public String getFrom() { return from; }
    public boolean isOnline() { return online; }
}