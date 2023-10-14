package com.codesimcoe.mapexplorer.save;

import java.io.Serializable;

public record SaveData(
    int width,
    int height,
    byte[] pixels) implements Serializable {
}