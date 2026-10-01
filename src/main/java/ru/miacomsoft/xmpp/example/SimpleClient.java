package ru.miacomsoft.xmpp.example;

import ru.miacomsoft.xmpp.XmppClient;
import ru.miacomsoft.xmpp.module.Contact;
import ru.miacomsoft.xmpp.module.MamModule;

import java.util.Scanner;

public class SimpleClient {

    public static void main(String[] args) throws Exception {
        String host = "smwrap.ru";
        int port = 5222;
        String jid = "myasnikovia@smwrap.ru";
        String password = "PASS";

        XmppClient client = new XmppClient(host, port);

        // ---- Слушатели событий ----
        client.getEventBus().addMessageListener(ev -> {
            switch (ev.getKind()) {
                case RECEIVED:
                    System.out.println("📩 [" + ev.getFrom() + "]: "
                            + ev.getMessage().getBody());
                    break;
                case SENT:
                    System.out.println("📤 Отправлено: "
                            + ev.getMessage().getBody());
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

        client.getEventBus().addPresenceListener(ev -> {
            System.out.println("👤 " + ev.getFrom()
                    + (ev.isOnline() ? " онлайн" : " офлайн"));
        });

        // ---- Подключение ----
        System.out.println("Подключаемся к " + host + ":" + port);
        client.connect(jid, password);

        System.out.println("Контакты:");
        for (Contact c : client.getRosterModule().getContacts().values()) {
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
                client.setCurrentChat(to);
                System.out.println("Чат с " + to);
                continue;
            }

            if (line.startsWith("/history ")) {
                int n = Integer.parseInt(line.substring(9).trim());
                if (client.getCurrentChat() != null) {
                    client.getModule(MamModule.class)
                            .requestMam(client.getCurrentChat(), n);
                    System.out.println("Запрошена история");
                } else {
                    System.out.println("Сначала /to <jid>");
                }
                continue;
            }

            if (client.getCurrentChat() != null) {
                client.sendMessage(client.getCurrentChat(), line);
            } else {
                System.out.println("Сначала выберите чат: /to <jid>");
            }
        }

        client.disconnect();
        System.out.println("Пока!");
    }
}