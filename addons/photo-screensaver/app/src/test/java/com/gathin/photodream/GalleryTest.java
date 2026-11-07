package com.gathin.photodream;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

/** The screensaver finds pictures in the folder and its subfolders, and nothing else. */
public class GalleryTest {
    private static void write(File f) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(1); }
    }

    @Test public void findsPicturesInSubfoldersOnly() throws IOException {
        File root = Files.createTempDirectory("dcim").toFile();
        write(new File(root, "top.jpg"));
        write(new File(root, "Download/IMG 1 (copy).JPEG"));
        write(new File(root, "Download/note.txt"));
        write(new File(root, ".thumbnails/hidden.jpg"));
        new File(root, "empty.png").createNewFile();
        List<String> names = new ArrayList<>();
        for (File f : Media.gallery(root, new HashSet<>())) names.add(f.getName());
        assertEquals(List.of("IMG 1 (copy).JPEG", "top.jpg"), names);
    }

    @Test public void aRepeatedNameIsShownOnce() throws IOException {
        File root = Files.createTempDirectory("dcim").toFile();
        write(new File(root, "a/same.jpg"));
        write(new File(root, "b/same.jpg"));
        assertEquals(1, Media.gallery(root, new HashSet<>()).size());
    }
}
