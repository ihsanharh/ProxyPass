package com.ihsanharh.hiveutils.core;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CustomForm extends Form {
    private final String type = "custom_form";
    private String title = "";
    private List<Element> content = new ArrayList<>();

    public CustomForm(String title) {
        this.title = title;
    }

    public void addToggle(String text, boolean defaultValue) {
        Toggle toggle = new Toggle();
        toggle.setText(text);
        toggle.setDefaultValue(defaultValue);
        this.content.add(toggle);
    }

    public void addInput(String text, String placeholder, String defaultValue) {
        Input input = new Input();
        input.setText(text);
        input.setPlaceholder(placeholder);
        input.setDefaultValue(defaultValue != null ? defaultValue : "");
        this.content.add(input);
    }

    public void addDropdown(String text, List<String> options, int defaultOption) {
        Dropdown dropdown = new Dropdown();
        dropdown.setText(text);
        dropdown.setOptions(options);
        dropdown.setDefaultValue(defaultOption);
        this.content.add(dropdown);
    }

    public void addLabel(String text) {
        Label label = new Label();
        label.setText(text);
        this.content.add(label);
    }

    @Override
    public String getType() {
        return type;
    }

    @Getter
    @Setter
    public static abstract class Element {
        private String type;
        private String text;
    }

    @Getter
    @Setter
    public static class Toggle extends Element {
        private boolean defaultValue;

        public Toggle() {
            setType("toggle");
        }

        @com.fasterxml.jackson.annotation.JsonProperty("default")
        public boolean isDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(boolean d) {
            this.defaultValue = d;
        }
    }

    @Getter
    @Setter
    public static class Input extends Element {
        private String placeholder = "";
        private String defaultValue = "";

        public Input() {
            setType("input");
        }

        @com.fasterxml.jackson.annotation.JsonProperty("default")
        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String d) {
            this.defaultValue = d;
        }
    }

    @Getter
    @Setter
    public static class Dropdown extends Element {
        private List<String> options = new ArrayList<>();
        private int defaultValue = 0;

        public Dropdown() {
            setType("dropdown");
        }

        @com.fasterxml.jackson.annotation.JsonProperty("default")
        public int getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(int d) {
            this.defaultValue = d;
        }
    }

    @Getter
    @Setter
    public static class Label extends Element {
        public Label() {
            setType("label");
        }
    }
}
