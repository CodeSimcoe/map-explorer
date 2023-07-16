package com.codesimcoe.mapexplorer;

import java.io.Serializable;

public record SaveData(
    int width,
    int height,
    byte[] pixels) implements Serializable {
}