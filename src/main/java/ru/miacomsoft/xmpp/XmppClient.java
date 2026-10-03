package ru.miacomsoft.xmpp;

import ru.miacomsoft.xmpp.connection.SaslAuthenticator;
import ru.miacomsoft.xmpp.connection.XmppConnection;
import ru.miacomsoft.xmpp.event.ConnectionEvent;
import ru.miacomsoft.xmpp.listener.EventBus;
import ru.miacomsoft.xmpp.module.*;

import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class XmppClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(XmppClient.class);

    private final String host;
    private final int port;
    private String jid;
    private String password;
    private String fullJid;
    private String bareJid;
    // Поле класса
    private volatile long lastActivity = System.currentTimeMillis();
    private static final long KEEPALIVE_INTERVAL_MS = 30_000;

    private XmppConnection connection;
    private final EventBus eventBus = new EventBus();
    private final List<XmppModule> modules = new CopyOnWriteArrayList<>();
    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();
    private volatile String currentChat;
    private volatile boolean running = false;
    private Thread readerThread;

    public XmppClient(String host, int port) {
        this.host = host;
        this.port = port;

        // Регистрация модулей — легко добавлять новые
        registerModule(new RosterModule());
        registerModule(new MessageModule());
        registerModule(new PresenceModule());
        registerModule(new MamModule());
        registerModule(new MucModule());
    }

    /**
     * Регистрация модуля. ВАЖНО: используем setClient() вместо прямого
     * присваивания m.client = this (из-за protected-доступа).
     */
    public void registerModule(XmppModule m) {
        m.setClient(this);
        modules.add(m);
    }

    public <T extends XmppModule> T getModule(Class<T> clazz) {
        for (XmppModule m : modules) {
            if (clazz.isInstance(m)) return clazz.cast(m);
        }
        return null;
    }

    // ============================================================
    // Подключение
    // ============================================================
    // ============================================================
    // Подключение
    // ============================================================
    public void connect(String jid, String password) throws Exception {
        this.jid = jid;
        this.password = password;
        this.bareJid = jid.contains("/") ? jid.substring(0, jid.indexOf('/')) : jid;

        connection = new XmppConnection(host, port);
        connection.open();

        // ------------------------------------------------------------
        // 1. Открываем XMPP-стрим — ОДНИМ write, БЕЗ \n после тега
        // ------------------------------------------------------------
        connection.send("<?xml version='1.0' encoding='UTF-8'?>"
                + "<stream:stream to='" + getDomain() + "' "
                + "xmlns='jabber:client' "
                + "xmlns:stream='http://etherx.jabber.org/streams' "
                + "version='1.0'>");

        // Читаем features в ЦИКЛЕ — сервер сначала присылает <stream:stream>,
        // потом <stream:features>. Пропускаем всё лишнее.
        String features = readUntilFeatures();
        log.info("Features (до TLS): {}", features);

        // ------------------------------------------------------------
        // 2. STARTTLS
        // ------------------------------------------------------------
        if (features.contains("starttls")) {
            connection.send("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

            String tlsResp = connection.readStanza();
            log.info("STARTTLS response: {}", tlsResp);

            if (!tlsResp.contains("proceed")) {
                throw new XmppException("Сервер не начал TLS");
            }

            connection.startTls();

            // Перезапускаем стрим после TLS
            connection.send("<stream:stream to='" + getDomain() + "' "
                    + "xmlns='jabber:client' "
                    + "xmlns:stream='http://etherx.jabber.org/streams' "
                    + "version='1.0'>");

            features = readUntilFeatures();
            log.info("Features (после TLS): {}", features);
        }

        // ------------------------------------------------------------
        // 3. SASL — сначала PLAIN (проще), потом SCRAM-SHA-1
        // ------------------------------------------------------------
        boolean auth = false;
        if (features.contains("PLAIN")) {
            log.info("Используем PLAIN");
            auth = saslPlain();
        } else if (features.contains("SCRAM-SHA-1")) {
            log.info("Используем SCRAM-SHA-1");
            auth = saslScram();
        } else {
            throw new XmppException("Сервер не предложил поддерживаемых SASL-механизмов");
        }
        if (!auth) throw new XmppException("SASL не удался");

        // ------------------------------------------------------------
        // 4. Restart stream после SASL
        // ------------------------------------------------------------
        connection.send("<stream:stream to='" + getDomain() + "' "
                + "xmlns='jabber:client' "
                + "xmlns:stream='http://etherx.jabber.org/streams' "
                + "version='1.0'>");

        String postAuthFeatures = readUntilFeatures();
        log.info("Features (после SASL): {}", postAuthFeatures);

        // ------------------------------------------------------------
        // 5. Bind resource
        // ------------------------------------------------------------
        String resource = "java-" + UUID.randomUUID().toString().substring(0, 8);
        connection.send("<iq type='set' id='bind_1'>"
                + "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>"
                + "<resource>" + resource + "</resource>"
                + "</bind></iq>");

        String bindResp = connection.readStanza();
        log.info("Bind response: {}", bindResp);

        Document doc = XmlUtil.parse(wrapForParsing(bindResp));
        Element jidEl = (Element) doc.getElementsByTagName("jid").item(0);
        if (jidEl == null) {
            throw new XmppException("Не получили JID в ответе bind: " + bindResp);
        }
        this.fullJid = jidEl.getTextContent();
        log.info("Bind OK: {}", fullJid);

        // ------------------------------------------------------------
        // 6. Session (если требуется сервером)
        // ------------------------------------------------------------
        if (postAuthFeatures.contains("<session") || postAuthFeatures.contains(":session")) {
            connection.send("<iq type='set' id='sess_1'>"
                    + "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/></iq>");

            String sessResp = connection.readStanza();
            log.info("Session response: {}", sessResp);
        }

        // ------------------------------------------------------------
        // 7. Стартуем reader loop
        // ------------------------------------------------------------
        running = true;
        readerThread = new Thread(this::readLoop, "xmpp-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        // Уведомляем модули
        for (XmppModule m : modules) {
            try { m.onConnected(); } catch (Exception e) {
                log.warn("Модуль {} упал в onConnected: {}", m.getName(), e.getMessage());
            }
        }

        // Presence + ростер
        getModule(PresenceModule.class).sendInitialPresence();
        getModule(RosterModule.class).requestRoster();

        eventBus.publish(new ConnectionEvent(
                ConnectionEvent.Kind.CONNECTED, "Подключено: " + fullJid));
    }



    /**
     * Читает станзы в цикле, пока не получит <stream:features>.
     * Пропускает <?xml?>, <stream:stream>, пустые фрагменты.
     */
    private String readUntilFeatures() throws XmppException {
        for (int i = 0; i < 20; i++) {
            String stanza = connection.readStanza();
            if (stanza == null) continue;

            String trimmed = stanza.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("<?xml")) continue;
            if (trimmed.startsWith("<stream:stream")) continue;

            if (trimmed.contains("<stream:features") || trimmed.contains("<features")) {
                return trimmed;
            }
            log.debug("readUntilFeatures: пропускаем станзу: {}", trimmed);
        }
        throw new XmppException("Не получили <stream:features> за 20 попыток");
    }

    // ============================================================
    // SASL
    // ============================================================
    private boolean saslPlain() throws XmppException {
        String auth = SaslAuthenticator.plain(bareJid, password);
        connection.send("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' "
                + "mechanism='PLAIN'>" + auth + "</auth>");

        String resp = connection.readStanza();
        log.info("SASL PLAIN response: {}", resp);

        if (resp.contains("<failure")) {
            log.error("SASL PLAIN failure: {}", resp);
            return false;
        }
        return resp.contains("<success");
    }

    private boolean saslScram() throws Exception {
        SaslAuthenticator.ScramSession scram =
                new SaslAuthenticator.ScramSession(bareJid);

        connection.send("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' "
                + "mechanism='SCRAM-SHA-1'>" + scram.clientFirstBase64() + "</auth>");

        String resp = connection.readStanza();
        log.info("SASL SCRAM challenge: {}", resp);

        if (resp.contains("<failure")) {
            log.error("SASL SCRAM failure на client-first: {}", resp);
            return false;
        }

        Document doc = XmlUtil.parse(wrapForParsing(resp));
        Element challenge = (Element) doc.getElementsByTagName("challenge").item(0);
        if (challenge == null) {
            log.error("Не получили <challenge> от сервера: {}", resp);
            return false;
        }

        String serverFirst = new String(Base64.getDecoder().decode(
                challenge.getTextContent().trim()));
        String clientFinal = scram.clientFinal(serverFirst, password);

        connection.send("<response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>"
                + clientFinal + "</response>");

        String resp2 = connection.readStanza();
        log.info("SASL SCRAM success response: {}", resp2);

        if (resp2.contains("<failure")) {
            log.error("SASL SCRAM failure на client-final: {}", resp2);
            return false;
        }
        if (!resp2.contains("<success")) return false;

        // Проверяем подпись сервера
        Document doc2 = XmlUtil.parse(wrapForParsing(resp2));
        Element success = (Element) doc2.getElementsByTagName("success").item(0);
        if (success != null && !success.getTextContent().isBlank()) {
            String serverFinal = new String(Base64.getDecoder().decode(
                    success.getTextContent().trim()));
            if (!scram.verifyServerFinal(serverFinal)) {
                log.warn("Не удалось проверить подпись сервера");
            }
        }
        return true;
    }

    private String wrapForParsing(String xml) {
        // Оборачиваем одиночную станзу в <wrapper>, чтобы DOMParser смог распарсить
        return "<wrapper>" + xml + "</wrapper>";
    }

    // ============================================================
    // Чтение станз (reader loop)
    // ============================================================
    private void readLoop() {
        Thread keepAlive = new Thread(this::keepAliveLoop, "xmpp-keepalive");
        keepAlive.setDaemon(true);
        keepAlive.start();

        while (running) {
            try {
                String xml = connection.readStanza();
                lastActivity = System.currentTimeMillis();
                if (xml == null || xml.isEmpty()) continue;

                String trimmed = xml.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith("<?xml")) continue;
                if (trimmed.startsWith("<stream:stream")) continue;

                log.debug("📥 Recv: {}", trimmed);
                dispatch(trimmed);

            } catch (XmppException e) {
                // Таймаут — это НОРМА, не ошибка. Продолжаем цикл.
                if (isTimeout(e)) {
                    log.debug("Таймаут чтения — продолжаем");
                    continue;
                }
                if (running) {
                    log.warn("Ошибка чтения: {}", e.getMessage());
                    eventBus.publish(new ConnectionEvent(
                            ConnectionEvent.Kind.ERROR, e.getMessage()));
                }
                break;
            } catch (Exception e) {
                if (running) {
                    log.warn("Неожиданная ошибка в readLoop: {}", e.getMessage(), e);
                }
                break;
            }
        }
    }

    /**
     * Проверяет, является ли исключение таймаутом (по cause или сообщению).
     */
    private boolean isTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof java.net.SocketTimeoutException) return true;
            cur = cur.getCause();
        }
        String msg = e.getMessage();
        return msg != null && msg.contains("Таймаут");
    }

    /**
     * Периодически шлёт пробел как keep-alive (XMPP whitespace ping).
     * Если был трафик за последние 30 секунд — не шлём.
     */
    private void keepAliveLoop() {
        while (running) {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                return;
            }
            if (!running) return;
            long idle = System.currentTimeMillis() - lastActivity;
            if (idle >= KEEPALIVE_INTERVAL_MS) {
                try {
                    connection.send(" ");
                    lastActivity = System.currentTimeMillis();
                    log.debug("keep-alive: отправлен пробел");
                } catch (Exception e) {
                    log.debug("keep-alive не удался: {}", e.getMessage());
                    return;
                }
            }
        }
    }
    private void dispatch(String xml) {
        try {
            Document doc = XmlUtil.parse(wrapForParsing(xml));
            Element root = doc.getDocumentElement();
            Element child = null;

            // Находим первый элемент внутри <wrapper>
            for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                if (root.getChildNodes().item(i).getNodeType() == Element.ELEMENT_NODE) {
                    child = (Element) root.getChildNodes().item(i);
                    break;
                }
            }
            if (child == null) return;

            log.debug("📨 Станза: {}", child.getNodeName());

            // Раскидываем по модулям
            for (XmppModule m : modules) {
                try {
                    if (m.handle(child)) {
                        log.debug("Станза обработана модулем {}", m.getName());
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Модуль {} упал: {}", m.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Парсинг станзы провалился: {} | XML: {}",
                    e.getMessage(), xml.substring(0, Math.min(100, xml.length())));
        }
    }

    // ============================================================
    // Публичные методы
    // ============================================================
    public void sendMessage(String to, String body) {
        getModule(MessageModule.class).sendMessage(to, body);
    }

    public void sendTyping(String to, boolean composing) {
        getModule(MessageModule.class).sendComposing(to, composing);
    }

    public void setCurrentChat(String jid) {
        this.currentChat = jid;
    }

    public String getCurrentChat() { return currentChat; }

    public void storeMessage(String chatJid, ChatMessage msg) {
        messages.computeIfAbsent(chatJid, k -> new CopyOnWriteArrayList<>()).add(msg);
    }

    public List<ChatMessage> getMessages(String chatJid) {
        return messages.getOrDefault(chatJid, Collections.emptyList());
    }

    public void markMessageStatus(String chatJid, String messageId, MessageStatus status) {
        List<ChatMessage> list = messages.get(chatJid);
        if (list == null) return;
        for (ChatMessage m : list) {
            if (m.getId().equals(messageId)) {
                m.setStatus(status);
                return;
            }
        }
    }

    public void sendRaw(String xml) throws XmppException {
        connection.send(xml);
    }

    public String getJid() { return fullJid; }
    public String getBareJid() { return bareJid; }
    public EventBus getEventBus() { return eventBus; }
    public RosterModule getRosterModule() { return getModule(RosterModule.class); }

    private String getDomain() {
        return bareJid.contains("@") ? bareJid.split("@")[1] : bareJid;
    }

    public void disconnect() {
        if (!running && connection == null) return;
        running = false;

        try {
            getModule(PresenceModule.class).sendUnavailable();
            connection.send("</stream:stream>");
        } catch (Exception ignored) {}

        for (XmppModule m : modules) {
            try { m.onDisconnected(); } catch (Exception ignored) {}
        }

        if (connection != null) connection.close();

        if (readerThread != null) {
            try {
                readerThread.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }

        eventBus.publish(new ConnectionEvent(
                ConnectionEvent.Kind.DISCONNECTED, "Отключено"));
    }

    @Override
    public void close() {
        disconnect();
    }
}