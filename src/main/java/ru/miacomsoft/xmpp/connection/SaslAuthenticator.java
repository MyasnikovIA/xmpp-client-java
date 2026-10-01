package ru.miacomsoft.xmpp.connection;

import ru.miacomsoft.xmpp.XmppException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class SaslAuthenticator {

    /**
     * Извлекает username из JID (обрезает @domain и /resource).
     * myasnikovia@smwrap.ru → myasnikovia
     */
    private static String extractUsername(String jid) {
        if (jid == null) return null;
        // отрезаем resource
        int slash = jid.indexOf('/');
        String bare = slash > 0 ? jid.substring(0, slash) : jid;
        // отрезаем @domain
        int at = bare.indexOf('@');
        return at > 0 ? bare.substring(0, at) : bare;
    }

    /**
     * SASL PLAIN: authzid\0authcid\0password
     * authzid — обычно пустой
     * authcid — ТОЛЬКО username (без домена!)
     */
    public static String plain(String jid, String password) {
        String username = extractUsername(jid);
        String raw = "\0" + username + "\0" + password;
        return Base64.getEncoder().encodeToString(
                raw.getBytes(StandardCharsets.UTF_8));
    }

    /** SCRAM-SHA-1 — client-first-message. */
    public static class ScramSession {
        public final String cnonce;
        public final String clientFirstMessageBare;
        public final String clientFirstMessage;
        private String serverSignature;

        public ScramSession(String jid) {
            // ВАЖНО: в SCRAM n= — это username БЕЗ домена
            String username = extractUsername(jid);

            SecureRandom rnd = new SecureRandom();
            byte[] nonce = new byte[16];
            rnd.nextBytes(nonce);
            this.cnonce = Base64.getEncoder().encodeToString(nonce);
            this.clientFirstMessageBare = "n=" + username + ",r=" + cnonce;
            this.clientFirstMessage = "n,," + clientFirstMessageBare;
        }

        public String clientFirstBase64() {
            return Base64.getEncoder().encodeToString(
                    clientFirstMessage.getBytes(StandardCharsets.UTF_8));
        }

        public String clientFinal(String serverFirst, String password) throws XmppException {
            try {
                String r = null, s = null;
                int i = -1;
                for (String part : serverFirst.split(",")) {
                    if (part.startsWith("r=")) r = part.substring(2);
                    else if (part.startsWith("s=")) s = part.substring(2);
                    else if (part.startsWith("i=")) i = Integer.parseInt(part.substring(2));
                }
                if (r == null || s == null || i < 0) {
                    throw new XmppException("SCRAM: неверный server-first");
                }
                byte[] salt = Base64.getDecoder().decode(s);
                byte[] saltedPassword = pbkdf2(password.toCharArray(), salt, i, 160);

                byte[] clientKey = hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
                byte[] storedKey = MessageDigest.getInstance("SHA-1").digest(clientKey);

                String channelBinding = "c=biws";
                String withoutProof = channelBinding + ",r=" + r;
                String authMessage = clientFirstMessageBare + "," + serverFirst + "," + withoutProof;

                byte[] clientSignature = hmac(storedKey, authMessage.getBytes(StandardCharsets.UTF_8));
                byte[] clientProof = xor(clientKey, clientSignature);

                byte[] serverKey = hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.UTF_8));
                byte[] serverSig = hmac(serverKey, authMessage.getBytes(StandardCharsets.UTF_8));
                this.serverSignature = Base64.getEncoder().encodeToString(serverSig);

                String finalMsg = withoutProof + ",p=" + Base64.getEncoder().encodeToString(clientProof);
                return Base64.getEncoder().encodeToString(
                        finalMsg.getBytes(StandardCharsets.UTF_8));
            } catch (XmppException e) {
                throw e;
            } catch (Exception e) {
                throw new XmppException("SCRAM ошибка", e);
            }
        }

        public boolean verifyServerFinal(String serverFinal) throws XmppException {
            String v = null;
            for (String part : serverFinal.split(",")) {
                if (part.startsWith("v=")) v = part.substring(2);
            }
            if (v == null) throw new XmppException("SCRAM: нет v=");
            return v.equals(serverSignature);
        }

        private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bits)
                throws Exception {
            javax.crypto.SecretKeyFactory factory =
                    javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return factory.generateSecret(
                    new javax.crypto.spec.PBEKeySpec(password, salt, iterations, bits)
            ).getEncoded();
        }

        private static byte[] hmac(byte[] key, byte[] data) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        }

        private static byte[] xor(byte[] a, byte[] b) {
            byte[] r = new byte[a.length];
            for (int i = 0; i < a.length; i++) r[i] = (byte) (a[i] ^ b[i]);
            return r;
        }
    }
}