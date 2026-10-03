package ru.miacomsoft.xmpp.example;

import ru.miacomsoft.xmpp.RegistrationClient;
import ru.miacomsoft.xmpp.XmppException;
import ru.miacomsoft.xmpp.module.RegistrationForm;

import java.util.Scanner;

/**
 * Пример регистрации нового XMPP-аккаунта через XEP-0077.
 *
 * Запуск:
 *   XMPP_HOST=smwrap.ru XMPP_PORT=5222 \
 *   java ru.miacomsoft.xmpp.example.RegisterUserExample
 *
 * Или с аргументами:
 *   java ... RegisterUserExample smwrap.ru 5222 newuser secretpass
 */
public class RegisterUserExample {

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("XMPP_HOST", "smwrap.ru");
        int port = Integer.parseInt(
                System.getenv().getOrDefault("XMPP_PORT", "5222"));

        // Домен JID может отличаться от хоста подключения.
        // Например, host=192.168.1.10, domain=example.com
        String domain = System.getenv().getOrDefault("XMPP_DOMAIN", host);

        String username = null;
        String password = null;
        if (args.length >= 4) {
            host = args[0];
            port = Integer.parseInt(args[1]);
            domain = host;
            username = args[2];
            password = args[3];
        }

        RegistrationClient reg = new RegistrationClient(host, port);
        reg.setDomain(domain);

        // Для self-signed сертификатов — включить вручную.
        // reg.setTrustAllCertificates(true);

        try {
            // --- Шаг 1: получаем форму регистрации ---
            System.out.println("Запрашиваем форму регистрации у " + host + ":" + port
                    + " (domain=" + domain + ")...");
            RegistrationForm form;
            try {
                form = reg.fetchForm();
            } catch (XmppException e) {
                System.err.println("Не удалось получить форму: " + e.getMessage());
                System.err.println("Возможно, сервер запретил регистрацию (XEP-0077).");
                return;
            }

            System.out.println("Форма получена.");
            if (!form.getInstructions().isEmpty()) {
                System.out.println("Инструкции сервера: " + form.getInstructions());
            }

            System.out.println("Поля формы:");
            for (RegistrationForm.Field f : form.getFieldMap().values()) {
                System.out.println("  - " + f.getVar()
                        + " [" + f.getType() + "]"
                        + (f.isRequired() ? " *обязательное" : "")
                        + (f.getOptions().isEmpty() ? "" : " опции=" + f.getOptions()));
            }

            // --- Шаг 2: если username/password не заданы — спрашиваем интерактивно ---
            Scanner scanner = new Scanner(System.in);
            if (username == null) {
                System.out.print("Введите username: ");
                username = scanner.nextLine().trim();
            }
            if (password == null) {
                System.out.print("Введите password: ");
                password = scanner.nextLine().trim();
            }

            if (form.hasField("username")) form.setValue("username", username);
            if (form.hasField("password")) form.setValue("password", password);

            // Если сервер требует другие обязательные поля — спросим.
            for (RegistrationForm.Field f : form.getFieldMap().values()) {
                if ("username".equals(f.getVar()) || "password".equals(f.getVar())) continue;
                if (!f.isRequired()) continue;
                System.out.print("Поле '" + f.getLabel() + "' (" + f.getVar() + "): ");
                String v = scanner.nextLine().trim();
                form.setValue(f.getVar(), v);
            }

            // --- Шаг 3: отправляем ---
            System.out.println("Отправляем форму...");
            try {
                reg.submit(form);
                System.out.println("✓ Аккаунт " + username + "@" + domain + " создан!");
                System.out.println("Теперь можно войти через XmppClient:");
                System.out.println("  new XmppClient(\"" + host + "\", " + port + ")");
                System.out.println("    .connect(\"" + username + "@" + domain + "\", \"***\");");
            } catch (XmppException e) {
                System.err.println("✗ Регистрация не удалась: " + e.getMessage());
            }

        } finally {
            reg.close();
        }
    }
}