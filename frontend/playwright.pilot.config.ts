import path from "node:path";
import { defineConfig } from "@playwright/test";
import dotenv from "dotenv";

const repositoryRoot = path.resolve(__dirname, "..");
const environmentFile = process.env.PILOT_UI_ENV_FILE
  ?? path.join(repositoryRoot, ".env.pilot-ui");
const evidenceDirectory = process.env.PILOT_UI_OUTPUT_DIR
  ?? path.join(repositoryRoot, "artifacts", "pilot-ui", "unscoped");

dotenv.config({ path: environmentFile, override: false, quiet: true });

const baseURL = process.env.PILOT_UI_BASE_URL;
if (!baseURL) {
  throw new Error("PILOT_UI_BASE_URL is required. Copy .env.pilot-ui.example first.");
}

export default defineConfig({
  testDir: "./e2e/pilot",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  outputDir: path.join(evidenceDirectory, "test-results"),
  reporter: [
    ["list"],
    ["html", { outputFolder: path.join(evidenceDirectory, "report"), open: "never" }],
    ["json", { outputFile: path.join(evidenceDirectory, "results.json") }],
  ],
  use: {
    baseURL,
    browserName: "chromium",
    colorScheme: "light",
    headless: process.env.PILOT_UI_HEADED !== "true",
    ignoreHTTPSErrors: false,
    locale: "en-IN",
    screenshot: "only-on-failure",
    timezoneId: "Asia/Kolkata",
    trace: "off",
    video: "off",
  },
  projects: [
    {
      name: "desktop-chromium",
      grep: /@desktop/,
      use: { viewport: { width: 1440, height: 1000 } },
    },
    {
      name: "zoom-200-layout",
      grep: /@zoom/,
      use: { viewport: { width: 720, height: 900 } },
    },
    {
      name: "android-360",
      grep: /@mobile/,
      use: {
        contextOptions: { screen: { width: 360, height: 800 } },
        deviceScaleFactor: 3,
        hasTouch: true,
        isMobile: true,
        userAgent: "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Mobile Safari/537.36",
        viewport: { width: 360, height: 800 },
      },
    },
    {
      name: "reduced-motion",
      grep: /@motion/,
      use: {
        contextOptions: { reducedMotion: "reduce" },
        viewport: { width: 1440, height: 1000 },
      },
    },
  ],
});
