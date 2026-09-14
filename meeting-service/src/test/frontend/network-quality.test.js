import test from "node:test";
import assert from "node:assert/strict";

import {
    DEFAULT_QUALITY_CONFIG,
    NetworkQualityEvaluator,
    NetworkQualityMonitor,
    QualitySnapshot,
    QUALITY_STATES,
    WebRtcStatsCollector,
    classifyQuality,
    createMetrics
} from "../../main/resources/static/js/network-quality.js";

function statsReport(values) {
    return new Map(values.map(value => [value.id, value]));
}

function peerConnection(reports) {
    let index = 0;
    return {
        iceConnectionState: "connected",
        async getStats() {
            const report = reports[Math.min(index, reports.length - 1)];
            index += 1;
            return report;
        }
    };
}

function report({
    timestamp,
    packetsReceived,
    packetsLost,
    bytesSent,
    framesEncoded,
    framesPerSecond,
    framesDropped,
    packetsSent,
    remotePacketsLost,
    fractionLost,
    availableOutgoingBitrate = 1_200_000
}) {
    return statsReport([
        {
            id: "transport",
            type: "transport",
            timestamp,
            selectedCandidatePairId: "pair"
        },
        {
            id: "pair",
            type: "candidate-pair",
            timestamp,
            state: "succeeded",
            nominated: true,
            localCandidateId: "local",
            currentRoundTripTime: 0.12,
            availableOutgoingBitrate
        },
        {id: "local", type: "local-candidate", timestamp, candidateType: "relay", address: "192.0.2.1"},
        {
            id: "inbound-video",
            type: "inbound-rtp",
            kind: "video",
            timestamp,
            packetsReceived,
            packetsLost,
            jitter: 0.004,
            framesDropped
        },
        {
            id: "outbound-audio",
            type: "outbound-rtp",
            kind: "audio",
            timestamp,
            bytesSent: bytesSent.audio
        },
        {
            id: "outbound-video",
            type: "outbound-rtp",
            kind: "video",
            timestamp,
            bytesSent: bytesSent.video,
            ...(packetsSent === undefined ? {} : {packetsSent}),
            framesEncoded,
            ...(framesPerSecond === undefined ? {} : {framesPerSecond})
        },
        ...(remotePacketsLost === undefined && fractionLost === undefined ? [] : [{
            id: "remote-inbound-video",
            type: "remote-inbound-rtp",
            kind: "video",
            timestamp,
            localId: "outbound-video",
            ...(remotePacketsLost === undefined ? {} : {packetsLost: remotePacketsLost}),
            ...(fractionLost === undefined ? {} : {fractionLost})
        }])
    ]);
}

function qualityMetrics(values = {}) {
    return createMetrics({
        rttMs: 40,
        jitterMs: 5,
        packetLossPercent: 0.5,
        availableOutgoingBitrateKbps: 1500,
        outboundBitrateKbps: 900,
        framesPerSecond: 30,
        droppedFrames: 0,
        iceState: "connected",
        candidateType: "relay",
        ...values
    });
}

test("normalizes WebRTC stats and calculates bitrate and loss from counter deltas", async () => {
    const connection = peerConnection([
        report({
            timestamp: 1000,
            packetsReceived: 100,
            packetsLost: 5,
            bytesSent: {audio: 1000, video: 2000},
            packetsSent: 100,
            remotePacketsLost: 5,
            framesEncoded: 50,
            framesPerSecond: 25,
            framesDropped: 2
        }),
        report({
            timestamp: 3000,
            packetsReceived: 190,
            packetsLost: 10,
            bytesSent: {audio: 3000, video: 5000},
            packetsSent: 200,
            remotePacketsLost: 15,
            framesEncoded: 90,
            framesDropped: 5
        })
    ]);
    const collector = new WebRtcStatsCollector();

    const first = await collector.collect(connection);
    const second = await collector.collect(connection);

    assert.equal(first.rttMs, 120);
    assert.equal(first.jitterMs, 4);
    assert.equal(first.availableOutgoingBitrateKbps, 1200);
    assert.equal(first.candidateType, "relay");
    assert.equal(first.packetLossPercent, null);
    assert.equal(first.outboundBitrateKbps, null);
    assert.equal(first.framesPerSecond, 25);
    assert.equal(second.inboundPacketLossPercent, 5 / 95 * 100);
    assert.equal(second.outboundPacketLossPercent, 10);
    assert.equal(second.packetLossPercent, 10);
    assert.equal(second.outboundBitrateKbps, 20);
    assert.equal(second.framesPerSecond, 20);
    assert.equal(second.droppedFrames, 3);
});

test("handles signed packetsLost counters without mistaking them for a reset", async () => {
    const connection = peerConnection([
        report({
            timestamp: 1000,
            packetsReceived: 100,
            packetsLost: 0,
            bytesSent: {audio: 1000, video: 2000},
            packetsSent: 100,
            remotePacketsLost: 4,
            framesEncoded: 50,
            framesDropped: 2
        }),
        report({
            timestamp: 3000,
            packetsReceived: 120,
            packetsLost: -2,
            bytesSent: {audio: 2000, video: 4000},
            packetsSent: 125,
            remotePacketsLost: 2,
            framesEncoded: 80,
            framesDropped: 3
        })
    ]);
    const collector = new WebRtcStatsCollector();

    await collector.collect(connection);
    const metrics = await collector.collect(connection);

    assert.equal(metrics.inboundPacketLossPercent, 0);
    assert.equal(metrics.outboundPacketLossPercent, 0);
    assert.equal(metrics.packetLossPercent, 0);
});

test("uses remote fractionLost when outbound delta counters are unavailable", async () => {
    const connection = peerConnection([report({
        timestamp: 1000,
        packetsReceived: 100,
        packetsLost: 1,
        bytesSent: {audio: 1000, video: 2000},
        fractionLost: 0.075,
        framesEncoded: 50,
        framesDropped: 2
    })]);

    const metrics = await new WebRtcStatsCollector().collect(connection);

    assert.equal(metrics.outboundPacketLossPercent, 7.5);
    assert.equal(metrics.packetLossPercent, 7.5);
});

test("handles missing browser fields and reset counters without fabricated deltas", async () => {
    const connection = peerConnection([
        report({
            timestamp: 1000,
            packetsReceived: 100,
            packetsLost: 5,
            bytesSent: {audio: 1000, video: 2000},
            framesEncoded: 50,
            framesDropped: 2
        }),
        report({
            timestamp: 3000,
            packetsReceived: 10,
            packetsLost: 1,
            bytesSent: {audio: 100, video: 200},
            framesEncoded: 5,
            framesDropped: 0
        }),
        statsReport([])
    ]);
    const collector = new WebRtcStatsCollector({now: () => 5000});

    await collector.collect(connection);
    const reset = await collector.collect(connection);
    const missing = await collector.collect(connection);

    assert.equal(reset.packetLossPercent, null);
    assert.equal(reset.outboundBitrateKbps, null);
    assert.equal(reset.framesPerSecond, null);
    assert.equal(reset.droppedFrames, null);
    assert.deepEqual(missing, createMetrics({iceState: "connected"}));
});

test("classifies configurable boundaries for every quality state", () => {
    assert.equal(classifyQuality(qualityMetrics()), QUALITY_STATES.GOOD);
    assert.equal(classifyQuality(qualityMetrics({rttMs: 150})), QUALITY_STATES.FAIR);
    assert.equal(classifyQuality(qualityMetrics({packetLossPercent: 5})), QUALITY_STATES.POOR);
    assert.equal(classifyQuality(qualityMetrics({jitterMs: 100})), QUALITY_STATES.CRITICAL);
    assert.equal(classifyQuality(qualityMetrics({iceState: "failed"})), QUALITY_STATES.OFFLINE);

    const custom = {
        ...DEFAULT_QUALITY_CONFIG.thresholds,
        fair: {...DEFAULT_QUALITY_CONFIG.thresholds.fair, rttMs: 200}
    };
    assert.equal(classifyQuality(qualityMetrics({rttMs: 175}), custom), QUALITY_STATES.GOOD);
});

test("requires two worsening confirmations and recovers one level per three confirmations", () => {
    const evaluator = new NetworkQualityEvaluator({windowSize: 1});
    const poor = qualityMetrics({packetLossPercent: 6});
    const good = qualityMetrics();

    assert.equal(evaluator.evaluate(poor, 1).state, QUALITY_STATES.GOOD);
    assert.equal(evaluator.evaluate(poor, 2).state, QUALITY_STATES.POOR);

    assert.equal(evaluator.evaluate(good, 3).state, QUALITY_STATES.POOR);
    assert.equal(evaluator.evaluate(good, 4).state, QUALITY_STATES.POOR);
    const firstRecovery = evaluator.evaluate(good, 5);
    assert.equal(firstRecovery.state, QUALITY_STATES.FAIR);
    assert.equal(firstRecovery.changed, true);

    evaluator.evaluate(good, 6);
    evaluator.evaluate(good, 7);
    assert.equal(evaluator.evaluate(good, 8).state, QUALITY_STATES.GOOD);
});

test("sliding window is bounded and alternating boundary samples do not oscillate", () => {
    const evaluator = new NetworkQualityEvaluator({windowSize: 3});
    const fair = qualityMetrics({rttMs: 160});
    const good = qualityMetrics({rttMs: 140});
    const states = [];

    for (let index = 0; index < 10; index += 1) {
        const snapshot = evaluator.evaluate(index % 2 === 0 ? fair : good, index);
        states.push(snapshot.state);
        assert.ok(snapshot.sampleCount <= 3);
    }

    const transitions = states.filter((state, index) => index > 0 && state !== states[index - 1]);
    assert.ok(transitions.length <= 1);
    assert.equal(states.includes(QUALITY_STATES.POOR), false);
});

test("polling uses the configured interval and never overlaps getStats calls", async () => {
    let resolveCollection;
    let collectionCount = 0;
    let intervalMs;
    let intervalCallback;
    let clearedTimer;
    const snapshots = [];
    const collector = {
        collect() {
            collectionCount += 1;
            return new Promise(resolve => {
                resolveCollection = resolve;
            });
        }
    };
    const monitor = new NetworkQualityMonitor({
        peerConnection: {},
        config: {sampleIntervalMs: 2345, windowSize: 1},
        collector,
        onSnapshot: snapshot => snapshots.push(snapshot),
        setIntervalImpl: (callback, milliseconds) => {
            intervalCallback = callback;
            intervalMs = milliseconds;
            return 17;
        },
        clearIntervalImpl: timer => {
            clearedTimer = timer;
        }
    });

    monitor.start();
    intervalCallback();
    assert.equal(await monitor.poll(), null);
    assert.equal(collectionCount, 1);
    assert.equal(intervalMs, 2345);

    resolveCollection(qualityMetrics());
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(snapshots.length, 1);
    assert.equal(snapshots[0] instanceof QualitySnapshot, true);
    assert.deepEqual(Object.keys(snapshots[0]).sort(), [
        "candidateState",
        "changed",
        "metrics",
        "previousState",
        "sampleCount",
        "state",
        "timestamp"
    ]);
    assert.doesNotMatch(JSON.stringify(snapshots[0]), /address|ipAddress|roomId|sdp|token|userId/i);

    monitor.stop();
    assert.equal(clearedTimer, 17);
});
