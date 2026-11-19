# श्री गणेश संगीत यन्त्र — Android APK (GitHub Actions)

## APK बनाउने तरिका
1. GitHub मा नयाँ repository बनाउनुहोस् (Private भए पनि हुन्छ)।
2. यो फोल्डरका सबै फाइल (`.github`, `native`, `player`, `scripts`, `signing`, `package.json`, `capacitor.config.json`, `.gitignore`) repository मा upload गर्नुहोस्। `.github` लुकेको फोल्डर हो — छुटेको छैन भनी जाँच्नुहोस्।
3. **Actions** ट्याबमा **Build APK** चल्छ (नचले *Run workflow*)। ५–10 मिनेट लाग्छ।
4. सकिएपछि run खोल्नुहोस् → तल **Artifacts** → `ganesh-sangeet-apk` डाउनलोड → zip खोलेर `ganesh-sangeet.apk` फोनमा install गर्नुहोस् ("अज्ञात स्रोतबाट install" अनुमति चाहिन्छ)।

## HTML अपडेट गर्न
`player/index.html` बदल्नुहोस् र push गर्नुहोस् — APK आफैं फेरि बन्छ। `signing/debug.keystore` उही रहने भएकाले नयाँ APK पुरानो माथि update हुन्छ (सेटिङ/Favorites जोगिन्छन्)।

## फाइलहरू
- `player/index.html` — मुख्य प्लेयर (browser र APK दुवैमा चल्ने)
- `native/native-bridge.js` — APK भित्र मात्र सक्रिय हुने bridge
- `native/java/*` — Android नेटिभ कोड (लाइब्रेरी स्क्यान, PiP, alarm, रेकर्डर, फुलस्क्रिन)
- `scripts/patch-android.js` — Manifest/permission/आइकन थप्छ
- `.github/workflows/build-apk.yml` — APK बनाउने workflow
