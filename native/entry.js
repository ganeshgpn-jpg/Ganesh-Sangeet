// Bundled by esbuild -> www/native-bundle.js (runs before the app script)
import "@jofr/capacitor-media-session";   // polyfills navigator.mediaSession + foreground-service notification on Android
import { App } from "@capacitor/app";
window.CapApp = App;
