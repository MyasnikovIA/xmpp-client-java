package ru.miacomsoft.xmpp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.miacomsoft.xmpp.RegistrationClient;
import ru.miacomsoft.xmpp.XmppException;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class RegistrationClientTimeoutIT {

    @Test
    @DisplayName("IT: сервер молчит → таймаут при fetchForm")
    void serverSilent() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            Thread t = new Thread(() -> {
                try (Socket s = ss.accept()) {
                    // читаем открытие стрима и молчим
                    s.getInputStream().read(new byte[1024]);
                    Thread.sleep(20_000);
                } catch (Exception ignored) {}
            });
            t.setDaemon(true);
            t.start();

            RegistrationClient reg = new RegistrationClient("localhost", ss.getLocalPort());
            try {
                XmppException ex = assertThrows(XmppException.class, reg::fetchForm);
                assertTrue(ex.getMessage().contains("Таймаут")
                        || ex.getMessage().contains("features")
                        || ex.getMessage().contains("Не дождались"));
            } finally {
                reg.close();
            }
        }
    }

    @Test
    @DisplayName("IT: сервер закрывает соединение → XmppException")
    void serverClosesConnection() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            Thread t = new Thread(() -> {
                try (Socket s = ss.accept()) {
                    s.getInputStream().read(new byte[1024]);
                    // сразу закрываем
                } catch (Exception ignored) {}
            });
            t.setDaemon(true);
            t.start();

            RegistrationClient reg = new RegistrationClient("localhost", ss.getLocalPort());
            try {
                assertThrows(XmppException.class, reg::fetchForm);
            } finally {
                reg.close();
            }
        }
    }
}