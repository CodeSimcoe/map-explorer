package com.codesimcoe.mapexplorer;

import javafx.scene.paint.Color;

public class ColorConstants {

    /**
     *   Color color = Color.BLACK;
     *   int a = 255;
     *   int r = (int) (color.getRed() * 255);
     *   int g = (int) (color.getGreen() * 255);
     *   int b = (int) (color.getBlue() * 255);
     *   int argb = (a << 24) | (r << 16) | (g << 8) | b;
     */
    public static final int BLACK_ARGB = -16777216;

    public static final Color ERASER_OVERLAY = Color.RED;
    public static final Color BRUSH_OVERLAY = Color.GREEN;

    private ColorConstants() {
        // Non-instantiable
    }
}