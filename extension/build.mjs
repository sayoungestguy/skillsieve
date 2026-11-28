// Copies the static files next to the compiled JS so dist/ loads as an unpacked extension.
import { cpSync, mkdirSync } from "node:fs";

mkdirSync("dist", { recursive: true });
cpSync("manifest.json", "dist/manifest.json");
cpSync("static", "dist", { recursive: true });
cpSync("icons", "dist/icons", { recursive: true });
console.log("Built dist/. Load it at chrome://extensions → Load unpacked.");
