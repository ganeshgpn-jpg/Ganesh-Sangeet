package np.ganesh.sangeet;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SangeetPlugin.class);
        super.onCreate(savedInstanceState);
        try {
            WebSettings s = getBridge().getWebView().getSettings();
            s.setMediaPlaybackRequiresUserGesture(false); // auto-next, alarms and schedules may start audio without a tap
            s.setAllowFileAccess(true);
        } catch (Exception ignored) { }
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent in) {
        if (in == null) return;
        String id = in.getStringExtra("alarm");
        if (id != null) {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            }
            SangeetPlugin.emitAlarm(id);
            in.removeExtra("alarm");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // keep the WebView (audio / timers) running when the app is in background or in PiP
        try {
            getBridge().getWebView().onResume();
            getBridge().getWebView().resumeTimers();
        } catch (Exception ignored) { }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (SangeetPlugin.videoActive) SangeetPlugin.enterPip(this);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPip, Configuration cfg) {
        super.onPictureInPictureModeChanged(isInPip, cfg);
        SangeetPlugin.emitPip(isInPip);
    }
}
