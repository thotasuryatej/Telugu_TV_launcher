package com.gathin.photodream;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which files the screensaver shows and FilesProvider accepts, and how big a picture is decoded. */
final class Media {
    static final String PICTURES = "pictures";
    static final String SECONDS = "seconds.txt";
    static final int DEFAULT_SECONDS = 20;
    static final int MAX_FILES = 100;
    static final long IMAGE_MAX = 10L * 1024 * 1024;
    static final long VIDEO_MAX = 1024L * 1024 * 1024;
    /** 4K UHD and a little more; a bigger picture is skipped rather than decoded on a 1 GB TV. */
    static final long MAX_PIXELS = 8_400_000L;

    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO = Pattern.compile("[A-Za-z0-9_.-]+\\.mp4", Pattern.CASE_INSENSITIVE);

    private Media() {}

    static boolean image(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }

    static boolean video(String name) {
        return name != null && !name.startsWith(".") && VIDEO.matcher(name).matches();
    }

    static boolean file(String name) {
        return image(name) || video(name);
    }

    /** The path under the files dir that a provider URI may write or delete, or null. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1 && SECONDS.equals(segments.get(0))) return SECONDS;
        if (segments.size() == 2 && PICTURES.equals(segments.get(0)) && file(segments.get(1))) {
            return PICTURES + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "pictures" alone: a delete of it clears every picture and clip. */
    static boolean isFolder(List<String> segments) {
        return segments != null && segments.size() == 1 && PICTURES.equals(segments.get(0));
    }

    /** The folder's pictures and clips by name, at most 100; empty and oversized files are left out. */
    static List<File> list(File folder) {
        return list(folder, MAX_FILES);
    }

    private static List<File> list(File folder, int max) {
        List<File> result = new ArrayList<>();
        File[] entries = folder == null ? null : folder.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entry : entries) {
            if (!entry.isFile() || entry.length() <= 0) continue;
            String name = entry.getName();
            boolean ok = video(name) ? entry.length() <= VIDEO_MAX : image(name) && entry.length() <= IMAGE_MAX;
            if (ok) result.add(entry);
            if (result.size() == max) break;
        }
        return result;
    }

    /** Pictures the TV keeps in DCIM, its subfolders included; the screensaver reads them each time it starts. */
    static final int MAX_GALLERY = 2000;

    /** The app's own pictures, then the TV's DCIM pictures: what the screensaver shows. */
    static List<File> all(File appDir) {
        List<File> result = appDir == null ? new ArrayList<>() : list(new File(appDir, PICTURES), MAX_GALLERY);
        java.util.Set<String> off = excluded(appDir);
        for (File f : gallery(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DCIM), namesOf(result))) {
            if (!off.contains(f.getPath())) result.add(f);
        }
        android.util.Log.i("PhotoDream", "pictures found: " + result.size());
        return result;
    }

    private static final String EXCLUDED = "excluded.txt";

    /** The DCIM pictures taken off the screensaver in PicturesActivity, by path. */
    static java.util.Set<String> excluded(File appDir) {
        java.util.Set<String> paths = new java.util.HashSet<>();
        File file = appDir == null ? null : new File(appDir, EXCLUDED);
        if (file == null || !file.isFile()) return paths;
        try {
            for (String line : java.nio.file.Files.readAllLines(file.toPath())) if (!line.isEmpty()) paths.add(line);
        } catch (java.io.IOException ignored) {
            // Unreadable: every DCIM picture shows, which is the default.
        }
        return paths;
    }

    static void saveExcluded(File appDir, java.util.Set<String> paths) {
        if (appDir == null) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(appDir, EXCLUDED))) {
            out.write(String.join(System.lineSeparator(), paths).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {
            // The picker shows the choice for now; it is only lost if the TV's storage cannot be written.
        }
    }

    static boolean picture(String name) {
        return PICTURE.matcher(name).matches();
    }

    static boolean under(File file, File folder) {
        return folder != null && file.getPath().startsWith(folder.getPath() + File.separator);
    }

    /** Where a picture from outside DCIM is copied: a name list() accepts, made unique by the source's path. */
    static File copyTarget(File appDir, File picture) {
        if (appDir == null) return null;
        String name = picture.getName();
        int dot = name.lastIndexOf('.');
        String base = name.substring(0, dot).replaceAll("[^A-Za-z0-9_-]", "_");
        String id = Integer.toHexString(picture.getPath().hashCode());
        return new File(new File(appDir, PICTURES), "usb-" + id + "-" + base + name.substring(dot).toLowerCase());
    }

    static void copy(File from, File to) throws java.io.IOException {
        File folder = to.getParentFile();
        if (folder != null) folder.mkdirs();
        java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static java.util.Set<String> namesOf(List<File> files) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (File f : files) names.add(f.getName());
        return names;
    }

    /** Pictures under {@code root} by name. {@code taken} keeps names unique, as next() finds the place by name. */
    static List<File> gallery(File root, java.util.Set<String> taken) {
        List<File> result = new ArrayList<>();
        collect(root, taken, result, 0);
        result.sort(Comparator.comparing(File::getName));
        return result;
    }

    private static final Pattern PICTURE = Pattern.compile(".+[.](jpg|jpeg|png|webp)", Pattern.CASE_INSENSITIVE);

    private static void collect(File folder, java.util.Set<String> taken, List<File> into, int depth) {
        File[] entries = folder == null || depth > 4 ? null : folder.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (into.size() >= MAX_GALLERY) return;
            String name = entry.getName();
            if (name.startsWith(".")) continue;
            if (entry.isDirectory()) collect(entry, taken, into, depth + 1);
            else if (PICTURE.matcher(name).matches() && entry.length() > 0 && entry.length() <= IMAGE_MAX
                    && taken.add(name)) into.add(entry);
        }
    }

    /** The file after {@code previous} by name, wrapping around; the first one when previous is gone. */
    static File next(List<File> files, String previous) {
        if (files.isEmpty()) return null;
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).getName().equals(previous)) return files.get((i + 1) % files.size());
        }
        return files.get(0);
    }

    /** seconds.txt's number, 5 to 3600; 20 when it is missing or not a number. */
    static int seconds(String text) {
        if (text == null) return DEFAULT_SECONDS;
        try {
            int s = Integer.parseInt(text.trim());
            return s < 5 || s > 3600 ? DEFAULT_SECONDS : s;
        } catch (NumberFormatException e) {
            return DEFAULT_SECONDS;
        }
    }

    private static final Pattern NAME_DATE = Pattern.compile("-(\\d{4})(\\d{2})(\\d{2})?\\.[A-Za-z0-9]+$");

    /** {year, month} from a name ending in its date, photo-20231202.jpg or photo-202312.jpg; else null. */
    static int[] dateFromName(String name) {
        Matcher m = name == null ? null : NAME_DATE.matcher(name);
        if (m == null || !m.find()) return null;
        int year = Integer.parseInt(m.group(1)), month = Integer.parseInt(m.group(2));
        return year >= 1900 && month >= 1 && month <= 12 ? new int[]{year, month} : null;
    }

    static boolean accepted(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }

    /** The largest power-of-two sample size that still covers the screen in both directions. */
    static int sampleSize(int width, int height, int screenW, int screenH) {
        int sample = 1;
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2;
        return sample;
    }

    /** {inDensity, inTargetDensity} that scale a sampled picture down to what CENTER_CROP shows, or null. */
    static int[] coverScale(int width, int height, int screenW, int screenH) {
        if ((long) screenW * height >= (long) screenH * width) {
            return width > screenW ? new int[]{width, screenW} : null;
        }
        return height > screenH ? new int[]{height, screenH} : null;
    }
}
