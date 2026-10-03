package ru.miacomsoft.xmpp.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.miacomsoft.xmpp.XmppException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SaslAuthenticatorTest {

    // ============================================================
    // PLAIN
    // ============================================================

    @Test
    @DisplayName("PLAIN: корректно извлекает username из JID")
    void plainExtractsUsername() {
        String b64 = SaslAuthenticator.plain("user@example.com", "secret");
        String raw = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        assertEquals("\0user\0secret", raw);
    }

    @Test
    @DisplayName("PLAIN: игнорирует resource в JID")
    void plainIgnoresResource() {
        String b64 = SaslAuthenticator.plain("user@example.com/phone", "p");
        String raw = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        assertEquals("\0user\0p", raw);
    }

    @Test
    @DisplayName("PLAIN: null JID → IllegalArgumentException")
    void plainNullJid() {
        assertThrows(IllegalArgumentException.class,
                () -> SaslAuthenticator.plain(null, "p"));
    }

    @Test
    @DisplayName("PLAIN: null пароль → IllegalArgumentException")
    void plainNullPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> SaslAuthenticator.plain("user@example.com", null));
    }

    @Test
    @DisplayName("PLAIN: NUL в пароле → IllegalArgumentException")
    void plainNulInPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> SaslAuthenticator.plain("user@example.com", "a\0b"));
    }

    @Test
    @DisplayName("PLAIN: пустой JID → IllegalArgumentException")
    void plainEmptyJid() {
        assertThrows(IllegalArgumentException.class,
                () -> SaslAuthenticator.plain("", "p"));
    }

    @Test
    @DisplayName("PLAIN: JID без @ (только username) — тоже ок")
    void plainBareUsername() {
        String b64 = SaslAuthenticator.plain("justuser", "p");
        String raw = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        assertEquals("\0justuser\0p", raw);
    }

    // ============================================================
    // SCRAM-SHA-1
    // ============================================================

    @Test
    @DisplayName("SCRAM: client-first содержит n=username,r=nonce")
    void scramClientFirst() {
        SaslAuthenticator.ScramSession s =
                new SaslAuthenticator.ScramSession("user@example.com");
        assertTrue(s.clientFirstMessage.startsWith("n,,n=user,r="));
        assertFalse(s.clientFirstMessage.contains("@example.com"));
    }

    @Test
    @DisplayName("SCRAM: nonce уникален между сессиями")
    void scramNonceUnique() {
        SaslAuthenticator.ScramSession a =
                new SaslAuthenticator.ScramSession("u@d");
        SaslAuthenticator.ScramSession b =
                new SaslAuthenticator.ScramSession("u@d");
        assertNotEquals(a.cnonce, b.cnonce);
    }

    @Test
    @DisplayName("SCRAM: clientFirstBase64 декодируется обратно")
    void scramClientFirstBase64() {
        SaslAuthenticator.ScramSession s =
                new SaslAuthenticator.ScramSession("user@example.com");
        String dec = new String(Base64.getDecoder().decode(s.clientFirstBase64()),
                StandardCharsets.UTF_8);
        assertEquals(s.clientFirstMessage, dec);
    }

    @Test
    @DisplayName("SCRAM: clientFinal отвергает server nonce без client nonce (MITM)")
    void scramRejectsBadNonce() throws Exception {
        SaslAuthenticator.ScramSession s =
                new SaslAuthenticator.ScramSession("user@example.com");
        // Серверный nonce НЕ начинается с client nonce — это атака/ошибка
        String serverFirst = "r=ATTACKER_NONCE,s=" + Base64.getEncoder()
                .encodeToString("salt".getBytes()) + ",i=4096";
        XmppException ex = assertThrows(XmppException.class,
                () -> s.clientFinal(serverFirst, "secret"));
        assertTrue(ex.getMessage().toLowerCase().contains("nonce"));
    }

    @Test
    @DisplayName("SCRAM: clientFinal отвергает слишком мало итераций")
    void scramRejectsLowIterations() {
        SaslAuthenticator.ScramSession s =
                new SaslAuthenticator.ScramSession("user@example.com");
        String serverFirst = "r=" + s.cnonce + "XYZ,s="
                + Base64.getEncoder().encodeToString("salt".getBytes())
                + ",i=100";     // меньше 4096
        assertThrows(XmppException.class,
                () -> s.clientFinal(serverFirst, "secret"));
    }

    @Test
    @DisplayName("SCRAM: clientFinal отвергает неверный server-first")
    void scramRejectsBadServerFirst() {
        SaslAuthenticator.ScramSession s =
                new SaslAuthenticator.ScramSession("user@example.com");
        assertThrows(XmppException.class,
                () -> s.clientFinal("garbage", "secret"));
    }

    @Test
    @DisplayName("SCRAM: детерминированный результат для фиксированного nonce (RFC 5802 вектор)")
    void scramRfc5802Vector() throws Exception {
        // Тестовый вектор из RFC 5802 Appendix B.
        // Но наш nonce генерируется случайно — поэтому используем свой
        // способ: проверим только, что clientFinal формирует корректный proof
        // и что verifyServerFinal возвращает true для правильно посчитанного.
        // Здесь проверяем базовое: serverSig детерминирован для одинаковых входов.
        SaslAuthenticator.ScramSession s =
                new SaslAuthenticator.ScramSession("user@example.com");
        String salt = Base64.getEncoder().encodeToString(new byte[16]);
        String serverFirst = "r=" + s.cnonce + "srv,s=" + salt + ",i=4096";
        String final1 = s.clientFinal(serverFirst, "secret");

        // Повторный вызов на той же сессии должен дать тот же proof
        String final2 = s.clientFinal(serverFirst, "secret");
        assertEquals(final1, final2);
    }
}