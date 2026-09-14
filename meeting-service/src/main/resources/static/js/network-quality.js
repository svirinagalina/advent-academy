const QUALITY_STATES = Object.freeze({
    GOOD: "GOOD",
    FAIR: "FAIR",
    POOR: "POOR",
    CRITICAL: "CRITICAL",
    OFFLINE: "OFFLINE"
});

const STATE_ORDER = Object.freeze([
    QUALITY_STATES.GOOD,
    QUALITY_STATES.FAIR,
    QUALITY_STATES.POOR,
    QUALITY_STATES.CRITICAL,
    QUALITY_STATES.OFFLINE
]);

const DEFAULT_QUALITY_CONFIG = Object.freeze({
    sampleIntervalMs: 2000,
    windowSize: 5,
    worseningConfirmations: 2,
    recoveryConfirmations: 3,
    thresholds: Object.freeze({
        fair: Object.freeze({
            rttMs: 150,
            jitterMs: 30,
            packetLossPercent: 2,
            minimumAvailableOutgoingBitrateKbps: 900
        }),
        poor: Object.freeze({
            rttMs: 300,
            jitterMs: 50,
            packetLossPercent: 5,
            minimumAvailableOutgoingBitrateKbps: 400
        }),
        critical: Object.freeze({
            rttMs: 600,
            jitterMs: 100,
            packetLossPercent: 10,
            minimumAvailableOutgoingBitrateKbps: 150
        })
    })
});

class QualitySnapshot {
    constructor({
        timestamp,
        state,
        previousState,
        candidateState,
        changed,
        sampleCount,
        metrics
    }) {
        this.timestamp = timestamp;
        this.state = state;
        this.previousState = previousState;
        this.candidateState = candidateState;
        this.changed = changed;
        this.sampleCount = sampleCount;
        this.metrics = metrics;
        Object.freeze(this);
    }
}

class WebRtcStatsCollector {
    constructor({now = () => Date.now()} = {}) {
        this.now = now;
        this.previousCounters = new Map();
        this.previousMeasurementTimestamp = null;
    }

    async collect(peerConnection) {
        if (!peerConnection?.getStats) {
            throw new Error("RTCPeerConnection.getStats() is not supported");
        }

        const report = await peerConnection.getStats();
        const stats = statsValues(report);
        const measurementTimestamp = maximum(stats.map(stat => stat.timestamp)) ?? this.now();
        const elapsedSeconds = this.previousMeasurementTimestamp === null
            ? null
            : (measurementTimestamp - this.previousMeasurementTimestamp) / 1000;
        this.previousMeasurementTimestamp = measurementTimestamp;

        const selectedPair = findSelectedCandidatePair(stats);
        const localCandidate = selectedPair?.localCandidateId
            ? stats.find(stat => stat.id === selectedPair.localCandidateId)
            : null;
        const inboundRtp = stats.filter(stat => stat.type === "inbound-rtp" && !stat.isRemote);
        const outboundRtp = stats.filter(stat => stat.type === "outbound-rtp" && !stat.isRemote);
        const outboundById = new Map(outboundRtp.map(stat => [stat.id, stat]));
        const remoteInboundRtp = stats.filter(stat => stat.type === "remote-inbound-rtp");
        const outboundVideo = outboundRtp.filter(stat => mediaKind(stat) === "video");

        const inboundPacketDelta = sumDeltas(inboundRtp.map(stat => this.delta(stat, [
            "packetsReceived",
            "packetsLost"
        ], ["packetsLost"])));
        const outboundDeltas = new Map(outboundRtp.map(stat => [stat.id, this.delta(stat, [
            "bytesSent",
            "packetsSent"
        ])]));
        const outboundDelta = sumDeltas(Array.from(outboundDeltas.values()));
        const remotePacketDelta = sumDeltas(remoteInboundRtp.map(stat => ({
            packetsSent: outboundById.has(stat.localId)
                ? outboundDeltas.get(stat.localId)?.packetsSent
                : null,
            packetsLost: this.delta(stat, ["packetsLost"], ["packetsLost"]).packetsLost
        })));
        const videoDelta = sumDeltas(outboundVideo.map(stat => this.delta(stat, ["framesEncoded"])));
        const droppedDelta = sumDeltas(inboundRtp
            .filter(stat => mediaKind(stat) === "video")
            .map(stat => this.delta(stat, ["framesDropped"])));

        const inboundPacketLossPercent = receivedPacketLossPercent(
            inboundPacketDelta.packetsReceived,
            inboundPacketDelta.packetsLost
        );
        const outboundPacketLossPercent = sentPacketLossPercent(
            remotePacketDelta.packetsSent,
            remotePacketDelta.packetsLost
        ) ?? fractionLossPercent(remoteInboundRtp);
        const validElapsed = finite(elapsedSeconds) && elapsedSeconds > 0;

        return createMetrics({
            rttMs: secondsToMilliseconds(selectedPair?.currentRoundTripTime
                ?? maximum(stats
                    .filter(stat => stat.type === "remote-inbound-rtp")
                    .map(stat => stat.roundTripTime))),
            jitterMs: secondsToMilliseconds(maximum(inboundRtp.map(stat => stat.jitter))),
            inboundPacketLossPercent,
            outboundPacketLossPercent,
            packetLossPercent: maximum([inboundPacketLossPercent, outboundPacketLossPercent]),
            availableOutgoingBitrateKbps: bitsToKilobits(selectedPair?.availableOutgoingBitrate),
            outboundBitrateKbps: validElapsed && outboundDelta.bytesSent !== null
                ? outboundDelta.bytesSent * 8 / elapsedSeconds / 1000
                : null,
            framesPerSecond: maximum(outboundVideo.map(stat => stat.framesPerSecond))
                ?? (validElapsed && videoDelta.framesEncoded !== null
                    ? videoDelta.framesEncoded / elapsedSeconds
                    : null),
            droppedFrames: droppedDelta.framesDropped,
            iceState: peerConnection.iceConnectionState ?? "unknown",
            candidateType: allowedCandidateType(localCandidate?.candidateType)
        });
    }

    delta(stat, fields, signedFields = []) {
        const previous = this.previousCounters.get(stat.id) ?? {};
        const current = {};
        const result = {};
        const signed = new Set(signedFields);

        for (const field of fields) {
            const value = stat[field];
            if (!finite(value)) {
                result[field] = null;
                continue;
            }
            current[field] = value;
            if (!finite(previous[field])) {
                result[field] = null;
            } else {
                const difference = value - previous[field];
                result[field] = signed.has(field)
                    ? Math.max(0, difference)
                    : difference >= 0 ? difference : null;
            }
        }

        this.previousCounters.set(stat.id, {...previous, ...current});
        return result;
    }
}

class NetworkQualityEvaluator {
    constructor(config = {}) {
        this.config = mergeQualityConfig(config);
        this.samples = [];
        this.state = QUALITY_STATES.GOOD;
        this.pendingState = null;
        this.pendingConfirmations = 0;
    }

    evaluate(metrics, timestamp = Date.now()) {
        const safeMetrics = createMetrics(metrics);
        this.samples.push(safeMetrics);
        if (this.samples.length > this.config.windowSize) {
            this.samples.shift();
        }

        const windowMetrics = aggregateMetrics(this.samples);
        const candidateState = classifyQuality(windowMetrics, this.config.thresholds);
        const previousState = this.state;

        if (candidateState === this.state) {
            this.resetPending();
        } else {
            this.recordCandidate(candidateState);
            const worsening = severity(candidateState) > severity(this.state);
            const required = worsening
                ? this.config.worseningConfirmations
                : this.config.recoveryConfirmations;
            if (this.pendingConfirmations >= required) {
                this.state = worsening
                    ? candidateState
                    : nextRecoveryState(this.state, candidateState);
                this.resetPending();
            }
        }

        return new QualitySnapshot({
            timestamp,
            state: this.state,
            previousState,
            candidateState,
            changed: previousState !== this.state,
            sampleCount: this.samples.length,
            metrics: windowMetrics
        });
    }

    recordCandidate(candidateState) {
        if (candidateState === this.pendingState) {
            this.pendingConfirmations += 1;
            return;
        }
        this.pendingState = candidateState;
        this.pendingConfirmations = 1;
    }

    resetPending() {
        this.pendingState = null;
        this.pendingConfirmations = 0;
    }
}

class NetworkQualityMonitor {
    constructor({
        peerConnection,
        config = {},
        collector = new WebRtcStatsCollector(),
        evaluator = new NetworkQualityEvaluator(config),
        onSnapshot = () => {},
        onError = () => {},
        setIntervalImpl = globalThis.setInterval?.bind(globalThis),
        clearIntervalImpl = globalThis.clearInterval?.bind(globalThis)
    }) {
        this.peerConnection = peerConnection;
        this.config = mergeQualityConfig(config);
        this.collector = collector;
        this.evaluator = evaluator;
        this.onSnapshot = onSnapshot;
        this.onError = onError;
        this.setInterval = setIntervalImpl;
        this.clearInterval = clearIntervalImpl;
        this.timer = null;
        this.inFlight = false;
        this.running = false;
    }

    start() {
        if (this.running) {
            return;
        }
        this.running = true;
        void this.poll();
        this.timer = this.setInterval(() => void this.poll(), this.config.sampleIntervalMs);
    }

    stop() {
        this.running = false;
        if (this.timer !== null) {
            this.clearInterval(this.timer);
            this.timer = null;
        }
    }

    async poll() {
        if (this.inFlight) {
            return null;
        }
        this.inFlight = true;
        try {
            const metrics = await this.collector.collect(this.peerConnection);
            const snapshot = this.evaluator.evaluate(metrics);
            if (this.running) {
                this.onSnapshot(snapshot);
            }
            return snapshot;
        } catch (error) {
            if (this.running) {
                this.onError(error);
            }
            return null;
        } finally {
            this.inFlight = false;
        }
    }
}

function classifyQuality(metrics, thresholds = DEFAULT_QUALITY_CONFIG.thresholds) {
    if (["disconnected", "failed", "closed"].includes(metrics.iceState)) {
        return QUALITY_STATES.OFFLINE;
    }
    if (breaches(metrics, thresholds.critical)) {
        return QUALITY_STATES.CRITICAL;
    }
    if (breaches(metrics, thresholds.poor)) {
        return QUALITY_STATES.POOR;
    }
    if (breaches(metrics, thresholds.fair)) {
        return QUALITY_STATES.FAIR;
    }
    return QUALITY_STATES.GOOD;
}

function breaches(metrics, threshold) {
    return (finite(metrics.rttMs) && metrics.rttMs >= threshold.rttMs)
        || (finite(metrics.jitterMs) && metrics.jitterMs >= threshold.jitterMs)
        || (finite(metrics.packetLossPercent)
            && metrics.packetLossPercent >= threshold.packetLossPercent)
        || (finite(metrics.availableOutgoingBitrateKbps)
            && metrics.availableOutgoingBitrateKbps <= threshold.minimumAvailableOutgoingBitrateKbps);
}

function createMetrics(values = {}) {
    const inboundPacketLossPercent = percentageOrNull(values.inboundPacketLossPercent);
    const outboundPacketLossPercent = percentageOrNull(values.outboundPacketLossPercent);
    return Object.freeze({
        rttMs: numberOrNull(values.rttMs),
        jitterMs: numberOrNull(values.jitterMs),
        inboundPacketLossPercent,
        outboundPacketLossPercent,
        packetLossPercent: percentageOrNull(values.packetLossPercent)
            ?? maximum([inboundPacketLossPercent, outboundPacketLossPercent]),
        availableOutgoingBitrateKbps: numberOrNull(values.availableOutgoingBitrateKbps),
        outboundBitrateKbps: numberOrNull(values.outboundBitrateKbps),
        framesPerSecond: numberOrNull(values.framesPerSecond),
        droppedFrames: numberOrNull(values.droppedFrames),
        iceState: allowedIceState(values.iceState),
        candidateType: allowedCandidateType(values.candidateType)
    });
}

function aggregateMetrics(samples) {
    const latest = samples.at(-1) ?? createMetrics();
    return createMetrics({
        rttMs: average(samples.map(sample => sample.rttMs)),
        jitterMs: average(samples.map(sample => sample.jitterMs)),
        inboundPacketLossPercent: average(samples.map(sample => sample.inboundPacketLossPercent)),
        outboundPacketLossPercent: average(samples.map(sample => sample.outboundPacketLossPercent)),
        packetLossPercent: average(samples.map(sample => sample.packetLossPercent)),
        availableOutgoingBitrateKbps: average(samples
            .map(sample => sample.availableOutgoingBitrateKbps)),
        outboundBitrateKbps: average(samples.map(sample => sample.outboundBitrateKbps)),
        framesPerSecond: average(samples.map(sample => sample.framesPerSecond)),
        droppedFrames: sumAvailable(samples.map(sample => sample.droppedFrames)),
        iceState: latest.iceState,
        candidateType: latest.candidateType
    });
}

function mergeQualityConfig(config) {
    return {
        sampleIntervalMs: positiveInteger(config.sampleIntervalMs,
            DEFAULT_QUALITY_CONFIG.sampleIntervalMs),
        windowSize: positiveInteger(config.windowSize, DEFAULT_QUALITY_CONFIG.windowSize),
        worseningConfirmations: positiveInteger(config.worseningConfirmations,
            DEFAULT_QUALITY_CONFIG.worseningConfirmations),
        recoveryConfirmations: positiveInteger(config.recoveryConfirmations,
            DEFAULT_QUALITY_CONFIG.recoveryConfirmations),
        thresholds: {
            fair: {...DEFAULT_QUALITY_CONFIG.thresholds.fair, ...config.thresholds?.fair},
            poor: {...DEFAULT_QUALITY_CONFIG.thresholds.poor, ...config.thresholds?.poor},
            critical: {...DEFAULT_QUALITY_CONFIG.thresholds.critical, ...config.thresholds?.critical}
        }
    };
}

function nextRecoveryState(currentState, candidateState) {
    const currentIndex = severity(currentState);
    const candidateIndex = severity(candidateState);
    return STATE_ORDER[Math.max(candidateIndex, currentIndex - 1)];
}

function severity(state) {
    return STATE_ORDER.indexOf(state);
}

function statsValues(report) {
    if (typeof report?.values === "function") {
        return Array.from(report.values());
    }
    const values = [];
    report?.forEach?.(value => values.push(value));
    return values;
}

function findSelectedCandidatePair(stats) {
    const transport = stats.find(stat => stat.type === "transport" && stat.selectedCandidatePairId);
    const transportPair = transport
        ? stats.find(stat => stat.id === transport.selectedCandidatePairId)
        : null;
    return transportPair
        ?? stats.find(stat => stat.type === "candidate-pair"
            && stat.state === "succeeded" && stat.nominated)
        ?? null;
}

function sumDeltas(deltas) {
    const fields = new Set(deltas.flatMap(delta => Object.keys(delta)));
    return Object.fromEntries(Array.from(fields, field => {
        const values = deltas.map(delta => delta[field]);
        return [field, values.length === 0 || values.some(value => !finite(value))
            ? null
            : values.reduce((sum, value) => sum + value, 0)];
    }));
}

function mediaKind(stat) {
    return stat.kind ?? stat.mediaType;
}

function average(values) {
    const available = values.filter(finite);
    return available.length === 0
        ? null
        : available.reduce((sum, value) => sum + value, 0) / available.length;
}

function sumAvailable(values) {
    const available = values.filter(finite);
    return available.length === 0
        ? null
        : available.reduce((sum, value) => sum + value, 0);
}

function maximum(values) {
    const available = values.filter(finite);
    return available.length === 0 ? null : Math.max(...available);
}

function receivedPacketLossPercent(packetsReceived, packetsLost) {
    if (!finite(packetsReceived) || !finite(packetsLost)) {
        return null;
    }
    const expectedPackets = packetsReceived + packetsLost;
    return expectedPackets > 0 ? packetsLost / expectedPackets * 100 : null;
}

function sentPacketLossPercent(packetsSent, packetsLost) {
    return finite(packetsSent) && finite(packetsLost) && packetsSent > 0
        ? Math.min(100, packetsLost / packetsSent * 100)
        : null;
}

function fractionLossPercent(remoteInboundRtp) {
    const fractions = remoteInboundRtp
        .map(stat => stat.fractionLost)
        .filter(finite)
        .map(value => percentageOrNull(value * 100));
    return maximum(fractions);
}

function allowedIceState(value) {
    return ["new", "checking", "connected", "completed", "disconnected", "failed", "closed"]
        .includes(value) ? value : "unknown";
}

function allowedCandidateType(value) {
    return ["host", "srflx", "prflx", "relay"].includes(value) ? value : null;
}

function secondsToMilliseconds(value) {
    return finite(value) ? value * 1000 : null;
}

function bitsToKilobits(value) {
    return finite(value) ? value / 1000 : null;
}

function numberOrNull(value) {
    return finite(value) ? value : null;
}

function percentageOrNull(value) {
    return finite(value) ? Math.min(100, Math.max(0, value)) : null;
}

function finite(value) {
    return Number.isFinite(value);
}

function positiveInteger(value, fallback) {
    return Number.isInteger(value) && value > 0 ? value : fallback;
}

export {
    DEFAULT_QUALITY_CONFIG,
    NetworkQualityEvaluator,
    NetworkQualityMonitor,
    QualitySnapshot,
    QUALITY_STATES,
    WebRtcStatsCollector,
    classifyQuality,
    createMetrics
};
