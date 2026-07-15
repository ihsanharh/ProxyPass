package com.ihsanharh.hiveutils.utils;

import java.util.ArrayList;
import java.util.List;

import com.ihsanharh.hiveutils.core.SimpleForm;

public class SimpleFormBuilder {
    private final SimpleForm form;
    private final List<String> actions = new ArrayList<>();

    public SimpleFormBuilder(String title, String content) {
        form = new SimpleForm(title, content);
    }

    public SimpleFormBuilder add(String label, String actionId) {
        form.addButton(label);
        actions.add(actionId);

        return this;
    }

    public SimpleFormBuilder add(String label, String actionId, String imageUrl) {
        form.addButton(label, imageUrl);
        actions.add(actionId);

        return this;
    }

    public SimpleForm build() {
        return form;
    }

    public String parseAction(String response) {
        if (response == null || response.trim().isEmpty() || response.equals("null")) {
            return null;
        }
        try {
            return actions.get(Integer.parseInt(response.trim()));
        } catch (Exception e) {
            return null;
        }
    }
}