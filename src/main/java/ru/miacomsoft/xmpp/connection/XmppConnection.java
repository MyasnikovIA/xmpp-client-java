package ru.miacomsoft.xmpp.connection;

import ru.miacomsoft.xmpp.XmppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class XmppConnection implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(XmppConnection.class);

    private final String host;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    /**
     * Накопительный буфер строк. Из него вырезаются готовые станзы.
     */
    private final StringBuilder rawBuffer = new StringBuilder();

    public XmppConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void open() throws XmppException {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(15_000);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            log.info("TCP open: {}:{}", host, port);
        } catch (IOException e) {
            throw new XmppException("Не удалось открыть TCP: " + host + ":" + port, e);
        }
    }

    public void startTls() throws XmppException {
        try {
            // Создаём SSLContext с TrustManager, который принимает ЛЮБОЙ сертификат
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(
                    null,
                    new javax.net.ssl.TrustManager[] {
                            new javax.net.ssl.X509TrustManager() {
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                    return new java.security.cert.X509Certificate[0];
                                }
                                public void checkClientTrusted(
                                        java.security.cert.X509Certificate[] certs, String authType) {
                                    // доверяем всем
                                }
                                public void checkServerTrusted(
                                        java.security.cert.X509Certificate[] certs, String authType) {
                                    // доверяем всем
                                }
                            }
                    },
                    new java.security.SecureRandom()
            );

            SSLSocketFactory factory = sslContext.getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    socket, host, port, true);

            // Отключаем проверку hostname (иначе self-signed с неправильным CN не сработает)
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

            sslSocket.startHandshake();

            socket = sslSocket;
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            rawBuffer.setLength(0);
            log.info("TLS установлен");
        } catch (Exception e) {
            throw new XmppException("Ошибка STARTTLS", e);
        }
    }

    public void send(String xml) throws XmppException {
        try {
            log.debug(">>> {}", xml);
            byte[] data = xml.getBytes(StandardCharsets.UTF_8);
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            throw new XmppException("Ошибка отправки", e);
        }
    }

    public String readStanza() throws XmppException {
        try {
            while (true) {
                // Пытаемся вычленить из rawBuffer законченную станзу
                String stanza = tryExtractStanza();
                if (stanza != null) {
                    log.debug("<<< {}", stanza);
                    return stanza;
                }

                // Нужно ещё данных — читаем из сокета
                byte[] buf = new byte[4096];
                log.debug("readStanza: в буфере нет полной станзы, читаем сокет...");
                int n = inputStream.read(buf);
                log.debug("readStanza: прочитано {} байт", n);
                if (n <= 0) {
                    throw new XmppException("Поток закрыт сервером");
                }
                rawBuffer.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        } catch (java.net.SocketTimeoutException e) {
            throw new XmppException("Таймаут чтения — сервер не отвечает", e);
        } catch (IOException e) {
            throw new XmppException("Ошибка чтения: " + e.getMessage(), e);
        }
    }

    /**
     * Пытается вычленить из rawBuffer законченную станзу.
     * Возвращает null, если станза ещё не полная.
     */
    private String tryExtractStanza() {
        while (true) {
            String data = rawBuffer.toString();

            int firstLt = data.indexOf('<');
            if (firstLt < 0) {
                rawBuffer.setLength(0);
                return null;
            }
            if (firstLt > 0) {
                rawBuffer.delete(0, firstLt);
                continue;
            }

            // XML-пролог
            if (data.startsWith("<?") && data.contains("?>")) {
                int idx = data.indexOf("?>");
                rawBuffer.delete(0, idx + 2);
                continue;
            }
            if (data.startsWith("<?") && !data.contains("?>")) {
                return null;
            }

            // ← ГЛАВНОЕ ИСПРАВЛЕНИЕ: отслеживаем НАЧАЛО текущего тега
            int depth = 0;
            boolean inQuote = false;
            char quoteChar = 0;
            int tagStart = -1;

            for (int i = 0; i < data.length(); i++) {
                char ch = data.charAt(i);

                if (inQuote) {
                    if (ch == quoteChar) inQuote = false;
                    continue;
                }
                if (ch == '"' || ch == '\'') {
                    inQuote = true;
                    quoteChar = ch;
                    continue;
                }
                if (ch == '<') {
                    tagStart = i;                 // ← запоминаем начало тега
                    continue;
                }

                if (ch == '>' && tagStart >= 0) {
                    String tag = data.substring(tagStart, i + 1);   // ← ТОЛЬКО текущий тег
                    String full = data.substring(0, i + 1);          // ← всё с начала

                    boolean selfClosing = tag.endsWith("/>");
                    boolean isClosing = tag.startsWith("</");        // ← теперь корректно
                    boolean isStreamStream = tag.startsWith("<stream:stream")
                            && !isClosing && !selfClosing;

                    if (isStreamStream) {
                        String stanza = full.trim();
                        rawBuffer.delete(0, i + 1);
                        return stanza;
                    }

                    if (selfClosing) {
                        if (depth == 0) {
                            String stanza = full.trim();
                            rawBuffer.delete(0, i + 1);
                            return stanza;
                        }
                    } else if (isClosing) {
                        depth--;
                    } else {
                        depth++;
                    }

                    if (depth == 0) {
                        String stanza = full.trim();
                        rawBuffer.delete(0, i + 1);
                        return stanza;
                    }

                    tagStart = -1;   // ← сбрасываем для следующего тега
                }
            }

            return null;
        }
    }

    public boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}