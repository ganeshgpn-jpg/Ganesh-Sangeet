package np.ganesh.sangeet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Stores alarm specs and schedules the next occurrence of each with AlarmManager.setAlarmClock. */
public class AlarmHelper {
    static final String PREF = "sgs_alarms";

    static void save(Context c, String json) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("list", json).apply();
    }

    static String load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return p.getString("list", "[]");
    }

    static long next(String time, JSONArray days) {
        try {
            String[] hm = time.split(":");
            Calendar now = Calendar.getInstance();
            for (int add = 0; add < 8; add++) {
                Calendar t = (Calendar) now.clone();
                t.add(Calendar.DAY_OF_YEAR, add);
                t.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                t.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                t.set(Calendar.SECOND, 0);
                t.set(Calendar.MILLISECOND, 0);
                if (t.getTimeInMillis() <= now.getTimeInMillis()) continue;
                if (days == null || days.length() == 0) return t.getTimeInMillis();
                int dow = t.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday, same as JS getDay()
                for (int i = 0; i < days.length(); i++) if (days.getInt(i) == dow) return t.getTimeInMillis();
            }
        } catch (Exception ignored) { }
        return -1;
    }

    static int code(String id) { return id.hashCode() & 0x7fffffff; }

    static PendingIntent firePi(Context c, String id, String label) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("np.ganesh.sangeet.ALARM." + id);
        i.putExtra("id", id);
        i.putExtra("label", label);
        return PendingIntent.getBroadcast(c, code(id), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray arr = new JSONArray(load(c));
            for (int k = 0; k < arr.length(); k++) {
                JSONObject a = arr.getJSONObject(k);
                String id = a.getString("id");
                String label = a.optString("label", "Alarm");
                PendingIntent pi = firePi(c, id, label);
                am.cancel(pi);
                if (!a.optBoolean("on", true)) continue;
                long when = next(a.getString("time"), a.optJSONArray("days"));
                if (when < 0) continue;
                Intent show = new Intent(c, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(c, 7, show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showPi), pi);
            }
        } catch (Exception ignored) { }
    }

    /** After an alarm fires: one-shot alarms (no days) are switched off, repeating ones rescheduled. */
    static void afterFire(Context c, String id) {
        try {
            JSONArray arr = new JSONArray(load(c));
            for (int k = 0; k < arr.length(); k++) {
                JSONObject a = arr.getJSONObject(k);
                if (id.equals(a.optString("id"))) {
                    JSONArray d = a.optJSONArray("days");
                    if (d == null || d.length() == 0) a.put("on", false);
                }
            }
            save(c, arr.toString());
        } catch (Exception ignored) { }
        scheduleAll(c);
    }
}
