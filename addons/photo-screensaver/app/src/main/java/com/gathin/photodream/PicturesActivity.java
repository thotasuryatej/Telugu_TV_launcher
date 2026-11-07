package com.gathin.photodream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pick the screensaver's pictures with the remote: browse the TV and any USB drive, and OK on a picture puts it on
 * the screensaver or takes it off. A DCIM picture is on unless taken off; any other picture is copied in.
 */
public class PicturesActivity extends Activity {
    private static final int COLUMNS = 5;

    /** One tile: a folder, or a picture. */
    private static final class Item {
        final File file;
        final String label;
        final boolean folder;
        final Runnable action;
        final int icon;

        Item(File file, String label, boolean folder) {
            this.file = file;
            this.label = label;
            this.folder = folder;
            this.action = null;
            this.icon = android.R.drawable.ic_menu_gallery;
        }

        /** A button in the grid instead of a file. */
        Item(String label, int icon, Runnable action) {
            this.file = new File(label);
            this.label = label;
            this.folder = true;
            this.action = action;
            this.icon = icon;
        }
    }

    /** A picture on a USB drive. {@code key} is its path, only a name: this TV lets no app open it as a file. */
    private static final class DrivePicture {
        final android.net.Uri uri;
        final File key;
        final long size;

        DrivePicture(android.net.Uri uri, File key, long size) {
            this.uri = uri;
            this.key = key;
            this.size = size;
        }
    }

    /** Every removable drive's folder, its media index name and shown name. */
    private final java.util.Map<File, String[]> drives = new java.util.HashMap<>();
    private final java.util.Map<String, DrivePicture> drivePictures = new java.util.HashMap<>();
    private File loadedDrive;
    private final List<Item> items = new ArrayList<>();
    private final Deque<File> trail = new ArrayDeque<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final BaseAdapter adapter = new TileAdapter();
    private TextView heading;
    private GridView grid;
    private File appDir;
    private File dcim;
    private Set<String> excluded;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        appDir = getExternalFilesDir(null);
        dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        excluded = Media.excluded(appDir);
        int pad = dp(32);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0D14);
        root.setPadding(pad, dp(20), pad, dp(12));
        heading = new TextView(this);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(22);
        root.addView(heading);
        TextView help = new TextView(this);
        help.setTextColor(0xB3FFFFFF);
        help.setTextSize(14);
        help.setText("OK on a picture adds or removes it. Hold OK to preview it as the screensaver. Back goes up.");
        help.setPadding(0, dp(4), 0, dp(12));
        root.addView(help);
        grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setVerticalSpacing(dp(10));
        grid.setHorizontalSpacing(dp(10));
        grid.setDrawSelectorOnTop(true);
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(Color.TRANSPARENT);
        focus.setStroke(dp(3), Color.WHITE);
        focus.setCornerRadius(dp(8));
        grid.setSelector(focus);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> press(items.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            Item item = items.get(position);
            if (item.folder) return false;
            preview(item.file);
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        showRoots();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showRoots() {
        items.clear();
        items.add(new Item("Preview screensaver", android.R.drawable.ic_media_play, () -> preview(null)));
        items.add(new Item("Starts after " + idleLabel(), android.R.drawable.ic_lock_idle_alarm, this::nextIdleTime));
        items.add(new Item(dcim, "This TV", true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StorageManager storage = getSystemService(StorageManager.class);
            for (StorageVolume volume : storage.getStorageVolumes()) {
                File dir = volume.getDirectory();
                if (volume.isRemovable() && dir != null) {
                    String label = volume.getDescription(this);
                    drives.put(dir, new String[] {volume.getMediaStoreVolumeName(), label});
                    items.add(new Item(dir, label, true));
                }
            }
        }
        refresh("Screensaver pictures");
    }

    private File driveOf(File folder) {
        for (File drive : drives.keySet()) {
            if (folder.equals(drive) || Media.under(folder, drive)) return drive;
        }
        return null;
    }

    /** Reads a drive's pictures from the media index, once each time the drive is opened from the top. */
    private void loadDrive(File drive) {
        if (drive.equals(loadedDrive)) return;
        drivePictures.clear();
        loadedDrive = drive;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        android.net.Uri base = android.provider.MediaStore.Images.Media.getContentUri(drives.get(drive)[0]);
        String[] columns = {android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.provider.MediaStore.MediaColumns.SIZE};
        try (android.database.Cursor c = getContentResolver().query(base, columns, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(1), relative = c.getString(2);
                if (name == null || !Media.picture(name)) continue;
                File key = new File(new File(drive, relative == null ? "" : relative), name);
                drivePictures.put(key.getPath(), new DrivePicture(
                        android.content.ContentUris.withAppendedId(base, c.getLong(0)), key, c.getLong(3)));
            }
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not read this drive", Toast.LENGTH_LONG).show();
        }
    }

    private void showDriveFolder(File folder, File drive) {
        loadDrive(drive);
        items.clear();
        String prefix = folder.getPath() + File.separator;
        java.util.TreeSet<String> folders = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<DrivePicture> here = new ArrayList<>();
        for (DrivePicture p : drivePictures.values()) {
            String path = p.key.getPath();
            if (!path.startsWith(prefix)) continue;
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf(File.separatorChar);
            if (slash >= 0) folders.add(rest.substring(0, slash));
            else here.add(p);
        }
        here.sort(Comparator.comparing(p -> p.key.getName().toLowerCase()));
        if (!here.isEmpty()) {
            boolean allOn = true;
            for (DrivePicture p : here) if (!shown(p.key)) allOn = false;
            boolean remove = allOn;
            items.add(new Item((remove ? "Remove all " : "Add all ") + here.size() + " here",
                    remove ? android.R.drawable.ic_menu_delete : android.R.drawable.ic_input_add,
                    () -> addAll(here, remove, folder)));
        }
        for (String name : folders) items.add(new Item(new File(folder, name), name, true));
        for (DrivePicture p : here) items.add(new Item(p.key, p.key.getName(), false));
        refresh(folder.equals(drive) ? drives.get(drive)[1] : folder.getName());
    }

    /** Copies a whole folder onto the screensaver, or takes it off, then shows the folder again. */
    private void addAll(List<DrivePicture> pictures, boolean remove, File folder) {
        Toast.makeText(this, remove ? "Removing..." : "Adding " + pictures.size() + " pictures. This can take a while.",
                Toast.LENGTH_LONG).show();
        loader.execute(() -> {
            int done = 0;
            for (DrivePicture p : pictures) {
                File copy = Media.copyTarget(appDir, p.key);
                if (copy == null) continue;
                if (remove) {
                    if (copy.delete()) done++;
                } else if (p.size <= Media.IMAGE_MAX && !copy.exists() && copyIn(p, copy)) {
                    done++;
                }
            }
            int count = done;
            runOnUiThread(() -> {
                Toast.makeText(this, (remove ? "Removed " : "Added ") + count + " pictures", Toast.LENGTH_LONG).show();
                if (folder.equals(trail.peek())) showFolder(folder);
            });
        });
    }

    private boolean copyIn(DrivePicture picture, File copy) {
        File folder = copy.getParentFile();
        if (folder != null) folder.mkdirs();
        // This TV's media index gives an I/O error on a pen drive; with all-files access the file itself opens.
        try (java.io.InputStream in = picture.key.canRead() ? new java.io.FileInputStream(picture.key)
                : getContentResolver().openInputStream(picture.uri)) {
            java.nio.file.Files.copy(in, copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("PhotoDream", "copy failed: " + picture.uri, e);
            copy.delete();
            return false;
        }
    }

    private void showFolder(File folder) {
        File drive = driveOf(folder);
        if (drive != null) {
            showDriveFolder(folder, drive);
            return;
        }
        items.clear();
        File[] entries = folder.listFiles();
        if (entries != null) {
            Arrays.sort(entries, Comparator.comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase()));
            for (File entry : entries) {
                String name = entry.getName();
                if (name.startsWith(".") || (entry.isDirectory() && name.equals("Android"))) continue;
                if (entry.isDirectory()) items.add(new Item(entry, name, true));
                else if (Media.picture(name)) items.add(new Item(entry, name, false));
            }
        }
        refresh(folder.getName());
    }

    private void refresh(String title) {
        heading.setText(title);
        adapter.notifyDataSetChanged();
        grid.setSelection(0);
        grid.requestFocus();
    }

    private static final int[] IDLE_MINUTES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    private int idleMinutes() {
        int ms = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 600000);
        return Math.max(1, ms / 60000);
    }

    private String idleLabel() {
        int minutes = idleMinutes();
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    /** The idle time the TV waits before its screensaver: the next choice up, wrapping around. */
    private void nextIdleTime() {
        if (!android.provider.Settings.System.canWrite(this)) {
            Toast.makeText(this, "This TV has not allowed changing the time", Toast.LENGTH_LONG).show();
            return;
        }
        int now = idleMinutes(), next = IDLE_MINUTES[0];
        for (int choice : IDLE_MINUTES) {
            if (choice > now) {
                next = choice;
                break;
            }
        }
        android.provider.Settings.System.putInt(getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT, next * 60000);
        int position = grid.getSelectedItemPosition();
        showRoots();
        grid.setSelection(position);
    }

    /** Opens the slideshow the screensaver shows; on {@code first} when it is one of the pictures shown. */
    private void preview(File first) {
        List<File> shown = Media.all(appDir);
        if (shown.isEmpty()) {
            Toast.makeText(this, "No pictures on the screensaver yet", Toast.LENGTH_LONG).show();
            return;
        }
        if (first != null) {
            File target = Media.under(first, dcim) ? first : Media.copyTarget(appDir, first);
            int at = -1;
            for (int i = 0; i < shown.size(); i++) if (shown.get(i).equals(target)) at = i;
            if (at < 0) {
                Toast.makeText(this, "Add this picture first, then hold OK to preview it", Toast.LENGTH_LONG).show();
                return;
            }
            // The slideshow opens on the picture after the one it last showed.
            getSharedPreferences("photo_dream", MODE_PRIVATE).edit()
                    .putString("image", shown.get((at - 1 + shown.size()) % shown.size()).getName()).commit();
        }
        startActivity(new android.content.Intent(this, SlideshowActivity.class));
    }

    private void press(Item item) {
        if (item.action != null) {
            item.action.run();
        } else if (item.folder) {
            trail.push(item.file);
            showFolder(item.file);
        } else if (Media.under(item.file, dcim)) {
            String path = item.file.getPath();
            if (!excluded.remove(path)) excluded.add(path);
            Media.saveExcluded(appDir, excluded);
            adapter.notifyDataSetChanged();
        } else {
            toggleCopy(item.file);
        }
    }

    private void toggleCopy(File picture) {
        File copy = Media.copyTarget(appDir, picture);
        DrivePicture onDrive = drivePictures.get(picture.getPath());
        if (copy == null || (onDrive != null ? onDrive.size : picture.length()) > Media.IMAGE_MAX) {
            Toast.makeText(this, "This picture is too big for the screensaver", Toast.LENGTH_LONG).show();
            return;
        }
        if (copy.exists()) {
            copy.delete();
            adapter.notifyDataSetChanged();
            return;
        }
        loader.execute(() -> {
            String problem = null;
            if (onDrive != null) {
                if (!copyIn(onDrive, copy)) problem = "Could not add this picture";
            } else {
                try {
                    Media.copy(picture, copy);
                } catch (IOException e) {
                    copy.delete();
                    problem = "Could not add this picture";
                }
            }
            String message = problem;
            runOnUiThread(() -> {
                if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                adapter.notifyDataSetChanged();
            });
        });
    }

    private boolean shown(File picture) {
        if (Media.under(picture, dcim)) return !excluded.contains(picture.getPath());
        File copy = Media.copyTarget(appDir, picture);
        return copy != null && copy.exists();
    }

    @Override
    public void onBackPressed() {
        if (trail.isEmpty()) {
            super.onBackPressed();
            return;
        }
        trail.pop();
        if (trail.isEmpty()) {
            loadedDrive = null;   // opening the drive again picks up what was added to it
            showRoots();
        } else {
            showFolder(trail.peek());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loader.shutdownNow();
    }

    private Bitmap thumbnail(File file) {
        DrivePicture onDrive = drivePictures.get(file.getPath());
        if (onDrive == null || file.canRead()) return Photos.decode(file, 320, 180);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            return getContentResolver().loadThumbnail(onDrive.uri, new android.util.Size(320, 180), null);
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("PhotoDream", "thumbnail failed: " + onDrive.uri, e);
            return null;
        }
    }

    private final class TileAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View reuse, ViewGroup parent) {
            Context context = parent.getContext();
            Tile tile = reuse instanceof Tile ? (Tile) reuse : new Tile(context);
            tile.bind(items.get(position));
            return tile;
        }
    }

    private final class Tile extends LinearLayout {
        private final ImageView image = new ImageView(getContext());
        private final TextView mark = new TextView(getContext());
        private final TextView name = new TextView(getContext());
        private String path;

        Tile(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(4), dp(4), dp(4), dp(4));
            FrameLayout frame = new FrameLayout(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xFF1C2030);
            frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            mark.setText("✓");
            mark.setTextColor(Color.WHITE);
            mark.setTextSize(16);
            mark.setGravity(Gravity.CENTER);
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(0xFF1E9E5A);
            mark.setBackground(badge);
            FrameLayout.LayoutParams markLp = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.END | Gravity.TOP);
            markLp.setMargins(0, dp(6), dp(6), 0);
            frame.addView(mark, markLp);
            addView(frame, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));
            name.setTextColor(0xE6FFFFFF);
            name.setTextSize(12);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(4), 0, 0);
            addView(name);
        }

        void bind(Item item) {
            path = item.file.getPath();
            name.setText(item.label);
            image.setImageDrawable(null);
            if (item.folder) {
                mark.setVisibility(GONE);
                image.setImageResource(item.icon);
                image.setScaleType(ImageView.ScaleType.CENTER);
                return;
            }
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mark.setVisibility(shown(item.file) ? VISIBLE : GONE);
            Bitmap cached = thumbs.get(path);
            if (cached != null) {
                image.setImageBitmap(cached);
                return;
            }
            String wanted = path;
            loader.execute(() -> {
                if (!wanted.equals(path)) return;
                Bitmap bitmap = thumbnail(item.file);
                if (bitmap == null) return;
                runOnUiThread(() -> {
                    thumbs.put(wanted, bitmap);
                    if (wanted.equals(path)) image.setImageBitmap(bitmap);
                });
            });
        }
    }
}
