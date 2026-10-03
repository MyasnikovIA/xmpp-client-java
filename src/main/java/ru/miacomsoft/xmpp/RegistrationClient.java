package ru.miacomsoft.xmpp;

import ru.miacomsoft.xmpp.connection.XmppConnection;
import ru.miacomsoft.xmpp.module.RegistrationForm;
import ru.miacomsoft.xmpp.module.RegistrationModule;
import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.Closeable;
import java.util.UUID;

/**
 * Клиент для регистрации нового XMPP-аккаунта (XEP-0077).
 *
 * Типовой сценарий:
 * <pre>
 *   RegistrationClient reg = new RegistrationClient("smwrap.ru", 5222);
 *   RegistrationForm form = reg.fetchForm();          // получаем поля
 *   form.setCredentials("newuser", "secret");         // заполняем
 *   reg.submit(form);                                 // регистрируем
 * </pre>
 *
 * Класс не является потокобезопасным — используйте из одного потока.
 */
public class RegistrationClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RegistrationClient.class);

    private final String host;
    private final int port;
    private String domain;              // может отличаться от host
    private boolean trustAllCertificates = false;

    private XmppConnection connection;
    private final RegistrationModule module = new RegistrationModule();

    public RegistrationClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.domain = host;
    }

    /** Если JID-домен отличается от хоста подключения. */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /** Включить режим "доверять всем" сертификатам (для self-signed). */
    public void setTrustAllCertificates(boolean trustAll) {
        this.trustAllCertificates = trustAll;
    }

    public RegistrationModule getModule() { return module; }

    // ============================================================
    // Открытие регистрационного стрима
    // ============================================================
    private void ensureStreamOpen() throws XmppException {
        if (connection != null && connection.isOpen()) return;

        connection = new XmppConnection(host, port);
        connection.setTrustAllCertificates(trustAllCertificates);
        connection.open();

        connection.send("<?xml version='1.0' encoding='UTF-8'?>"
                + "<stream:stream to='" + XmlUtil.escape(domain) + "' "
                + "xmlns='jabber:client' "
                + "xmlns:stream='http://etherx.jabber.org/streams' "
                + "version='1.0'>");

        String features = readUntilFeatures();
        log.info("Features (reg): {}", features);

        // STARTTLS, если предложен
        if (features.contains("starttls")) {
            connection.send("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
            String tlsResp = connection.readStanza();
            if (!tlsResp.contains("proceed")) {
                throw new XmppException("Сервер не начал TLS: " + tlsResp);
            }
            connection.startTls();

            connection.send("<stream:stream to='" + XmlUtil.escape(domain) + "' "
                    + "xmlns='jabber:client' "
                    + "xmlns:stream='http://etherx.jabber.org/streams' "
                    + "version='1.0'>");
            features = readUntilFeatures();
            log.info("Features (reg, после TLS): {}", features);
        }

        // Проверяем, разрешена ли регистрация
        if (features.contains("<register")
                || features.contains(":register")
                || features.contains("jabber:iq:register")) {
            log.info("Сервер анонсировал <register/> — регистрация разрешена");
        } else {
            log.warn("Сервер не анонсировал <register/> — попробуем всё равно");
        }
    }

    // ============================================================
    // Публичное API
    // ============================================================

    /**
     * Запрашивает у сервера форму регистрации.
     *
     * @return заполненная структура с полями; значения полей — пустые
     *         (кроме тех, что сервер задал по умолчанию)
     */
    public RegistrationForm fetchForm() throws XmppException {
        ensureStreamOpen();

        String id = "reg_" + UUID.randomUUID();
        connection.send(module.buildRegistrationFormRequest(id));

        Element iq = readIqWithId(id);
        return module.parseRegistrationForm(iq);
    }

    /**
     * Отправляет заполненную форму.
     *
     * @throws XmppException если сервер отклонил (conflict, not-allowed, ...)
     */
    public void submit(RegistrationForm form) throws XmppException {
        ensureStreamOpen();

        String id = "regset_" + UUID.randomUUID();
        connection.send(module.buildRegistrationSubmit(id, form));

        Element iq = readIqWithId(id);
        module.checkIqResult(iq);
        log.info("Регистрация успешна");

        // Закрываем стрим — аккаунт создан
        try { connection.send("</stream:stream>"); } catch (Exception ignored) {}
        connection.close();
        connection = null;
    }

    /**
     * Удобный шорткат: получить форму, заполнить username/password, отправить.
     * Возвращает серверную форму — можно посмотреть, какие ещё поля были.
     */
    public RegistrationForm register(String username, String password) throws XmppException {
        RegistrationForm form = fetchForm();
        form.setCredentials(username, password);
        submit(form);
        return form;
    }

    // ============================================================
    // Внутреннее
    // ============================================================

    /**
     * Читает станзы, пока не найдёт IQ с нужным id.
     * Попутно пропускает <?xml?>, <stream:stream>, <stream:features>.
     */
    private Element readIqWithId(String id) throws XmppException {
        for (int i = 0; i < 30; i++) {
            String xml = connection.readStanza();
            if (xml == null || xml.trim().isEmpty()) continue;

            String trimmed = xml.trim();
            if (trimmed.startsWith("<?xml")) continue;
            if (trimmed.startsWith("<stream:stream")) continue;
            if (trimmed.contains("<stream:features")) continue;
            if (trimmed.contains("<stream:error")) {
                throw new XmppException("Сервер вернул stream:error: " + trimmed);
            }

            try {
                Document doc = XmlUtil.parse("<w>" + trimmed + "</w>");
                Element root = doc.getDocumentElement();
                Element child = null;
                for (int k = 0; k < root.getChildNodes().getLength(); k++) {
                    if (root.getChildNodes().item(k).getNodeType()
                            == Element.ELEMENT_NODE) {
                        child = (Element) root.getChildNodes().item(k);
                        break;
                    }
                }
                if (child == null) continue;
                if (!"iq".equals(XmlUtil.localName(child))) {
                    log.debug("readIqWithId: пропускаем {}", XmlUtil.localName(child));
                    continue;
                }
                String gotId = XmlUtil.attr(child, "id");
                if (id.equals(gotId)) return child;
                log.debug("readIqWithId: чужой id={}", gotId);
            } catch (Exception e) {
                log.debug("readIqWithId: не распарсили: {}", e.getMessage());
            }
        }
        throw new XmppException("Не дождались IQ с id=" + id);
    }

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
            log.debug("readUntilFeatures(reg): пропускаем {}", trimmed);
        }
        throw new XmppException("Не получили <stream:features>");
    }

    @Override
    public void close() {
        if (connection != null) {
            try { connection.send("</stream:stream>"); } catch (Exception ignored) {}
            connection.close();
            connection = null;
        }
    }
}