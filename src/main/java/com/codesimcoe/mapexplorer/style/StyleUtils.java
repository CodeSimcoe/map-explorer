package com.codesimcoe.mapexplorer.style;

import javafx.scene.Node;
import javafx.scene.Scene;

public final class StyleUtils {

    // Dark theme
    private static final String THEME = "/style/theme.css";

    private StyleUtils() {
    }

    public static void setTheme(final Scene scene) {
        String stylesheet = StyleUtils.class.getResource(THEME).toExternalForm();
        scene.getStylesheets().add(stylesheet);
    }

    public static void setStyleClass(final Node node, final String styleClass) {
        node.getStyleClass().clear();
        node.getStyleClass().add(styleClass);
    }
}
