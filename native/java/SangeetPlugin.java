package np.ganesh.sangeet;

import android.Manifest;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Rational;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CapacitorPlugin(
    name = "SangeetNative",
    permissions = {
        @Permission(alias = "audio", strings = {Manifest.permission.READ_MEDIA_AUDIO}),
        @Permission(alias = "video", strings = {Manifest.permission.READ_MEDIA_VIDEO}),
        @Permission(alias = "storage", strings = {Manifest.permission.READ_EXTERNAL_STORAGE}),
        @Permission(alias = "notif", strings = {"android.permission.POST_NOTIFICATIONS"})
    }
)
public class SangeetPlugin extends Plugin {
    static SangeetPlugin inst;
    static volatile boolean videoActive = false;
    static String pendingAlarm = null;

    private final Map<String, OutputStream> outs = new HashMap<>();
    private final Map<String, Uri> outUris = new HashMap<>();
    private final Map<String, File> outFiles = new HashMap<>();

    @Override
    public void load() { inst = this; }

    static void emitPip(boolean on) {
        if (inst != null) {
            JSObject o = new JSObject();
            o.put("on", on);
            inst.notifyListeners("pip", o);
        }
    }

    static void emitAlarm(String id) {
        pendingAlarm = id;
        if (inst != null) {
            JSObject o = new JSObject();
            o.put("id", id);
            inst.notifyListeners("alarm", o);
        }
    }

    private String[] libAliases() {
        if (Build.VERSION.SDK_INT >= 33) return new String[]{"audio", "video"};
        return new String[]{"storage"};
    }

    private boolean hasLib() {
        for (String a : libAliases()) {
            if (getPermissionState(a) != com.getcapacitor.PermissionState.GRANTED) return false;
        }
        return true;
    }

    /* ---------- permissions ---------- */
    @PluginMethod
    public void requestLibrary(PluginCall call) {
        if (hasLib()) {
            JSObject o = new JSObject();
            o.put("granted", true);
            call.resolve(o);
            return;
        }
        requestPermissionForAliases(libAliases(), call, "libCb");
    }

    @PermissionCallback
    private void libCb(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", hasLib());
        call.resolve(o);
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notif") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAliases(new String[]{"notif"}, call, "notifCb");
        } else {
            JSObject o = new JSObject();
            o.put("granted", true);
            call.resolve(o);
        }
    }

    @PermissionCallback
    private void notifCb(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", getPermissionState("notif") == com.getcapacitor.PermissionState.GRANTED);
        call.resolve(o);
    }

    /* ---------- library scan (MediaStore) ---------- */
    @PluginMethod
    public void scan(PluginCall call) {
        if (!hasLib()) {
            call.reject("permission");
            return;
        }
        new Thread(() -> {
            try {
                JSArray arr = new JSArray();
                query(arr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "a");
                query(arr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "v");
                JSObject o = new JSObject();
                o.put("files", arr);
                call.resolve(o);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private void query(JSArray out, Uri uri, String kind) {
        ContentResolver cr = getContext().getContentResolver();
        String[] proj = {MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE};
        try (Cursor c = cr.query(uri, proj, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String n = c.getString(0), p = c.getString(1);
                if (n == null || p == null) continue;
                if (p.contains("/Android/data/") || p.contains("/Android/obb/")) continue;
                if (kind.equals("a") && (p.contains("/Ringtones/") || p.contains("/Notifications/") || p.contains("/Alarms/"))) continue;
                JSObject o = new JSObject();
                o.put("n", n);
                o.put("p", p);
                o.put("k", kind);
                o.put("s", c.getLong(2));
                out.put(o);
            }
        }
    }

    /* ---------- picture in picture ---------- */
    @PluginMethod
    public void setVideoActive(PluginCall call) {
        videoActive = call.getBoolean("on", false);
        call.resolve();
    }

    static boolean enterPip(Activity a) {
        if (Build.VERSION.SDK_INT < 26) return false;
        try {
            PictureInPictureParams p = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
            return a.enterPictureInPictureMode(p);
        } catch (Exception e) {
            return false;
        }
    }

    @PluginMethod
    public void pip(PluginCall call) {
        Activity a = getActivity();
        a.runOnUiThread(() -> {
            boolean ok = enterPip(a);
            JSObject o = new JSObject();
            o.put("ok", ok);
            call.resolve(o);
        });
    }

    /* ---------- screen ---------- */
    @PluginMethod
    public void keepAwake(PluginCall call) {
        boolean on = call.getBoolean("on", false);
        Activity a = getActivity();
        a.runOnUiThread(() -> {
            if (on) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        call.resolve();
    }

    @PluginMethod
    public void orientation(PluginCall call) {
        String m = call.getString("mode", "auto");
        Activity a = getActivity();
        a.runOnUiThread(() -> a.setRequestedOrientation(
            m.equals("landscape") ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        call.resolve();
    }

    @PluginMethod
    public void immersive(PluginCall call) {
        boolean on = call.getBoolean("on", false);
        Activity a = getActivity();
        a.runOnUiThread(() -> {
            WindowCompat.setDecorFitsSystemWindows(a.getWindow(), !on);
            WindowInsetsControllerCompat c = new WindowInsetsControllerCompat(a.getWindow(), a.getWindow().getDecorView());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (on) c.hide(WindowInsetsCompat.Type.systemBars());
            else c.show(WindowInsetsCompat.Type.systemBars());
        });
        call.resolve();
    }

    /* ---------- save file (recordings / settings export) in chunks ---------- */
    /** Opens an output stream in shared storage (Music/Ganesh Recordings for audio, Downloads otherwise). */
    private Object[] openOut(String name, String mime) throws Exception {
        boolean audio = mime.startsWith("audio/");
        Context ctx = getContext();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, audio ? Environment.DIRECTORY_MUSIC + "/Ganesh Recordings" : Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri base = audio ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                             : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri u = ctx.getContentResolver().insert(base, v);
            if (u == null) throw new Exception("insert failed");
            return new Object[]{ctx.getContentResolver().openOutputStream(u), u, null};
        }
        File dir = ctx.getExternalFilesDir(audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_DOWNLOADS);
        if (dir != null) dir.mkdirs();
        File f = new File(dir, name);
        return new Object[]{new FileOutputStream(f), null, f};
    }

    private String finish(Uri u) {
        if (u == null) return "";
        String path = "";
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContext().getContentResolver().update(u, v, null, null);
            try (Cursor c = getContext().getContentResolver().query(u, new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
                if (c != null && c.moveToFirst() && c.getString(0) != null) path = c.getString(0);
            }
        } catch (Exception ignored) { }
        return path;
    }

    @PluginMethod
    public void saveStart(PluginCall call) {
        String name = call.getString("name", "file");
        String mime = call.getString("mime", "application/octet-stream");
        try {
            String tok = UUID.randomUUID().toString();
            Object[] r = openOut(name, mime);
            outs.put(tok, (OutputStream) r[0]);
            if (r[1] != null) outUris.put(tok, (Uri) r[1]);
            if (r[2] != null) outFiles.put(tok, (File) r[2]);
            JSObject o = new JSObject();
            o.put("token", tok);
            call.resolve(o);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void saveChunk(PluginCall call) {
        try {
            OutputStream os = outs.get(call.getString("token"));
            if (os == null) { call.reject("no token"); return; }
            os.write(Base64.decode(call.getString("data", ""), Base64.DEFAULT));
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void saveEnd(PluginCall call) {
        String tok = call.getString("token");
        try {
            OutputStream os = outs.remove(tok);
            if (os != null) os.close();
            finish(outUris.remove(tok));
            outFiles.remove(tok);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /* ---------- native radio recorder (bypasses CORS, writes straight to Music/Ganesh Recordings) ---------- */
    private volatile boolean recStopFlag = false;
    private volatile boolean recBusy = false;

    private void recEmit(JSObject o) { notifyListeners("rec", o); }

    @PluginMethod
    public void recStart(PluginCall call) {
        if (recBusy) { call.reject("busy"); return; }
        final String urlS = call.getString("url", "");
        final String base = call.getString("name", "radio").replaceAll("[\\\\/:*?\"<>|]", "_");
        recStopFlag = false;
        recBusy = true;
        call.resolve();
        new Thread(() -> {
            HttpURLConnection con = null;
            OutputStream os = null;
            Uri uri = null;
            File file = null;
            long bytes = 0;
            String ext = "mp3", err = "";
            try {
                String u = urlS;
                for (int i = 0; i < 6; i++) {
                    con = (HttpURLConnection) new URL(u).openConnection();
                    con.setRequestProperty("Icy-MetaData", "0");
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) GaneshSangeet");
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(20000);
                    con.setInstanceFollowRedirects(false);
                    int rc = con.getResponseCode();
                    if (rc >= 300 && rc < 400 && con.getHeaderField("Location") != null) {
                        u = new URL(new URL(u), con.getHeaderField("Location")).toString();
                        con.disconnect();
                        continue;
                    }
                    if (rc >= 400) throw new Exception("HTTP " + rc);
                    break;
                }
                String ct = con.getContentType() == null ? "" : con.getContentType().toLowerCase();
                String mime = "audio/mpeg";
                if (ct.contains("aac") || ct.contains("mp4")) { ext = "aac"; mime = "audio/aac"; }
                else if (ct.contains("ogg") || ct.contains("opus")) { ext = "ogg"; mime = "audio/ogg"; }
                String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(new java.util.Date());
                Object[] r = openOut(base + "-" + stamp + "." + ext, mime);
                os = (OutputStream) r[0];
                uri = (Uri) r[1];
                file = (File) r[2];
                InputStream in = con.getInputStream();
                byte[] buf = new byte[16384];
                int n;
                long last = 0;
                while (!recStopFlag && (n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    bytes += n;
                    long now = System.currentTimeMillis();
                    if (now - last > 1000) {
                        last = now;
                        JSObject o = new JSObject();
                        o.put("bytes", bytes);
                        recEmit(o);
                    }
                }
            } catch (Exception e) {
                err = String.valueOf(e.getMessage());
            } finally {
                try { if (os != null) os.close(); } catch (Exception ignored) { }
                try { if (con != null) con.disconnect(); } catch (Exception ignored) { }
            }
            String path = file != null ? file.getAbsolutePath() : finish(uri);
            JSObject o = new JSObject();
            o.put("done", true);
            o.put("bytes", bytes);
            o.put("ext", ext);
            o.put("path", path);
            o.put("uri", uri == null ? "" : uri.toString());
            o.put("error", err);
            recBusy = false;
            recEmit(o);
        }).start();
    }

    @PluginMethod
    public void recStop(PluginCall call) {
        recStopFlag = true;
        call.resolve();
    }

    @PluginMethod
    public void deleteMedia(PluginCall call) {
        try {
            String u = call.getString("uri", "");
            String p = call.getString("path", "");
            if (!u.isEmpty()) getContext().getContentResolver().delete(Uri.parse(u), null, null);
            else if (!p.isEmpty()) new File(p).delete();
        } catch (Exception ignored) { }
        call.resolve();
    }

    /* ---------- alarms ---------- */
    @PluginMethod
    public void setAlarms(PluginCall call) {
        JSArray list = call.getArray("list", new JSArray());
        AlarmHelper.save(getContext(), list.toString());
        AlarmHelper.scheduleAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void takeLaunchAlarm(PluginCall call) {
        JSObject o = new JSObject();
        o.put("id", pendingAlarm == null ? "" : pendingAlarm);
        pendingAlarm = null;
        call.resolve(o);
    }
}
