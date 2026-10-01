package ru.miacomsoft.xmpp.module;

public enum MessageStatus {
    SENT,        // ✓ одна галочка — отправлено на сервер
    DELIVERED,   // ✓✓ серые — доставлено собеседнику
    READ,        // ✓✓ синие — прочитано
    RECEIVED,    // входящее
    DELETED      // удалено (XEP-0424)
}