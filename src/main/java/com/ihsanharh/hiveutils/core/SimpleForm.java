package com.ihsanharh.hiveutils.core;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SimpleForm extends Form {
    private final String type = "form";
    private String title = "";
    private String content = "";
    private List<Button> buttons = new ArrayList<>();

    public SimpleForm(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void addButton(String text) {
        this.buttons.add(new Button(text));
    }

    public void addButton(String text, String imageUrl) {
        this.buttons.add(new Button(text, imageUrl));
    }

    @Override
    public String getType() {
        return type;
    }

    @Getter
    @Setter
    public static class Button {
        private String text;
        private FormImage image; // Null by default, so GSON/Jackson will ignore it if empty

        public Button(String text) {
            this.text = text;
        }

        public Button(String text, String imageUrl) {
            this.text = text;
            this.image = new FormImage(imageUrl);
        }
    }

    @Getter
    @Setter
    public static class FormImage {
        private String type = "url";
        private String data;

        public FormImage(String data) {
            this.data = data;
        }
    }
}
