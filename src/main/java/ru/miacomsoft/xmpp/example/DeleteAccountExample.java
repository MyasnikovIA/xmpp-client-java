package ru.miacomsoft.xmpp.example;

import ru.miacomsoft.xmpp.XmppClient;

/**
 * Пример удаления собственного аккаунта (XEP-0077 §3.2).
 *
 * Запуск:
 *   XMPP_JID=user@example.com XMPP_PASSWORD=secret \
 *   java ru.miacomsoft.xmpp.example.DeleteAccountExample
 */
public class DeleteAccountExample {

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("XMPP_HOST", "smwrap.ru");
        int port = Integer.parseInt(
                System.getenv().getOrDefault("XMPP_PORT", "5222"));
        String jid = System.getenv("XMPP_JID");
        String password = System.getenv("XMPP_PASSWORD");

        if (jid == null || password == null) {
            System.err.println("Задайте XMPP_JID и XMPP_PASSWORD");
            System.exit(1);
            return;
        }

        XmppClient client = new XmppClient(host, port);
        try {
            client.connect(jid, password);
            System.out.println("Подключено как " + client.getJid());
            System.out.println("Удаляем аккаунт...");
            client.deleteAccount();
            System.out.println("✓ Аккаунт удалён.");
        } finally {
            client.close();
        }
    }
}