package ru.miacomsoft.xmpp.module;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationFormTest {

    // ============================================================
    // addField / getValue / hasField
    // ============================================================

    @Test
    @DisplayName("addField + getValue: значение доступно")
    void addAndGet() {
        RegistrationForm f = new RegistrationForm();
        f.addField("username", "bob", "text");
        assertEquals("bob", f.getValue("username"));
    }

    @Test
    @DisplayName("setValue: создаёт поле, если его не было")
    void setValueCreates() {
        RegistrationForm f = new RegistrationForm();
        f.setValue("x", "y");
        assertTrue(f.hasField("x"));
        assertEquals("y", f.getValue("x"));
    }

    @Test
    @DisplayName("setValue: перезаписывает существующее")
    void setValueOverwrites() {
        RegistrationForm f = new RegistrationForm();
        f.addField("username", "old", "text");
        f.setValue("username", "new");
        assertEquals("new", f.getValue("username"));
    }

    @Test
    @DisplayName("hasField: false для отсутствующего")
    void hasFieldFalse() {
        RegistrationForm f = new RegistrationForm();
        assertFalse(f.hasField("nope"));
    }

    @Test
    @DisplayName("getValue: null для отсутствующего")
    void getValueNull() {
        RegistrationForm f = new RegistrationForm();
        assertNull(f.getValue("nope"));
    }

    // ============================================================
    // getFields
    // ============================================================

    @Test
    @DisplayName("getFields: возвращает var → value")
    void getFieldsMap() {
        RegistrationForm f = new RegistrationForm();
        f.setValue("a", "1");
        f.setValue("b", "2");
        Map<String, String> m = f.getFields();
        assertEquals("1", m.get("a"));
        assertEquals("2", m.get("b"));
    }

    @Test
    @DisplayName("getFields: пустая форма → пустая мапа")
    void getFieldsEmpty() {
        RegistrationForm f = new RegistrationForm();
        assertTrue(f.getFields().isEmpty());
    }

    @Test
    @DisplayName("getFields: сохраняет порядок добавления")
    void getFieldsOrder() {
        RegistrationForm f = new RegistrationForm();
        f.setValue("z", "1");
        f.setValue("a", "2");
        f.setValue("m", "3");
        String[] keys = f.getFields().keySet().toArray(new String[0]);
        assertArrayEquals(new String[]{"z", "a", "m"}, keys);
    }

    // ============================================================
    // setCredentials
    // ============================================================

    @Test
    @DisplayName("setCredentials: выставляет username и password")
    void setCredentials() {
        RegistrationForm f = new RegistrationForm();
        f.setCredentials("alice", "wonderland");
        assertEquals("alice", f.getValue("username"));
        assertEquals("wonderland", f.getValue("password"));
    }

    @Test
    @DisplayName("setCredentials: не падает, если поля не были объявлены")
    void setCredentialsOnEmptyForm() {
        RegistrationForm f = new RegistrationForm();
        assertDoesNotThrow(() -> f.setCredentials("a", "b"));
        assertTrue(f.hasField("username"));
        assertTrue(f.hasField("password"));
    }

    @Test
    @DisplayName("setCredentials: перезаписывает существующие")
    void setCredentialsOverwrites() {
        RegistrationForm f = new RegistrationForm();
        f.setValue("username", "old");
        f.setValue("password", "oldp");
        f.setCredentials("new", "newp");
        assertEquals("new", f.getValue("username"));
        assertEquals("newp", f.getValue("password"));
    }

    // ============================================================
    // instructions
    // ============================================================

    @Test
    @DisplayName("instructions: null → пустая строка")
    void instructionsNull() {
        RegistrationForm f = new RegistrationForm();
        f.setInstructions(null);
        assertEquals("", f.getInstructions());
    }

    @Test
    @DisplayName("instructions: сохраняется и читается")
    void instructionsStored() {
        RegistrationForm f = new RegistrationForm();
        f.setInstructions("Придумайте логин");
        assertEquals("Придумайте логин", f.getInstructions());
    }

    @Test
    @DisplayName("instructions: пустая по умолчанию")
    void instructionsDefaultEmpty() {
        RegistrationForm f = new RegistrationForm();
        assertEquals("", f.getInstructions());
    }

    // ============================================================
    // dataForm
    // ============================================================

    @Test
    @DisplayName("dataForm: по умолчанию false")
    void dataFormDefaultFalse() {
        RegistrationForm f = new RegistrationForm();
        assertFalse(f.isDataForm());
    }

    @Test
    @DisplayName("dataForm: переключается")
    void dataFormSwitch() {
        RegistrationForm f = new RegistrationForm();
        f.setDataForm(true);
        assertTrue(f.isDataForm());
    }

    @Test
    @DisplayName("dataForm: можно выключить обратно")
    void dataFormToggleBack() {
        RegistrationForm f = new RegistrationForm();
        f.setDataForm(true);
        f.setDataForm(false);
        assertFalse(f.isDataForm());
    }

    // ============================================================
    // getFieldMap
    // ============================================================

    @Test
    @DisplayName("getFieldMap: возвращает неизменяемую мапу")
    void fieldMapImmutable() {
        RegistrationForm f = new RegistrationForm();
        f.setValue("x", "1");
        assertThrows(UnsupportedOperationException.class,
                () -> f.getFieldMap().put("y", null));
    }

    @Test
    @DisplayName("getFieldMap: содержит Field с type и var")
    void fieldMapHasField() {
        RegistrationForm f = new RegistrationForm();
        f.addField("password", "", "text-private");
        RegistrationForm.Field field = f.getFieldMap().get("password");
        assertNotNull(field);
        assertEquals("password", field.getVar());
        assertEquals("text-private", field.getType());
    }

    // ============================================================
    // toString / маскирование
    // ============================================================

    @Test
    @DisplayName("toString: пароль маскируется как ***")
    void toStringMasksPassword() {
        RegistrationForm f = new RegistrationForm();
        f.setCredentials("bob", "topsecret");
        String s = f.toString();
        assertTrue(s.contains("username=bob"));
        assertTrue(s.contains("password=***"));
        assertFalse(s.contains("topsecret"));
    }

    @Test
    @DisplayName("toString: не падает на пустой форме")
    void toStringEmpty() {
        RegistrationForm f = new RegistrationForm();
        assertDoesNotThrow(f::toString);
    }

    @Test
    @DisplayName("toString: содержит dataForm-флаг")
    void toStringHasDataFormFlag() {
        RegistrationForm f = new RegistrationForm();
        f.setDataForm(true);
        assertTrue(f.toString().contains("dataForm=true"));
    }

    // ============================================================
    // Field
    // ============================================================

    @Test
    @DisplayName("Field: getLabel без label → возвращает var")
    void fieldLabelFallback() {
        RegistrationForm.Field field = new RegistrationForm.Field("username", "text");
        assertEquals("username", field.getLabel());
    }

    @Test
    @DisplayName("Field: getType сохраняется как передан")
    void fieldType() {
        RegistrationForm.Field field = new RegistrationForm.Field("x", "boolean");
        assertEquals("boolean", field.getType());
    }

    @Test
    @DisplayName("Field: options по умолчанию пусты")
    void fieldOptionsEmpty() {
        RegistrationForm.Field field = new RegistrationForm.Field("x", "text");
        assertTrue(field.getOptions().isEmpty());
    }

    @Test
    @DisplayName("Field: options иммутабельны снаружи")
    void fieldOptionsImmutable() {
        RegistrationForm.Field field = new RegistrationForm.Field("x", "text");
        assertThrows(UnsupportedOperationException.class,
                () -> field.getOptions().add("hack"));
    }

    @Test
    @DisplayName("Field: setValue / getValue")
    void fieldValue() {
        RegistrationForm.Field field = new RegistrationForm.Field("x", "text");
        field.setValue("hello");
        assertEquals("hello", field.getValue());
    }
}