package ru.miacomsoft.xmpp.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.miacomsoft.xmpp.RegistrationClient;
import ru.miacomsoft.xmpp.XmppException;
import ru.miacomsoft.xmpp.module.RegistrationForm;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class FakeServerRegistrationIT {

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile String lastRequest;

    @AfterEach
    void tearDown() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        if (serverThread != null) serverThread.join(2000);
    }

    /**
     * Запускает фейковый сервер, который:
     *   1. принимает стрим;
     *   2. отвечает features с register (без starttls — для простоты);
     *   3. на <iq type='get'> отдаёт форму регистрации;
     *   4. на <iq type='set'> отвечает result.
     */
    private int startFakeServer(boolean startTls, boolean registerAllowed,
                                String errorCondition) throws Exception {
        serverSocket = new ServerSocket(0);
        final int port = serverSocket.getLocalPort();
        final CountDownLatch ready = new CountDownLatch(1);

        serverThread = new Thread(() -> {
            try (Socket s = serverSocket.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        s.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                        s.getOutputStream(), StandardCharsets.UTF_8));

                ready.countDown();

                // 1. Читаем открытие стрима
                readUntil(in, "<stream:stream");

                // 2. Отдаём features
                out.write("<stream:stream xmlns='jabber:client' "
                        + "xmlns:stream='http://etherx.jabber.org/streams' "
                        + "id='srv' from='localhost' version='1.0'>");
                out.flush();

                StringBuilder feat = new StringBuilder("<stream:features>");
                if (startTls) feat.append("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
                if (registerAllowed)
                    feat.append("<register xmlns='http://jabber.org/features/iq-register'/>");
                feat.append("</stream:features>");
                out.write(feat.toString());
                out.flush();

                // 3. Если starttls — без реального TLS (тест не пойдёт по этому пути,
                //    но предусмотрим: сразу отдаём proceed и... тут нужен TLS-сокет,
                //    поэтому в этом фейке startTls=true не поддерживаем).
                if (startTls) {
                    return;
                }

                // 4. Читаем запрос формы
                String req = readUntil(in, "</iq>");
                lastRequest = req;

                if (errorCondition != null) {
                    out.write("<iq type='error' id='"
                            + extractId(req) + "'>"
                            + "<query xmlns='jabber:iq:register'/>"
                            + "<error type='cancel'><" + errorCondition
                            + " xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                            + "</error></iq>");
                    out.flush();
                    return;
                }

                // 5. Отдаём форму
                out.write("<iq type='result' id='" + extractId(req) + "'>"
                        + "<query xmlns='jabber:iq:register'>"
                        + "<instructions>Choose a name</instructions>"
                        + "<username/>"
                        + "<password/>"
                        + "</query></iq>");
                out.flush();

                // 6. Читаем submit
                String submit = readUntil(in, "</iq>");
                lastRequest = submit;

                // 7. Отвечаем result
                out.write("<iq type='result' id='" + extractId(submit) + "'/>");
                out.flush();
            } catch (Exception e) {
                // тест закроется по таймауту
            }
        }, "fake-xmpp-server");
        serverThread.setDaemon(true);
        serverThread.start();
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        return port;
    }

    private String readUntil(BufferedReader in, String marker) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            sb.append((char) ch);
            if (sb.indexOf(marker) >= 0) break;
        }
        return sb.toString();
    }

    private String extractId(String xml) {
        int i = xml.indexOf("id=");
        if (i < 0) return "unknown";
        char q = xml.charAt(i + 3);
        int j = xml.indexOf(q, i + 4);
        return xml.substring(i + 4, j);
    }

    // ============================================================
    // Тесты
    // ============================================================

    @Test
    @DisplayName("IT: fetchForm получает форму с username и password")
    void fetchForm() throws Exception {
        int port = startFakeServer(false, true, null);
        RegistrationClient reg = new RegistrationClient("localhost", port);
        try {
            RegistrationForm form = reg.fetchForm();
            assertTrue(form.hasField("username"));
            assertTrue(form.hasField("password"));
            assertEquals("Choose a name", form.getInstructions());
        } finally {
            reg.close();
        }
    }

    @Test
    @DisplayName("IT: submit отправляет форму и получает result")
    void submitForm() throws Exception {
        int port = startFakeServer(false, true, null);
        RegistrationClient reg = new RegistrationClient("localhost", port);
        try {
            RegistrationForm form = reg.fetchForm();
            form.setCredentials("newuser", "secret");

            assertDoesNotThrow(() -> reg.submit(form));
            assertTrue(lastRequest.contains("<username>newuser</username>"));
            assertTrue(lastRequest.contains("<password>secret</password>"));
        } finally {
            reg.close();
        }
    }

    @Test
    @DisplayName("IT: conflict → XmppException с 'занят'")
    void submitConflict() throws Exception {
        int port = startFakeServer(false, true, "conflict");
        RegistrationClient reg = new RegistrationClient("localhost", port);
        try {
            // fetchForm вернёт ошибку сразу
            XmppException ex = assertThrows(XmppException.class, reg::fetchForm);
            assertTrue(ex.getMessage().contains("conflict")
                    || ex.getMessage().contains("недоступна"));
        } finally {
            reg.close();
        }
    }

    @Test
    @DisplayName("IT: register() — шорткат fetchForm+submit")
    void registerShortcut() throws Exception {
        int port = startFakeServer(false, true, null);
        RegistrationClient reg = new RegistrationClient("localhost", port);
        try {
            assertDoesNotThrow(() -> reg.register("bob", "pwd"));
        } finally {
            reg.close();
        }
    }

    @Test
    @DisplayName("IT: сервер без <register/> — fetchForm либо падает, либо возвращает форму")
    void serverWithoutRegisterFeature() throws Exception {
        int port = startFakeServer(false, false, null);
        RegistrationClient reg = new RegistrationClient("localhost", port);
        try {
            // Наш фейк всё равно отдаёт форму — проверяем, что клиент не падает
            // при отсутствии явного <register/> в features.
            assertDoesNotThrow(reg::fetchForm);
        } finally {
            reg.close();
        }
    }
}