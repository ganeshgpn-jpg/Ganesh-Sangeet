/* Native (Capacitor / Android) bridge. Defines window.NAT only inside the APK; in a normal browser nothing happens. */
(function () {
  const C = window.Capacitor;
  if (!C || !C.isNativePlatform || !C.isNativePlatform()) return;
  const P = C.registerPlugin("SangeetNative");
  const rawFetch = window.CapacitorWebFetch || window.fetch.bind(window);
  const NAT = (window.NAT = { ok: true, P });

  NAT.url = path => C.convertFileSrc("file://" + path.split("/").map(encodeURIComponent).join("/"));

  /* file-like object that reads byte ranges from the local server (for ID3 / cover-art parsing) */
  NAT.file = (url, size, name) => ({
    name, size, type: "",
    slice(a, b) {
      const e = b === undefined ? size : b;
      return { arrayBuffer: async () => (await rawFetch(url, { headers: { Range: "bytes=" + a + "-" + (e - 1) } })).arrayBuffer() };
    },
    text: async () => (await rawFetch(url)).text()
  });

  NAT.perm = async () => { try { return (await P.requestLibrary()).granted; } catch (e) { return false; } };

  /* fill lib.a / lib.v from MediaStore. returns false if permission is missing */
  NAT.fill = async out => {
    let r;
    try { r = await P.scan(); } catch (e) { return false; }
    for (const f of r.files) {
      const parts = f.p.split("/"); parts.pop();
      let fo;
      if (f.p.startsWith("/storage/emulated/")) fo = parts.slice(4);
      else if (f.p.startsWith("/storage/")) fo = ["SD"].concat(parts.slice(3));
      else fo = parts.slice(1);
      if (fo.some(x => x[0] === ".")) continue;
      if (!fo.length) fo = ["Phone"];
      const url = NAT.url(f.p);
      out[f.k].push({
        name: clean(f.n), ext: extOf(f.n) || (f.k === "v" ? "mp4" : "mp3"), folder: fo,
        key: f.p, src: "nat", url, size: f.s,
        handle: { getFile: async () => NAT.file(url, f.s, f.n) }
      });
    }
    return true;
  };

  /* save a Blob into shared storage (Download / Music) in 512 KB chunks */
  NAT.saveBlob = async (blob, name, mime) => {
    const { token } = await P.saveStart({ name, mime });
    const CH = 512 * 1024;
    for (let o = 0; o < blob.size; o += CH) {
      const buf = new Uint8Array(await blob.slice(o, o + CH).arrayBuffer());
      let s = ""; for (let i = 0; i < buf.length; i += 8192) s += String.fromCharCode.apply(null, buf.subarray(i, i + 8192));
      await P.saveChunk({ token, data: btoa(s) });
    }
    await P.saveEnd({ token });
  };

  /* native radio recorder */
  NAT.recStart = async (url, name, cb) => {
    if (NAT._rl) NAT._rl.remove();
    NAT._rl = await P.addListener("rec", cb);
    await P.recStart({ url, name });
  };
  NAT.recStop = () => P.recStop();

  /* PiP / fullscreen / orientation */
  NAT.pip = async () => { try { return (await P.pip()).ok; } catch (e) { return false; } };
  P.addListener("pip", e => document.getElementById("vs").classList.toggle("pipm", !!e.on));

  /* alarms: keep the native AlarmManager copy in sync with CFG.al */
  let sig = "";
  const syncAlarms = () => {
    if (typeof CFG === "undefined") return;
    const list = CFG.al.map(a => ({ id: a.id, time: a.time, days: a.days, on: !!a.on, label: a.label || a.name || "Alarm" }));
    const s = JSON.stringify(list);
    if (s === sig) return;
    sig = s;
    if (list.some(a => a.on)) P.requestNotifications().catch(() => {});
    P.setAlarms({ list }).catch(() => {});
  };
  setInterval(syncAlarms, 3000);
  setTimeout(syncAlarms, 800);

  /* app launched / brought forward by an alarm */
  const launchAlarm = async () => {
    try { const r = await P.takeLaunchAlarm(); if (r.id && window.nativeAlarm) window.nativeAlarm(r.id); } catch (e) {}
  };
  P.addListener("alarm", () => launchAlarm());
  setTimeout(launchAlarm, 1200);
  if (window.CapApp) {
    window.CapApp.addListener("appStateChange", s => { if (s.isActive) launchAlarm(); });
    window.CapApp.addListener("backButton", () => { if (window.nativeBack) window.nativeBack(); });
  }
})();
