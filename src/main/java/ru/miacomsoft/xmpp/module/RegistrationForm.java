package ru.miacomsoft.xmpp.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель формы регистрации: набор полей + метаданные.
 * Может представлять как XEP-0004 Data Form, так и классическую форму.
 */
public class RegistrationForm {

    /** Описание одного поля. */
    public static class Field {
        private final String var;
        private final String type;      // text, boolean, list-single, hidden, ...
        private final List<String> options = new ArrayList<>();
        private String label;
        private String value;
        private boolean required;

        public Field(String var, String type) {
            this.var = var;
            this.type = type;
        }

        public String getVar() { return var; }
        public String getType() { return type; }
        public String getLabel() { return label != null ? label : var; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public List<String> getOptions() { return Collections.unmodifiableList(options); }
        void addOption(String o) { options.add(o); }
        void setLabel(String l) { this.label = l; }
    }

    private boolean dataForm;
    private String instructions = "";
    private final Map<String, Field> fields = new LinkedHashMap<>();

    public boolean isDataForm() { return dataForm; }
    public void setDataForm(boolean dataForm) { this.dataForm = dataForm; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) {
        this.instructions = instructions != null ? instructions : "";
    }

    public Map<String, Field> getFieldMap() { return Collections.unmodifiableMap(fields); }

    /** Возвращает только var → value — то, что отправляется на сервер. */
    public Map<String, String> getFields() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Field f : fields.values()) {
            out.put(f.getVar(), f.getValue());
        }
        return out;
    }

    public void addField(String var, String defaultValue, String type) {
        Field f = new Field(var, type);
        f.setValue(defaultValue);
        fields.put(var, f);
    }

    public void setValue(String var, String value) {
        Field f = fields.get(var);
        if (f == null) {
            addField(var, value, "text");
        } else {
            f.setValue(value);
        }
    }

    public String getValue(String var) {
        Field f = fields.get(var);
        return f != null ? f.getValue() : null;
    }

    public boolean hasField(String var) { return fields.containsKey(var); }

    void setLabel(String var, String label) {
        Field f = fields.get(var);
        if (f != null) f.setLabel(label);
    }

    void setOptions(String var, List<String> options) {
        Field f = fields.get(var);
        if (f != null) for (String o : options) f.addOption(o);
    }

    /** Удобный шорткат для типового случая. */
    public void setCredentials(String username, String password) {
        setValue("username", username);
        setValue("password", password);
    }

    /** Краткий дамп для логирования. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RegistrationForm{dataForm=")
                .append(dataForm).append(", fields=[");
        boolean first = true;
        for (Field f : fields.values()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(f.getVar()).append("=")
                    .append("password".equals(f.getVar()) ? "***" : f.getValue());
        }
        return sb.append("]}").toString();
    }
}