package com.ihsanharh.hiveutils.forms;

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

    @Override
    public String getType() {
        return type;
    }

    @Getter
    @Setter
    public static class Button {
        private String text;

        public Button(String text) {
            this.text = text;
        }
    }
}
