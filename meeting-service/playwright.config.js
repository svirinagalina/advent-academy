import {defineConfig} from "@playwright/test";

const baseURL = process.env.MEETING_E2E_BASE_URL ?? "http://127.0.0.1:8084";
const externalServer = process.env.MEETING_E2E_EXTERNAL_SERVER === "true";

export default defineConfig({
    testDir: "src/test/browser",
    outputDir: "build/playwright-results",
    fullyParallel: false,
    workers: 1,
    retries: 0,
    timeout: 45_000,
    expect: {
        timeout: 15_000
    },
    reporter: [
        ["list"],
        ["html", {outputFolder: "build/playwright-report", open: "never"}]
    ],
    use: {
        baseURL,
        channel: "chrome",
        headless: true,
        permissions: ["camera", "microphone"],
        launchOptions: {
            args: [
                "--use-fake-device-for-media-stream",
                "--use-fake-ui-for-media-stream",
                "--autoplay-policy=no-user-gesture-required"
            ]
        },
        trace: "retain-on-failure"
    },
    webServer: externalServer ? undefined : {
        command: "../gradlew :meeting-service:bootRun --no-daemon",
        cwd: ".",
        url: `${baseURL}/actuator/health`,
        reuseExistingServer: true,
        timeout: 120_000
    }
});
