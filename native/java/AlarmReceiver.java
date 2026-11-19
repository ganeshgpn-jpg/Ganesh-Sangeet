package np.ganesh.sangeet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    static final String CH = "sgs_alarm";

    @Override
    public void onReceive(Context c, Intent in) {
        String id = in.getStringExtra("id");
        String label = in.getStringExtra("label");
        if (id == null) return;
        if (label == null || label.isEmpty()) label = "Alarm";

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Alarm", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("alarm", id);
        PendingIntent pi = PendingIntent.getActivity(c, AlarmHelper.code(id), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Ganesh Sangeet · खोल्न थिच्नुहोस्")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true);
        nm.notify(AlarmHelper.code(id), b.build());

        try { c.startActivity(open); } catch (Exception ignored) { } // works when allowed by the OS; otherwise the notification handles it

        AlarmHelper.afterFire(c, id);
    }
}
