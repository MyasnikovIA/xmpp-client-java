package ru.miacomsoft.xmpp.module;

import ru.miacomsoft.xmpp.XmppException;
import ru.miacomsoft.xmpp.stanza.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XEP-0077 — In-Band Registration.
 *
 * Модуль умеет:
 *   - запрашивать у сервера форму регистрации;
 *   - отправлять заполненную форму;
 *   - удалять аккаунт (XEP-0077 remove).
 *
 * ВАЖНО: модуль используется ДО аутентификации, поэтому client.onConnected()
 * не вызывается. Регистрация управляется из RegistrationClient.
 */
public class RegistrationModule extends XmppModule {

    private static final Logger log = LoggerFactory.getLogger(RegistrationModule.class);

    @Override
    public String getName() { return "registration"; }

    /**
     * Отправляет IQ-запрос формы регистрации.
     * Ответ приходит асинхронно в handle() — но обычно RegistrationClient
     * сам читает станзу и передаёт её сюда.
     */
    public String buildRegistrationFormRequest(String id) {
        return "<iq type='get' id='" + XmlUtil.escape(id) + "'>"
                + "<query xmlns='jabber:iq:register'/>"
                + "</iq>";
    }

    /**
     * Строит IQ-set для регистрации с переданными полями.
     * Поддерживает как простую форму (username/password), так и XEP-0004 Data Forms.
     */
    public String buildRegistrationSubmit(String id, RegistrationForm form) {
        StringBuilder sb = new StringBuilder();
        sb.append("<iq type='set' id='").append(XmlUtil.escape(id)).append("'>");
        sb.append("<query xmlns='jabber:iq:register'>");

        if (form.isDataForm()) {
            // XEP-0004: <x xmlns='jabber:x:data' type='submit'>
            sb.append("<x xmlns='jabber:x:data' type='submit'>");
            sb.append("<field var='FORM_TYPE' type='hidden'>");
            sb.append("<value>jabber:iq:register</value>");
            sb.append("</field>");
            for (Map.Entry<String, String> e : form.getFields().entrySet()) {
                if (e.getValue() == null) continue;
                sb.append("<field var='").append(XmlUtil.escape(e.getKey())).append("'>");
                sb.append("<value>").append(XmlUtil.escape(e.getValue())).append("</value>");
                sb.append("</field>");
            }
            sb.append("</x>");
        } else {
            // Классическая форма: <username/>, <password/>, ...
            for (Map.Entry<String, String> e : form.getFields().entrySet()) {
                if (e.getValue() == null) continue;
                sb.append("<").append(XmlUtil.escape(e.getKey())).append(">")
                        .append(XmlUtil.escape(e.getValue()))
                        .append("</").append(XmlUtil.escape(e.getKey())).append(">");
            }
        }
        sb.append("</query></iq>");
        return sb.toString();
    }

    /**
     * Парсит <query xmlns='jabber:iq:register'/> из ответа сервера.
     * Возвращает RegistrationForm с полями (может быть data-form или классической).
     */
    public RegistrationForm parseRegistrationForm(Element iq) throws XmppException {
        Element query = XmlUtil.firstChild(iq, "query");
        if (query == null
                || !"jabber:iq:register".equals(query.getAttribute("xmlns"))) {
            throw new XmppException("Не нашли <query xmlns='jabber:iq:register'/>");
        }

        // Ошибка?
        Element error = XmlUtil.firstChild(iq, "error");
        if (error != null) {
            String type = XmlUtil.attr(error, "type");
            Element condition = null;
            NodeList ch = error.getChildNodes();
            for (int i = 0; i < ch.getLength(); i++) {
                if (ch.item(i).getNodeType() == Element.ELEMENT_NODE) {
                    condition = (Element) ch.item(i);
                    break;
                }
            }
            String condName = condition != null ? XmlUtil.localName(condition) : "unknown";
            throw new XmppException("Регистрация недоступна: " + type + " / " + condName);
        }

        RegistrationForm form = new RegistrationForm();

        // Смотрим, есть ли <x xmlns='jabber:x:data'>
        Element x = XmlUtil.firstChild(query, "x");
        if (x != null && "jabber:x:data".equals(x.getAttribute("xmlns"))) {
            form.setDataForm(true);
            NodeList fields = x.getElementsByTagName("field");
            for (int i = 0; i < fields.getLength(); i++) {
                Element f = (Element) fields.item(i);
                String var = XmlUtil.attr(f, "var");
                String type = XmlUtil.attr(f, "type");
                if (var == null) continue;
                if ("hidden".equals(type)) continue;

                Element valueEl = XmlUtil.firstChild(f, "value");
                String value = valueEl != null ? valueEl.getTextContent() : "";

                form.addField(var, value, type != null ? type : "text");

                // Собираем инструкции и опции
                Element labelEl = XmlUtil.firstChild(f, "label");
                if (labelEl != null) form.setLabel(var, labelEl.getTextContent());

                if ("boolean".equals(type) || "list-single".equals(type)) {
                    List<String> options = new ArrayList<>();
                    NodeList opts = f.getElementsByTagName("option");
                    for (int j = 0; j < opts.getLength(); j++) {
                        Element o = (Element) opts.item(j);
                        Element ov = XmlUtil.firstChild(o, "value");
                        if (ov != null) options.add(ov.getTextContent());
                    }
                    form.setOptions(var, options);
                }
            }
        } else {
            // Классическая форма — просто список дочерних тегов
            form.setDataForm(false);
            NodeList ch = query.getChildNodes();
            for (int i = 0; i < ch.getLength(); i++) {
                if (ch.item(i).getNodeType() != Element.ELEMENT_NODE) continue;
                Element el = (Element) ch.item(i);
                String name = XmlUtil.localName(el);
                if ("instructions".equals(name)) {
                    form.setInstructions(el.getTextContent());
                    continue;
                }
                form.addField(name, "", "text");
            }
        }

        // Инструкции из data-form
        Element instructionsEl = XmlUtil.firstChild(x != null ? x : query, "instructions");
        if (instructionsEl != null) {
            form.setInstructions(instructionsEl.getTextContent());
        }

        return form;
    }

    /**
     * Парсит ответ на submit (или на remove).
     * Бросает XmppException с расшифровкой ошибки.
     */
    public void checkIqResult(Element iq) throws XmppException {
        String type = XmlUtil.attr(iq, "type");
        if ("result".equals(type)) return;

        Element error = XmlUtil.firstChild(iq, "error");
        if (error == null) {
            throw new XmppException("Неожиданный ответ: type=" + type);
        }
        String errType = XmlUtil.attr(error, "type");
        String condition = "unknown";
        NodeList ch = error.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            if (ch.item(i).getNodeType() == Element.ELEMENT_NODE) {
                condition = XmlUtil.localName((Element) ch.item(i));
                break;
            }
        }
        String text = "";
        Element textEl = XmlUtil.firstChild(error, "text");
        if (textEl != null) text = textEl.getTextContent();

        String human = explainError(condition);
        throw new XmppException("Регистрация отклонена: " + errType
                + " / " + condition + (human.isEmpty() ? "" : " (" + human + ")")
                + (text.isEmpty() ? "" : " — " + text));
    }

    private String explainError(String condition) {
        switch (condition) {
            case "conflict":              return "логин уже занят";
            case "not-allowed":           return "регистрация запрещена сервером";
            case "feature-not-implemented": return "сервер не поддерживает XEP-0077";
            case "bad-request":           return "неверный формат запроса";
            case "forbidden":             return "доступ запрещён";
            case "registration-required": return "требуется регистрация";
            case "service-unavailable":   return "сервис недоступен";
            default:                      return "";
        }
    }

    /**
     * Строит IQ для удаления аккаунта (XEP-0077 §3.2).
     * Требует активной аутентифицированной сессии.
     */
    public String buildRemoveAccount(String id) {
        return "<iq type='set' id='" + XmlUtil.escape(id) + "'>"
                + "<query xmlns='jabber:iq:register'><remove/></query>"
                + "</iq>";
    }

    @Override
    public boolean handle(Element stanza) {
        // В обычной работе модуль не обрабатывает входящие —
        // регистрация идёт через RegistrationClient.
        return false;
    }
}