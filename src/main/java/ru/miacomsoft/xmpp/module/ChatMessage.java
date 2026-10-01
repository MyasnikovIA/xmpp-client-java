package ru.miacomsoft.xmpp.module;

public class ChatMessage {
    private final String id;
    private final String from;
    private final String to;
    private final String body;
    private final long timestamp;
    private MessageStatus status;
    private boolean deleted;

    public ChatMessage(String id, String from, String to, String body, long timestamp) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.body = body;
        this.timestamp = timestamp;
        this.status = MessageStatus.RECEIVED;
    }

    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getBody() { return body; }
    public long getTimestamp() { return timestamp; }
    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    @Override
    public String toString() {
        return "[" + status + "] " + from + " -> " + to + ": " + body;
    }
}