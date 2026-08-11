import {expect, test} from "@playwright/test";

test("two isolated browser contexts exchange live audio and video tracks", async ({browser}) => {
    const first = await openMeetingPage(browser);
    const second = await openMeetingPage(browser);
    try {
        await first.page.getByRole("button", {name: "Create room"}).click();
        await expect(first.page.locator("#connection-status"))
            .toHaveText("Waiting for another participant");
        const shareUrl = await first.page.locator("#share-url").inputValue();

        await second.page.goto(shareUrl);
        await second.page.getByRole("button", {name: "Join room"}).click();

        await expectConnectedWithRemoteMedia(first.page);
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
    const first = await openMeetingPage(browser);
    const second = await openMeetingPage(browser);
    const third = await openMeetingPage(browser);
    try {
        await first.page.getByRole("button", {name: "Create room"}).click();
        await expect(first.page.locator("#connection-status"))
            .toHaveText("Waiting for another participant");
        const shareUrl = await first.page.locator("#share-url").inputValue();

        await second.page.goto(shareUrl);
        await second.page.getByRole("button", {name: "Join room"}).click();
        await expectConnectedWithRemoteMedia(first.page);
        await expectConnectedWithRemoteMedia(second.page);

        await third.page.goto(shareUrl);
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

async function openMeetingPage(browser) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto("/");
    return {context, page};
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
