package ru.miacomsoft.xmpp;

public class XmppException extends Exception {
    public XmppException(String message) { super(message); }
    public XmppException(String message, Throwable cause) { super(message, cause); }
}