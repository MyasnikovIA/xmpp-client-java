# xmpp-client-java
Java библиотека для работы с ICQ сервером Jabber 

# XMPP Client Java Library

Лёгкая расширяемая библиотека XMPP-клиента на Java для работы с сервером **Prosody** (и любым другим XMPP-сервером, поддерживающим стандартные XEP).

Проверено на:
- **Сервер:** Prosody 0.11.9 (Docker, `prosody/prosody:latest`) [InstallProsodyDockerServer.md](InstallProsodyDockerServer.md)
- **Клиент:** Java 21 (AxiomJDK 21)
- **ОС:** Windows 10 / Linux Mint

---

## ✨ Возможности

- **TCP + STARTTLS** — защищённое соединение с сервером
- **SASL** — аутентификация по `PLAIN` и `SCRAM-SHA-1`
- **BIND + SESSION** — привязка ресурса и установка сессии
- **Roster** — список контактов, подписки
- **Presence** — онлайн/офлайн статусы
- **Обмен сообщениями** — отправка и приём `<message type="chat">`
- **XEP-0184** (Message Delivery Receipts) — подтверждения доставки
- **XEP-0333** (Chat Markers) — отметки о прочтении
- **XEP-0085** (Chat State Notifications) — индикатор «печатает…»
- **XEP-0313** (MAM) — история сообщений
- **Расширяемая архитектура** — новые XEP добавляются как модули

---

## 🏗 Архитектура

```
ru.miacomsoft.xmpp/
├── XmppClient.java                — главный класс клиента
├── XmppException.java             — исключение
├── connection/
│   ├── XmppConnection.java        — TCP/TLS + парсер XML-станз
│   └── SaslAuthenticator.java     — PLAIN + SCRAM-SHA-1
├── module/
│   ├── XmppModule.java            — базовый класс модуля
│   ├── RosterModule.java          — список контактов
│   ├── PresenceModule.java        — онлайн/офлайн
│   ├── MessageModule.java         — сообщения
│   ├── MamModule.java             — история (XEP-0313)
│   └── MucModule.java             — групповые чаты (заглушка)
├── model/
│   ├── ChatMessage.java           — модель сообщения
│   ├── Contact.java               — модель контакта
│   └── MessageStatus.java         — статус (SENT/DELIVERED/READ/...)
├── event/
│   ├── XmppEvent.java             — базовое событие
│   ├── MessageEvent.java          — событие сообщения
│   ├── PresenceEvent.java         — событие presence
│   └── ConnectionEvent.java       — событие соединения
├── listener/
│   └── EventBus.java              — публикация событий
└── example/
└── SimpleClient.java          — пример использования
```

**Ключевая идея:** `XmppClient` **не знает** о конкретных XEP. Вся логика — в модулях, наследующих `XmppModule`. Модули регистрируются в конструкторе `XmppClient`.

---

## 📦 Установка

### Maven

`pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.13</version>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.5.6</version>
    </dependency>
</dependencies>

<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

### Сборка

```bash
mvn clean compile
```

---

## 🚀 Быстрый старт

### 1. Подключение

```java
import ru.miacomsoft.xmpp.XmppClient;

XmppClient client = new XmppClient("smwrap.ru", 5222);
client.connect("user@smwrap.ru", "password");
```

### 2. Обработка событий

```java
client.getEventBus().addMessageListener(ev -> {
    switch (ev.getKind()) {
        case RECEIVED:
            System.out.println("📩 " + ev.getFrom() + ": " + ev.getMessage().getBody());
            break;
        case SENT:
            System.out.println("📤 Отправлено");
            break;
        case DELIVERED:
            System.out.println("✓✓ Доставлено");
            break;
        case READ:
            System.out.println("👁 Прочитано");
            break;
        case COMPOSING:
            System.out.println("💬 " + ev.getFrom() + " печатает...");
            break;
    }
});

client.getEventBus().addPresenceListener(ev -> {
    System.out.println("👤 " + ev.getFrom()
            + (ev.isOnline() ? " онлайн" : " офлайн"));
});
```

### 3. Отправка сообщений

```java
client.setCurrentChat("friend@smwrap.ru");
client.sendMessage("friend@smwrap.ru", "Привет!");
```

### 4. Работа с контактами

```java
for (Contact c : client.getRosterModule().getContacts().values()) {
    System.out.println(c.getJid() + (c.isOnline() ? " ●" : " ○"));
}
```

### 5. История сообщений (MAM)

```java
client.getModule(MamModule.class).requestMam("friend@smwrap.ru", 50);
```

### 6. Отключение

```java
client.disconnect();
```

---

## 📋 Полный пример

См. `ru.miacomsoft.xmpp.example.SimpleClient`:

```java
public class SimpleClient {
    public static void main(String[] args) throws Exception {
        XmppClient client = new XmppClient("smwrap.ru", 5222);

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
            }
        });

        client.connect("user@smwrap.ru", "password");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.equals("/quit")) break;
            if (line.startsWith("/to ")) {
                client.setCurrentChat(line.substring(4).trim());
                continue;
            }
            if (client.getCurrentChat() != null) {
                client.sendMessage(client.getCurrentChat(), line);
            }
        }

        client.disconnect();
    }
}
```

**Команды в примере:**
- `/to <jid>` — открыть чат с пользователем
- `/history <n>` — запросить последние N сообщений
- `/quit` — выход
- `<текст>` — отправить сообщение в текущий чат

---

## 🔧 Расширение

### Добавление нового XEP

Создайте класс-наследник `XmppModule`:

```java
public class MyModule extends XmppModule {

    @Override
    public String getName() { return "my-module"; }

    @Override
    public void onConnected() {
        // вызывается при подключении
    }

    @Override
    public boolean handle(Element stanza) throws XmppException {
        // обработать входящую станзу
        if (!"my-element".equals(stanza.getLocalName())) return false;
        // ...
        return true;   // станза обработана
    }
}
```

Зарегистрируйте в `XmppClient`:

```java
public XmppClient(String host, int port) {
    // ...
    registerModule(new MyModule());
}
```

### Готовые точки расширения

| XEP | Назначение | Класс |
|---|---|---|
| XEP-0045 | Групповые чаты (MUC) | `MucModule` (заглушка) |
| XEP-0363 | HTTP Upload (файлы) | создать `HttpUploadModule` |
| XEP-0424 | Удаление сообщений | создать `RetractionModule` |
| XEP-0280 | Carbons (синхронизация) | создать `CarbonsModule` |
| XEP-0163 | PEP (аватары) | создать `PepModule` |
| XEP-0199 | Ping | создать `PingModule` |

---

## ⚙️ Настройка

### Свой TrustManager для self-signed сертификата

По умолчанию библиотека **принимает любой сертификат** (для тестового сервера с self-signed). См. `XmppConnection.startTls()`.

**Для продакшена** замените на:

```java
TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init((KeyStore) null);   // системный truststore
SSLContext ctx = SSLContext.getInstance("TLS");
ctx.init(null, tmf.getTrustManagers(), null);
```

### Логирование

Библиотека использует SLF4J. Настройте уровень в `logback.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} — %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="ru.miacomsoft.xmpp" level="DEBUG"/>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

**Уровни:**
- `TRACE` — посимвольный разбор XML (очень много)
- `DEBUG` — все станзы (входящие/исходящие)
- `INFO` — ключевые события (подключение, bind, SASL)

---

## 🐛 Известные проблемы

### SSLHandshakeException: PKIX path building failed

Self-signed сертификат сервера не принимается JVM. Решение:
1. Заменить TrustManager на «доверять всем» (см. `XmppConnection.startTls()`).
2. Или импортировать сертификат в truststore JVM:
   ```bash
   keytool -importcert -alias smwrap -file smwrap.ru.crt \
           -keystore "%JAVA_HOME%\lib\security\cacerts" \
           -storepass changeit -noprompt
   ```

### SASL failure: Invalid username or password

В SASL `authcid` должен быть **только username** (без `@domain`). См. `SaslAuthenticator.extractUsername()`.

### Таймаут чтения после `<stream:stream>`

Парсер станз должен корректно обрабатывать **незакрытый `<stream:stream>`** и **слипшиеся станзы**. См. `XmppConnection.tryExtractStanza()`.

---

## 📚 Требования к серверу

- **Prosody 0.11+** или любой XMPP-сервер с поддержкой:
    - `SASL PLAIN` и/или `SCRAM-SHA-1`
    - `STARTTLS` (опционально, если настроен `c2s_require_encryption = false`)
    - `jabber:iq:roster`
    - `urn:xmpp:mam:2` (для истории)
- Домен должен резолвиться в IP сервера.
- Порт 5222 (c2s) должен быть открыт.

---

## 📄 Лицензия

MIT

---

## 🤝 Вклад

Приветствуются pull request'ы:
- Добавление новых XEP (MUC, HTTP Upload, Retraction, Carbons, PEP)
- Улучшение парсера XML-станз
- Поддержка WebSocket-транспорта (альтернатива TCP)
- Юнит-тесты

---

## 📞 Контакты

По вопросам и багам — открывайте Issue в репозитории.
```

---

## Что сделано в проекте (по состоянию на текущий момент)

**Работает:**
- ✅ TCP-соединение с Prosody
- ✅ STARTTLS с self-signed сертификатом
- ✅ SASL PLAIN и SCRAM-SHA-1
- ✅ BIND ресурса
- ✅ SESSION
- ✅ Получение ростера
- ✅ Приём presence (онлайн/офлайн)
- ✅ Отправка/приём сообщений
- ✅ Парсер XML-станз (обрабатывает слипшиеся станзы и незакрытый `<stream:stream>`)

**В процессе:**
- ⚠️ Таймаут чтения после idle (`22:13:37 WARN Ошибка чтения: Таймаут чтения`)

Это **нормальное поведение** — после `presence` сервер **молчит**, пока не придёт новое сообщение. **`soTimeout=15_000`** срабатывает, **`readLoop` завершается**. **Решение:** обернуть `readStanza` в `readLoop` в **бесконечный цикл с `continue` при таймауте**:

```java
private void readLoop() {
    while (running) {
        try {
            String xml = connection.readStanza();
            if (xml == null || xml.isEmpty()) continue;
            // ...
            dispatch(xml);
        } catch (XmppException e) {
            if (e.getMessage().contains("Таймаут")) {
                continue;   // просто продолжаем — это нормально
            }
            if (running) {
                log.warn("Ошибка чтения: {}", e.getMessage());
                eventBus.publish(new ConnectionEvent(
                        ConnectionEvent.Kind.ERROR, e.getMessage()));
            }
            break;
        }
    }
}
```
