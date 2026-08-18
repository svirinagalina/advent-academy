const DEFAULT_SPEAKING_THRESHOLD = 0.035;
const DEFAULT_SPEAKING_RELEASE_MS = 420;

export class VoiceActivityMonitor {
    constructor(dependencies = {}) {
        this.AudioContext = dependencies.AudioContextImpl
            ?? globalThis.AudioContext
            ?? globalThis.webkitAudioContext;
        this.requestAnimationFrame = dependencies.requestAnimationFrameImpl
            ?? globalThis.requestAnimationFrame?.bind(globalThis);
        this.cancelAnimationFrame = dependencies.cancelAnimationFrameImpl
            ?? globalThis.cancelAnimationFrame?.bind(globalThis);
        this.now = dependencies.nowImpl ?? (() => performance.now());
        this.onSpeakingChange = dependencies.onSpeakingChange ?? (() => {});
        this.threshold = dependencies.threshold ?? DEFAULT_SPEAKING_THRESHOLD;
        this.releaseMs = dependencies.releaseMs ?? DEFAULT_SPEAKING_RELEASE_MS;
        this.audioContext = null;
        this.sources = new Map();
        this.animationFrame = null;
    }

    async setStream(label, stream) {
        this.removeStream(label);
        const audioTrack = stream?.getAudioTracks?.()[0];
        if (!audioTrack || !this.AudioContext || !this.requestAnimationFrame) {
            this.onSpeakingChange(label, false, 0);
            return false;
        }

        this.audioContext ??= new this.AudioContext();
        if (this.audioContext.state === "suspended") {
            await this.audioContext.resume().catch(() => {});
        }
        const source = this.audioContext.createMediaStreamSource(stream);
        const analyser = this.audioContext.createAnalyser();
        analyser.fftSize = 512;
        analyser.smoothingTimeConstant = 0.45;
        source.connect(analyser);
        this.sources.set(label, {
            source,
            analyser,
            samples: new Uint8Array(analyser.fftSize),
            speaking: false,
            lastLoudAt: 0
        });
        this.schedule();
        return true;
    }

    removeStream(label) {
        const entry = this.sources.get(label);
        if (!entry) {
            return;
        }
        entry.source.disconnect();
        this.sources.delete(label);
        if (entry.speaking) {
            this.onSpeakingChange(label, false, 0);
        }
        if (this.sources.size === 0) {
            this.cancelScheduledFrame();
        }
    }

    sample() {
        const now = this.now();
        for (const [label, entry] of this.sources) {
            entry.analyser.getByteTimeDomainData(entry.samples);
            let energy = 0;
            for (const sample of entry.samples) {
                const centered = (sample - 128) / 128;
                energy += centered * centered;
            }
            const level = Math.sqrt(energy / entry.samples.length);
            if (level >= this.threshold) {
                entry.lastLoudAt = now;
                this.updateSpeaking(label, entry, true, level);
            } else if (entry.speaking && now - entry.lastLoudAt >= this.releaseMs) {
                this.updateSpeaking(label, entry, false, level);
            }
        }
        this.animationFrame = null;
        this.schedule();
    }

    updateSpeaking(label, entry, speaking, level) {
        if (entry.speaking === speaking) {
            return;
        }
        entry.speaking = speaking;
        this.onSpeakingChange(label, speaking, level);
    }

    schedule() {
        if (this.animationFrame === null && this.sources.size > 0 && this.requestAnimationFrame) {
            this.animationFrame = this.requestAnimationFrame(() => this.sample());
        }
    }

    cancelScheduledFrame() {
        if (this.animationFrame !== null) {
            this.cancelAnimationFrame?.(this.animationFrame);
            this.animationFrame = null;
        }
    }

    stop() {
        for (const label of [...this.sources.keys()]) {
            this.removeStream(label);
        }
        this.cancelScheduledFrame();
        if (this.audioContext) {
            this.audioContext.close().catch(() => {});
            this.audioContext = null;
        }
    }
}

export class BackgroundBlurProcessor {
    constructor(dependencies = {}) {
        this.SelfieSegmentation = dependencies.SelfieSegmentationImpl
            ?? globalThis.SelfieSegmentation;
        this.document = dependencies.documentImpl ?? globalThis.document;
        this.MediaStream = dependencies.MediaStreamImpl ?? globalThis.MediaStream;
        this.setTimeout = dependencies.setTimeoutImpl ?? globalThis.setTimeout.bind(globalThis);
        this.clearTimeout = dependencies.clearTimeoutImpl ?? globalThis.clearTimeout.bind(globalThis);
        this.onError = dependencies.onError ?? (() => {});
        this.frameRate = dependencies.frameRate ?? 24;
        this.blurPixels = dependencies.blurPixels ?? 18;
        this.segmenter = null;
        this.video = null;
        this.canvas = null;
        this.context = null;
        this.outputStream = null;
        this.outputTrack = null;
        this.frameTimer = null;
        this.running = false;
    }

    async start(sourceTrack) {
        if (!sourceTrack || sourceTrack.kind !== "video") {
            throw new Error("A camera video track is required for background blur");
        }
        if (!this.SelfieSegmentation || !this.document || !this.MediaStream) {
            throw new Error("Background blur is not supported by this browser");
        }

        this.video = this.document.createElement("video");
        this.video.muted = true;
        this.video.playsInline = true;
        this.video.srcObject = new this.MediaStream([sourceTrack]);
        await this.video.play();

        const settings = sourceTrack.getSettings?.() ?? {};
        this.canvas = this.document.createElement("canvas");
        this.canvas.width = settings.width ?? this.video.videoWidth ?? 640;
        this.canvas.height = settings.height ?? this.video.videoHeight ?? 480;
        this.context = this.canvas.getContext("2d", {alpha: false});
        if (!this.context || !this.canvas.captureStream) {
            throw new Error("Canvas video processing is not supported by this browser");
        }

        this.segmenter = new this.SelfieSegmentation({
            locateFile: file => `/vendor/mediapipe/${file}`
        });
        this.segmenter.setOptions({modelSelection: 1, selfieMode: false});
        this.segmenter.onResults(results => this.drawComposite(results));
        await this.segmenter.initialize();

        this.outputStream = this.canvas.captureStream(this.frameRate);
        this.outputTrack = this.outputStream.getVideoTracks()[0];
        if (!this.outputTrack) {
            throw new Error("The browser could not create a processed video track");
        }
        this.outputTrack.contentHint = "motion";
        this.running = true;
        this.scheduleFrame(0);
        return this.outputTrack;
    }

    drawComposite(results) {
        if (!this.context || !this.canvas) {
            return;
        }
        const {width, height} = this.canvas;
        this.context.save();
        this.context.clearRect(0, 0, width, height);
        this.context.filter = "none";
        this.context.globalCompositeOperation = "source-over";
        this.context.drawImage(results.segmentationMask, 0, 0, width, height);
        this.context.globalCompositeOperation = "source-in";
        this.context.drawImage(results.image, 0, 0, width, height);
        this.context.globalCompositeOperation = "destination-over";
        this.context.filter = `blur(${this.blurPixels}px)`;
        this.context.drawImage(results.image, 0, 0, width, height);
        this.context.restore();
    }

    scheduleFrame(delay = Math.round(1000 / this.frameRate)) {
        if (this.running) {
            this.frameTimer = this.setTimeout(() => this.processFrame(), delay);
        }
    }

    async processFrame() {
        this.frameTimer = null;
        if (!this.running) {
            return;
        }
        try {
            if (this.video.readyState >= 2) {
                await this.segmenter.send({image: this.video});
            }
        } catch (error) {
            this.onError(error?.message ?? "Background blur processing failed");
        } finally {
            this.scheduleFrame();
        }
    }

    stop() {
        this.running = false;
        if (this.frameTimer !== null) {
            this.clearTimeout(this.frameTimer);
            this.frameTimer = null;
        }
        this.outputTrack?.stop();
        this.outputTrack = null;
        this.outputStream = null;
        if (this.segmenter) {
            this.segmenter.close().catch(() => {});
            this.segmenter = null;
        }
        if (this.video) {
            this.video.pause();
            this.video.srcObject = null;
            this.video = null;
        }
        if (this.canvas) {
            this.canvas.width = 0;
            this.canvas.height = 0;
            this.canvas = null;
            this.context = null;
        }
    }
}

export {DEFAULT_SPEAKING_RELEASE_MS, DEFAULT_SPEAKING_THRESHOLD};
