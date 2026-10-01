package ru.miacomsoft.xmpp.module;

public class Contact {
    private final String jid;
    private String name;
    private boolean online;
    private int unread;

    public Contact(String jid) {
        this.jid = jid;
        this.name = jid;
    }

    public String getJid() { return jid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public int getUnread() { return unread; }
    public void setUnread(int unread) { this.unread = unread; }
    public void incUnread() { this.unread++; }
    public void clearUnread() { this.unread = 0; }
}