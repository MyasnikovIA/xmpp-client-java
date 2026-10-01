package ru.miacomsoft.xmpp.event;

public class ConnectionEvent extends XmppEvent {
    public enum Kind { CONNECTED, DISCONNECTED, AUTH_FAILED, ERROR }
    private final Kind kind;
    private final String info;

    public ConnectionEvent(Kind kind, String info) {
        this.kind = kind;
        this.info = info;
    }

    public Kind getKind() { return kind; }
    public String getInfo() { return info; }
}