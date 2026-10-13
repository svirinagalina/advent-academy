import {expect, test} from "@playwright/test";
import {createHmac} from "node:crypto";

const HOST_ID = "00000000-0000-0000-0000-000000000001";
const SECOND_ID = "00000000-0000-0000-0000-000000000002";
const THIRD_ID = "00000000-0000-0000-0000-000000000003";
const JWT_SECRET = "test-secret-key-for-testing-purposes-only-32-bytes";

test("two isolated browser contexts exchange live audio and video tracks", async ({browser}) => {
    const first = await openMeetingPage(browser, HOST_ID);
    const second = await openMeetingPage(browser, SECOND_ID);
    try {
        await first.page.getByRole("button", {name: "Create room"}).click();
        await expect(first.page.locator("#connection-status"))
            .toHaveText("Waiting for another participant");
        const shareUrl = await first.page.locator("#share-url").inputValue();
        const roomId = await first.page.locator("#room-id").inputValue();
        await grantMember(first.page, roomId, SECOND_ID, first.token);

        await second.page.goto(shareUrl);
        await setToken(second.page, second.token);
        await second.page.getByRole("button", {name: "Join room"}).click();

        await expectConnectedWithRemoteMedia(first.page);
        await expectConnectedWithRemoteMedia(second.page);
        await expectRealNetworkQualitySnapshot(first.page);
        await expectRealNetworkQualitySnapshot(second.page);

        await expect.poll(async () => first.page.locator(".video-frame.speaking").count())
            .toBeGreaterThan(0);

        await first.page.getByRole("button", {name: "Noise suppression: On"}).click();
        await expect(first.page.getByRole("button", {name: "Noise suppression: Off"})).toBeVisible();

        await first.page.getByRole("button", {name: "Background blur: Off"}).click();
        await expect(first.page.getByRole("button", {name: "Background blur: On"})).toBeVisible();
        await expectConnectedWithRemoteMedia(second.page);

        await first.page.getByRole("button", {name: "Mute microphone"}).click();
        await expect(first.page.getByRole("button", {name: "Unmute microphone"})).toBeVisible();
        await second.page.getByRole("button", {name: "Disable camera"}).click();
        await expect(second.page.getByRole("button", {name: "Enable camera"})).toBeVisible();
    } finally {
        await second.context.close();
        await first.context.close();
    }
});

test("third participant is rejected and refresh releases the occupied slot", async ({browser}) => {
    const first = await openMeetingPage(browser, HOST_ID);
    const second = await openMeetingPage(browser, SECOND_ID);
    const third = await openMeetingPage(browser, THIRD_ID);
    try {
        await first.page.getByRole("button", {name: "Create room"}).click();
        await expect(first.page.locator("#connection-status"))
            .toHaveText("Waiting for another participant");
        const shareUrl = await first.page.locator("#share-url").inputValue();
        const roomId = await first.page.locator("#room-id").inputValue();
        await grantMember(first.page, roomId, SECOND_ID, first.token);
        await grantMember(first.page, roomId, THIRD_ID, first.token);

        await second.page.goto(shareUrl);
        await setToken(second.page, second.token);
        await second.page.getByRole("button", {name: "Join room"}).click();
        await expectConnectedWithRemoteMedia(first.page);
        await expectConnectedWithRemoteMedia(second.page);

        await third.page.goto(shareUrl);
        await setToken(third.page, third.token);
        await third.page.getByRole("button", {name: "Join room"}).click();
        await expect(third.page.locator("#connection-status")).toHaveText("Failed");
        await expect(third.page.locator("#error-message"))
            .toHaveText("The room already contains two participants");
        await expect(third.page.getByRole("button", {name: "Join room"})).toBeEnabled();

        await second.page.reload();
        await expect(first.page.locator("#connection-status")).toHaveText("Participant left");

        await third.page.getByRole("button", {name: "Join room"}).click();
        await expectConnectedWithRemoteMedia(first.page);
        await expectConnectedWithRemoteMedia(third.page);
    } finally {
        await third.context.close();
        await second.context.close();
        await first.context.close();
    }
});

async function openMeetingPage(browser, userId) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto("/");
    const token = accessToken(userId);
    await setToken(page, token);
    return {context, page, token};
}

async function setToken(page, token) {
    await page.locator("#access-token").fill(token);
}

async function grantMember(page, roomId, userId, token) {
    const response = await page.request.put(`/api/meetings/rooms/${roomId}/members/${userId}`, {
        headers: {Authorization: `Bearer ${token}`}
    });
    expect(response.ok()).toBe(true);
}

function accessToken(userId) {
    const now = Math.floor(Date.now() / 1000);
    const header = base64Url(JSON.stringify({alg: "HS256", typ: "JWT"}));
    const payload = base64Url(JSON.stringify({
        sub: userId,
        iss: "advent-academy-user-service",
        aud: ["advent-academy-services"],
        iat: now,
        exp: now + 300,
        jti: `${userId}-${now}`,
        token_type: "access"
    }));
    const signature = createHmac("sha256", JWT_SECRET)
        .update(`${header}.${payload}`)
        .digest("base64url");
    return `${header}.${payload}.${signature}`;
}

function base64Url(value) {
    return Buffer.from(value).toString("base64url");
}

async function expectConnectedWithRemoteMedia(page) {
    await expect(page.locator("#connection-status")).toHaveText("Connected");
    await expect.poll(() => page.locator("#remote-video").evaluate(video => {
        const stream = video.srcObject;
        if (!stream) {
            return false;
        }
        const audioTracks = stream.getAudioTracks();
        const videoTracks = stream.getVideoTracks();
        return audioTracks.length === 1
            && videoTracks.length === 1
            && audioTracks[0].readyState === "live"
            && videoTracks[0].readyState === "live"
            && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA;
    })).toBe(true);
}

async function expectRealNetworkQualitySnapshot(page) {
    await expect.poll(() => page.locator("#connection-status").evaluate(status =>
        Number(status.dataset.qualitySamples ?? 0)
    ), {timeout: 10_000}).toBeGreaterThan(0);
    await expect(page.locator("#connection-status")).toHaveAttribute(
        "data-network-quality",
        /^(good|fair|poor|critical|offline)$/
    );
}
