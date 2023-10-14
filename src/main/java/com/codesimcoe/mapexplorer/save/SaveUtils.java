package com.codesimcoe.mapexplorer.save;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SaveUtils {

    private SaveUtils() {
        // Non-instantiable
    }

    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream out = new GZIPOutputStream(bos);
        out.write(data);
        out.close();
        return bos.toByteArray();
    }

    public static byte[] decompress(byte[] data) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        GZIPInputStream in = new GZIPInputStream(bis);
        return in.readAllBytes();
    }

    public static void saveToFile(SaveData data, String filename) throws IOException {
        byte[] compressed = compress(data.pixels());
        SaveData compressedData = new SaveData(data.width(), data.height(), compressed);

        try (FileOutputStream fos = new FileOutputStream(filename);
            ObjectOutputStream oos = new ObjectOutputStream(fos)) {

            oos.writeObject(compressedData);
        }
    }

    public static SaveData loadFromFile(String filename) throws IOException, ClassNotFoundException {

        try (FileInputStream fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis)) {

            SaveData data = (SaveData) ois.readObject();

            byte[] decompressed = decompress(data.pixels());
            return new SaveData(data.width(), data.height(), decompressed);
        }
    }
}
