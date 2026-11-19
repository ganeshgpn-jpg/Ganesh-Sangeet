// Runs after `npx cap add android`: installs native plugin code, manifest entries and icons. Idempotent.
const fs = require("fs"), path = require("path");
const root = path.join(__dirname, "..");
const A = path.join(root, "android", "app", "src", "main");
if (!fs.existsSync(A)) { console.error("android/ not found - run `npx cap add android` first"); process.exit(1); }

// 1. Java sources
const jd = path.join(A, "java", "np", "ganesh", "sangeet");
fs.mkdirSync(jd, { recursive: true });
for (const f of fs.readdirSync(path.join(root, "native", "java"))) fs.copyFileSync(path.join(root, "native", "java", f), path.join(jd, f));

// 2. Manifest
const mp = path.join(A, "AndroidManifest.xml");
let m = fs.readFileSync(mp, "utf8");
if (!m.includes("AlarmReceiver")) {
  m = m.replace("<application", '<application android:usesCleartextTraffic="true"');
  m = m.replace("<activity", '<activity android:supportsPictureInPicture="true"');
  m = m.replace("</application>", `
        <receiver android:name=".AlarmReceiver" android:exported="false" />
        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    </application>`);
  const perms = [
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.WAKE_LOCK",
    "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO",
    "android.permission.POST_NOTIFICATIONS", "android.permission.USE_FULL_SCREEN_INTENT",
    "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.VIBRATE",
    "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
  ].filter(p => !m.includes(p)).map(p => `    <uses-permission android:name="${p}" />`).join("\n");
  const legacy = '    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />';
  m = m.replace("</manifest>", perms + "\n" + legacy + "\n</manifest>");
  fs.writeFileSync(mp, m);
}

// 3. Launcher icons (plain PNG icons; drop the adaptive-icon xml that points at the default Capacitor art)
const res = path.join(A, "res");
const any = path.join(res, "mipmap-anydpi-v26");
if (fs.existsSync(any)) fs.rmSync(any, { recursive: true, force: true });
const src = path.join(root, "native", "res");
for (const d of fs.readdirSync(src)) {
  fs.mkdirSync(path.join(res, d), { recursive: true });
  for (const f of fs.readdirSync(path.join(src, d))) fs.copyFileSync(path.join(src, d, f), path.join(res, d, f));
}
console.log("android patched");
