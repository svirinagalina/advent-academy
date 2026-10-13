import test from "node:test";
import assert from "node:assert/strict";

import {BackgroundBlurProcessor, VoiceActivityMonitor}
    from "../../main/resources/static/js/media-effects.js";

test("voice activity uses attack and release hysteresis for each stream", async () => {
    const frames = [];
    const changes = [];
    let now = 0;
    let sampleValue = 160;
    const analyser = {
        fftSize: 0,
        smoothingTimeConstant: 0,
        getByteTimeDomainData(samples) {
            samples.fill(sampleValue);
        }
    };
    const source = {connect() {}, disconnect() {}};
    class FakeAudioContext {
        constructor() {
            this.state = "running";
        }

        createMediaStreamSource() {
            return source;
        }

        createAnalyser() {
            return analyser;
        }

        async close() {}
    }
    const monitor = new VoiceActivityMonitor({
        AudioContextImpl: FakeAudioContext,
        requestAnimationFrameImpl: callback => {
            frames.push(callback);
            return frames.length;
        },
        cancelAnimationFrameImpl: () => {},
        nowImpl: () => now,
        releaseMs: 400,
        onSpeakingChange: (label, speaking) => changes.push({label, speaking})
    });
    const audioTrack = {kind: "audio"};
    const stream = {getAudioTracks: () => [audioTrack]};

    assert.equal(await monitor.setStream("local", stream), true);
    frames.shift()();
    assert.deepEqual(changes, [{label: "local", speaking: true}]);

    sampleValue = 128;
    now = 300;
    frames.shift()();
    assert.equal(changes.length, 1);
    now = 500;
    frames.shift()();
    assert.deepEqual(changes.at(-1), {label: "local", speaking: false});

    monitor.stop();
});

test("background blur produces a canvas track and releases processing resources", async () => {
    const scheduled = [];
    const operations = [];
    const processedTrack = {kind: "video", stopped: false, stop() { this.stopped = true; }};
    const context = {
        filter: "none",
        globalCompositeOperation: "source-over",
        save() { operations.push("save"); },
        restore() { operations.push("restore"); },
        clearRect() { operations.push("clear"); },
        drawImage(image) { operations.push({image, filter: this.filter, mode: this.globalCompositeOperation}); }
    };
    const video = {
        muted: false,
        playsInline: false,
        readyState: 2,
        videoWidth: 640,
        videoHeight: 480,
        async play() {},
        pause() {}
    };
    const canvas = {
        width: 0,
        height: 0,
        getContext: () => context,
        captureStream: () => ({getVideoTracks: () => [processedTrack]})
    };
    class FakeMediaStream {
        constructor(tracks) {
            this.tracks = tracks;
        }
    }
    class FakeSegmenter {
        constructor(configuration) {
            this.configuration = configuration;
            this.closed = false;
        }

        setOptions(options) {
            this.options = options;
        }

        onResults(callback) {
            this.callback = callback;
        }

        async initialize() {}

        async send() {
            this.callback({image: "camera", segmentationMask: "mask"});
        }

        async close() {
            this.closed = true;
        }
    }
    const processor = new BackgroundBlurProcessor({
        SelfieSegmentationImpl: FakeSegmenter,
        documentImpl: {createElement: tag => tag === "video" ? video : canvas},
        MediaStreamImpl: FakeMediaStream,
        setTimeoutImpl: callback => {
            scheduled.push(callback);
            return scheduled.length;
        },
        clearTimeoutImpl: () => {}
    });
    const cameraTrack = {
        kind: "video",
        getSettings: () => ({width: 1280, height: 720})
    };

    assert.equal(await processor.start(cameraTrack), processedTrack);
    assert.equal(canvas.width, 1280);
    assert.equal(canvas.height, 720);
    assert.deepEqual(processor.segmenter.options, {modelSelection: 1, selfieMode: false});
    await scheduled.shift()();
    assert.equal(operations.some(operation => operation.image === "mask"), true);
    assert.equal(operations.some(operation => operation.filter === "blur(18px)"), true);

    processor.stop();
    assert.equal(processedTrack.stopped, true);
    assert.equal(processor.running, false);
});
