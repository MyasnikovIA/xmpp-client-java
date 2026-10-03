package ru.miacomsoft.xmpp.module;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ru.miacomsoft.xmpp.XmppException;
import ru.miacomsoft.xmpp.stanza.XmlUtil;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationModuleTest {

    private final RegistrationModule module = new RegistrationModule();

    private Element iq(String xml) throws Exception {
        Document d = XmlUtil.parse("<w>" + xml + "</w>");
        return (Element) d.getDocumentElement().getFirstChild();
    }

    // ============================================================
    // parseRegistrationForm
    // ============================================================

    @Test
    @DisplayName("parse: классическая форма (username/password)")
    void parseClassicForm() throws Exception {
        Element e = iq("<iq type='result' id='1'><query xmlns='jabber:iq:register'>"
                + "<instructions>Choose name</instructions>"
                + "<username/><password/></query></iq>");
        RegistrationForm f = module.parseRegistrationForm(e);

        assertFalse(f.isDataForm());
        assertTrue(f.hasField("username"));
        assertTrue(f.hasField("password"));
        assertEquals("Choose name", f.getInstructions());
    }

    @Test
    @DisplayName("parse: data-form (XEP-0004) с полями")
    void parseDataForm() throws Exception {
        Element e = iq("<iq type='result' id='1'><query xmlns='jabber:iq:register'>"
                + "<x xmlns='jabber:x:data' type='form'>"
                + "<field var='username' type='text'/>"
                + "<field var='password' type='text-private'/>"
                + "<field var='email' type='text'/>"
                + "</x></query></iq>");
        RegistrationForm f = module.parseRegistrationForm(e);

        assertTrue(f.isDataForm());
        assertTrue(f.hasField("username"));
        assertTrue(f.hasField("password"));
        assertTrue(f.hasField("email"));
        assertEquals("text-private", f.getFieldMap().get("password").getType());
    }

    @Test
    @DisplayName("parse: data-form с options (list-single)")
    void parseDataFormWithOptions() throws Exception {
        Element e = iq("<iq type='result' id='1'><query xmlns='jabber:iq:register'>"
                + "<x xmlns='jabber:x:data' type='form'>"
                + "<field var='color' type='list-single'>"
                + "<option><value>red</value></option>"
                + "<option><value>green</value></option>"
                + "</field>"
                + "</x></query></iq>");
        RegistrationForm f = module.parseRegistrationForm(e);

        assertEquals(2, f.getFieldMap().get("color").getOptions().size());
        assertTrue(f.getFieldMap().get("color").getOptions().contains("red"));
        assertTrue(f.getFieldMap().get("color").getOptions().contains("green"));
    }

    @Test
    @DisplayName("parse: ошибка conflict → XmppException с объяснением")
    void parseConflict() throws Exception {
        Element e = iq("<iq type='error' id='1'>"
                + "<query xmlns='jabber:iq:register'/>"
                + "<error type='cancel'><conflict xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                + "</error></iq>");
        XmppException ex = assertThrows(XmppException.class,
                () -> module.parseRegistrationForm(e));
        assertTrue(ex.getMessage().contains("недоступна")
                || ex.getMessage().contains("conflict"));
    }

    @Test
    @DisplayName("parse: not-allowed → XmppException")
    void parseNotAllowed() throws Exception {
        Element e = iq("<iq type='error' id='1'>"
                + "<query xmlns='jabber:iq:register'/>"
                + "<error type='cancel'><not-allowed xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                + "</error></iq>");
        assertThrows(XmppException.class, () -> module.parseRegistrationForm(e));
    }

    @Test
    @DisplayName("parse: без <query xmlns='jabber:iq:register'/> → XmppException")
    void parseWithoutQuery() throws Exception {
        Element e = iq("<iq type='result' id='1'><query xmlns='jabber:iq:version'/></iq>");
        assertThrows(XmppException.class, () -> module.parseRegistrationForm(e));
    }

    // ============================================================
    // buildRegistrationSubmit
    // ============================================================

    @Test
    @DisplayName("submit: классическая форма → <username>...</username>")
    void submitClassic() {
        RegistrationForm form = new RegistrationForm();
        form.setDataForm(false);
        form.setValue("username", "bob");
        form.setValue("password", "s3cret");

        String xml = module.buildRegistrationSubmit("1", form);
        assertTrue(xml.contains("<username>bob</username>"));
        assertTrue(xml.contains("<password>s3cret</password>"));
        assertTrue(xml.contains("id='1'"));
    }

    @Test
    @DisplayName("submit: data-form → <x xmlns='jabber:x:data'>")
    void submitDataForm() {
        RegistrationForm form = new RegistrationForm();
        form.setDataForm(true);
        form.setValue("username", "bob");
        form.setValue("password", "s3cret");

        String xml = module.buildRegistrationSubmit("2", form);
        assertTrue(xml.contains("xmlns='jabber:x:data'"));
        assertTrue(xml.contains("var='username'"));
        assertTrue(xml.contains("<value>bob</value>"));
        assertTrue(xml.contains("var='password'"));
        assertTrue(xml.contains("FORM_TYPE"));
        assertTrue(xml.contains("jabber:iq:register"));
    }

    @Test
    @DisplayName("submit: escape спецсимволов в значениях")
    void submitEscapesValues() {
        RegistrationForm form = new RegistrationForm();
        form.setDataForm(false);
        form.setValue("username", "a&b<c>");

        String xml = module.buildRegistrationSubmit("3", form);
        assertTrue(xml.contains("a&amp;b&lt;c&gt;"));
        assertFalse(xml.contains("a&b<c>"));
    }

    @Test
    @DisplayName("submit: null-значение поля пропускается")
    void submitSkipsNull() {
        RegistrationForm form = new RegistrationForm();
        form.setDataForm(false);
        form.setValue("username", "bob");
        form.addField("opt", null, "text");

        String xml = module.buildRegistrationSubmit("4", form);
        assertTrue(xml.contains("<username>bob</username>"));
        assertFalse(xml.contains("<opt>"));
    }

    // ============================================================
    // checkIqResult
    // ============================================================

    @Test
    @DisplayName("checkIqResult: result → без исключения")
    void checkResultOk() throws Exception {
        Element e = iq("<iq type='result' id='1'/>");
        assertDoesNotThrow(() -> module.checkIqResult(e));
    }

    @Test
    @DisplayName("checkIqResult: error conflict → XmppException с 'занят'")
    void checkConflict() throws Exception {
        Element e = iq("<iq type='error' id='1'>"
                + "<error type='cancel'>"
                + "<conflict xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                + "</error></iq>");
        XmppException ex = assertThrows(XmppException.class,
                () -> module.checkIqResult(e));
        assertTrue(ex.getMessage().contains("занят"));
    }

    @Test
    @DisplayName("checkIqResult: error not-allowed → XmppException")
    void checkNotAllowed() throws Exception {
        Element e = iq("<iq type='error' id='1'>"
                + "<error type='cancel'>"
                + "<not-allowed xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                + "</error></iq>");
        XmppException ex = assertThrows(XmppException.class,
                () -> module.checkIqResult(e));
        assertTrue(ex.getMessage().contains("запрещена"));
    }

    @Test
    @DisplayName("checkIqResult: error feature-not-implemented → XmppException")
    void checkFeatureNotImplemented() throws Exception {
        Element e = iq("<iq type='error' id='1'>"
                + "<error type='cancel'>"
                + "<feature-not-implemented xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                + "</error></iq>");
        XmppException ex = assertThrows(XmppException.class,
                () -> module.checkIqResult(e));
        assertTrue(ex.getMessage().contains("XEP-0077"));
    }

    @Test
    @DisplayName("checkIqResult: неизвестный condition → XmppException")
    void checkUnknownCondition() throws Exception {
        Element e = iq("<iq type='error' id='1'>"
                + "<error type='modify'>"
                + "<something-weird xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                + "</error></iq>");
        XmppException ex = assertThrows(XmppException.class,
                () -> module.checkIqResult(e));
        assertTrue(ex.getMessage().contains("something-weird"));
    }

    // ============================================================
    // buildRemoveAccount
    // ============================================================

    @Test
    @DisplayName("remove: корректный IQ с <remove/>")
    void removeAccountXml() {
        String xml = module.buildRemoveAccount("r1");
        assertTrue(xml.contains("type='set'"));
        assertTrue(xml.contains("jabber:iq:register"));
        assertTrue(xml.contains("<remove/>"));
    }

    @Test
    @DisplayName("remove: id сохраняется в escape-форме")
    void removeAccountId() {
        String xml = module.buildRemoveAccount("a'b<c");
        assertTrue(xml.contains("id='a&apos;b&lt;c'"));
    }

    @Test
    @DisplayName("remove: query содержит именно <remove/>, а не другие поля")
    void removeAccountOnlyRemove() {
        String xml = module.buildRemoveAccount("r2");
        assertTrue(xml.contains("<remove/>"));
        assertFalse(xml.contains("<username>"));
        assertFalse(xml.contains("<password>"));
    }

    // ============================================================
    // buildRegistrationFormRequest
    // ============================================================

    @Test
    @DisplayName("formRequest: содержит type='get' и xmlns='jabber:iq:register'")
    void formRequestXml() {
        String xml = module.buildRegistrationFormRequest("q1");
        assertTrue(xml.contains("type='get'"));
        assertTrue(xml.contains("id='q1'"));
        assertTrue(xml.contains("jabber:iq:register"));
    }

    @Test
    @DisplayName("formRequest: escape id")
    void formRequestEscapesId() {
        String xml = module.buildRegistrationFormRequest("a&b");
        assertTrue(xml.contains("id='a&amp;b'"));
    }

    @Test
    @DisplayName("formRequest: не содержит лишних полей")
    void formRequestMinimal() {
        String xml = module.buildRegistrationFormRequest("q2");
        assertFalse(xml.contains("<username>"));
        assertFalse(xml.contains("<password>"));
    }

    // ============================================================
    // handle() — модуль в обычной сессии станзы не обрабатывает
    // ============================================================

    @Test
    @DisplayName("handle: всегда false — регистрация идёт через RegistrationClient")
    void handleReturnsFalse() throws Exception {
        Element e = iq("<iq type='set' id='1'><query xmlns='jabber:iq:register'/></iq>");
        assertFalse(module.handle(e));
    }

    @Test
    @DisplayName("getName: возвращает 'registration'")
    void nameIsRegistration() {
        assertEquals("registration", module.getName());
    }

    @Test
    @DisplayName("handle: null-станза не падает")
    void handleNull() throws Exception {
        assertFalse(module.handle(null));
    }
}