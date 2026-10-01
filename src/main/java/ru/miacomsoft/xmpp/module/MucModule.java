package ru.miacomsoft.xmpp.module;

/**
 * XEP-0045 — Multi-User Chat. Реализовать при необходимости.
 * Каркас показывает, как добавлять новые возможности:
 *   - наследуемся от XmppModule,
 *   - реализуем handle() и onConnected(),
 *   - регистрируем в XmppClient при сборке.
 */
public class MucModule extends XmppModule {

    @Override
    public String getName() { return "muc"; }

    // TODO: реализовать join(), leave(), sendGroupMessage() и т.д.
}