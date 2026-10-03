package ru.miacomsoft.xmpp.example;

import ru.miacomsoft.xmpp.XmppClient;
import ru.miacomsoft.xmpp.module.Contact;
import ru.miacomsoft.xmpp.module.MamModule;

import java.util.Scanner;

public class SimpleClient {

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("XMPP_HOST", "smwrap.ru");
        String portStr = System.getenv().getOrDefault("XMPP_PORT", "5222");
        int port = Integer.parseInt(portStr);
        String jid = System.getenv("XMPP_JID");
        String password = System.getenv("XMPP_PASSWORD");


        if (jid == null || password == null) {
            // Fallback: аргументы командной строки
            if (args.length >= 2) {
                jid = args[0];
                password = args[1];
            }
        }
        if (jid == null || password == null) {
            System.err.println("Задайте XMPP_JID и XMPP_PASSWORD (или передайте как аргументы)");
            System.exit(1);
            return;
        }

        XmppClient xmppClient = new XmppClient(host, port);

        // ---- Слушатели событий ----
        xmppClient.getEventBus().addMessageListener(ev -> {
            switch (ev.getKind()) {
                case RECEIVED:
                    System.out.println("📩 [" + ev.getFrom() + "]: " + ev.getMessage().getBody());
                    break;
                case SENT:
                    System.out.println("📤 Отправлено: " + ev.getMessage().getBody());
                    break;
                case DELIVERED:
                    System.out.println("✓✓ Доставлено для " + ev.getFrom());
                    break;
                case READ:
                    System.out.println("👁️ Прочитано для " + ev.getFrom());
                    break;
                case COMPOSING:
                    System.out.println("💬 " + ev.getFrom() + " печатает...");
                    break;
                case PAUSED:
                    System.out.println("💤 " + ev.getFrom() + " перестал печатать");
                    break;
            }
        });

        xmppClient.getEventBus().addPresenceListener(ev -> {
            System.out.println("👤 " + ev.getFrom()
                    + (ev.isOnline() ? " онлайн" : " офлайн"));
        });

        // ---- Подключение ----
        System.out.println("Подключаемся к " + host + ":" + port);
        try {
            xmppClient.connect(jid, password);

            System.out.println("Контакты:");
            for (Contact c : xmppClient.getRosterModule().getContacts().values()) {
                System.out.println("  " + c.getJid()
                        + (c.isOnline() ? " ●" : " ○"));
            }

            // ---- Интерактив ----
            Scanner scanner = new Scanner(System.in);
            System.out.println("\nКоманды:");
            System.out.println("  /to <jid>      — открыть чат");
            System.out.println("  /history <n>   — история (MAM)");
            System.out.println("  /quit          — выход");
            System.out.println("  <текст>        — отправить сообщение");

            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                if (line.equals("/quit")) break;

                if (line.startsWith("/to ")) {
                    String to = line.substring(4).trim();
                    xmppClient.setCurrentChat(to);
                    System.out.println("Чат с " + to);
                    continue;
                }

                if (line.startsWith("/history ")) {
                    int n = Integer.parseInt(line.substring(9).trim());
                    if (xmppClient.getCurrentChat() != null) {
                        xmppClient.getModule(MamModule.class)
                                .requestMam(xmppClient.getCurrentChat(), n);
                        System.out.println("Запрошена история");
                    } else {
                        System.out.println("Сначала /to <jid>");
                    }
                    continue;
                }

                if (xmppClient.getCurrentChat() != null) {
                    xmppClient.sendMessage(xmppClient.getCurrentChat(), line);
                } else {
                    System.out.println("Сначала выберите чат: /to <jid>");
                }
            }
        } finally {
            xmppClient.disconnect();
            System.out.println("Пока!");
        }
    }

}