// Builds www/ from the single-file player: copies index.html, injects native bridge scripts.
const fs = require("fs"), path = require("path");
const src = path.join(__dirname, "..", "player", "index.html");
const www = path.join(__dirname, "..", "www");
fs.mkdirSync(www, { recursive: true });
let h = fs.readFileSync(src, "utf8");
const inj = '<script src="native-bundle.js"></script>\n<script src="native-bridge.js"></script>\n';
if (!h.includes("native-bridge.js")) h = h.replace("<script>", inj + "<script>");
fs.writeFileSync(path.join(www, "index.html"), h);
fs.copyFileSync(path.join(__dirname, "..", "native", "native-bridge.js"), path.join(www, "native-bridge.js"));
console.log("www ready:", fs.readdirSync(www).join(", "));
