import { api } from "@/lib/learning";
export type Meeting = {
	id: string;
	courseId: string;
	courseTitle: string;
	hostId: string;
	title: string;
	startsAt: string;
	endsAt: string;
	status: string;
	version: number;
	participantCount: number;
	participants: string[];
	canManage: boolean;
	canStart: boolean;
	canJoin: boolean;
};
export type MeetingPage = {
	items: Meeting[];
	total: number;
	page: number;
	size: number;
};
export type CallView = {
	local: MediaStream | null;
	peers: { id: string; stream: MediaStream }[];
	status: string;
	connected: boolean;
};
type Signal = {
	type: string;
	senderId?: string;
	payload?: {
		participantId?: string;
		peers?: { participantId: string; initiator: boolean }[];
		initiator?: boolean;
		sdp?: string;
		candidate?: string;
		sdpMid?: string | null;
		sdpMLineIndex?: number | null;
		code?: string;
	};
};
export class GroupCall {
	private socket: WebSocket | null = null;
	private local: MediaStream | null = null;
	private peers = new Map<string, RTCPeerConnection>();
	private remote = new Map<string, MediaStream>();
	private candidates = new Map<string, RTCIceCandidateInit[]>();
	private seen = new Map<string, Set<string>>();
	private queue = Promise.resolve();
	private renewal: ReturnType<typeof setInterval> | null = null;
	private heartbeat: ReturnType<typeof setInterval> | null = null;
	private joinTimeout: ReturnType<typeof setTimeout> | null = null;
	private lastPong = 0;
	private room = "";
	private stopped = false;
	private joined = false;
	private status = "Подключаемся…";
	constructor(private update: (view: CallView) => void) {}
	async connect(meetingId: string) {
		try {
			const config = await api<{
				iceServers: RTCIceServer[];
				signalingUrl: string;
			}>("/api/backend/meetings/config");
			this.local = await navigator.mediaDevices.getUserMedia({
				audio: true,
				video: true,
			});
			if (this.stopped) {
				this.local.getTracks().forEach((t) => {
					t.stop();
				});
				return;
			}
			this.emit();
			const admission = await api<{ roomId: string; capability: string }>(
				`/api/backend/meetings/scheduled/${meetingId}/capabilities`,
				{ method: "POST" },
			);
			if (this.stopped) return;
			this.room = admission.roomId;
			this.socket = new WebSocket(config.signalingUrl);
			this.joinTimeout = setTimeout(
				() => this.stop("Сервер не ответил. Подключитесь повторно."),
				15000,
			);
			this.socket.onopen = () =>
				this.send("JOIN", undefined, undefined, admission.capability);
			this.socket.onmessage = (e) => {
				this.queue = this.queue
					.then(async () => {
						if (!this.stopped)
							await this.receive(
								JSON.parse(e.data) as Signal,
								config.iceServers,
							);
					})
					.catch(() =>
						this.stop("Не удалось установить связь. Подключитесь повторно."),
					);
			};
			this.socket.onerror = () =>
				this.stop("Нет связи с сервером. Подключитесь повторно.");
			this.socket.onclose = (event) =>
				this.stop(
					event.code === 1008
						? "Занятие завершено или отменено."
						: "Соединение закрыто. Обновите расписание или подключитесь повторно.",
				);
			this.renewal = setInterval(() => {
				void api<{ roomId: string; capability: string }>(
					`/api/backend/meetings/scheduled/${meetingId}/capabilities`,
					{ method: "POST" },
				)
					.then((grant) => {
						if (!this.stopped)
							this.send("REAUTHORIZE", undefined, undefined, grant.capability);
					})
					.catch(() =>
						this.stop("Доступ к занятию изменился. Подключитесь повторно."),
					);
			}, 60000);
			this.heartbeat = setInterval(() => {
				if (this.joined) {
					if (Date.now() - this.lastPong > 45000)
						this.stop("Соединение потеряно. Подключитесь повторно.");
					else this.send("PING");
				}
			}, 15000);
		} catch (error) {
			this.stop(
				error instanceof Error
					? error.message
					: "Не удалось подключиться. Проверьте доступ к камере и микрофону.",
			);
		}
	}
	private emit() {
		this.update({
			local: this.local,
			peers: [...this.remote].map(([id, stream]) => ({ id, stream })),
			status: this.status,
			connected: this.joined,
		});
	}
	private send(
		type: string,
		targetId?: string,
		payload?: unknown,
		capability?: string,
	) {
		if (this.socket?.readyState === WebSocket.OPEN)
			this.socket.send(
				JSON.stringify({
					version: 1,
					type,
					roomId: this.room,
					targetId,
					payload,
					capability,
				}),
			);
	}
	private peer(id: string, servers: RTCIceServer[]) {
		const current = this.peers.get(id);
		if (current) return current;
		if (this.peers.size >= 7) throw new Error("Group capacity exceeded");
		const peer = new RTCPeerConnection({
			iceServers: servers.filter((server) => Boolean(server.urls)),
		});
		this.peers.set(id, peer);
		for (const track of this.local?.getTracks() || [])
			peer.addTrack(track, this.local as MediaStream);
		peer.onicecandidate = (e) => {
			if (e.candidate)
				this.send("ICE_CANDIDATE", id, {
					candidate: e.candidate.candidate,
					sdpMid: e.candidate.sdpMid,
					sdpMLineIndex: e.candidate.sdpMLineIndex,
				});
		};
		peer.ontrack = (e) => {
			const stream = this.remote.get(id) || new MediaStream();
			if (!stream.getTracks().some((t) => t.id === e.track.id))
				stream.addTrack(e.track);
			this.remote.set(id, stream);
			this.emit();
		};
		peer.onconnectionstatechange = () => {
			if (peer.connectionState === "failed") {
				this.status = "Связь с участником потеряна. Переподключитесь.";
				this.emit();
			}
		};
		return peer;
	}
	private async offer(id: string, servers: RTCIceServer[]) {
		const peer = this.peer(id, servers);
		if (peer.localDescription) return;
		const offer = await peer.createOffer();
		await peer.setLocalDescription(offer);
		this.send("OFFER", id, { sdp: offer.sdp });
	}
	private async receive(message: Signal, servers: RTCIceServer[]) {
		const p = message.payload || {};
		if (message.type === "JOINED") {
			this.joined = true;
			this.lastPong = Date.now();
			if (this.joinTimeout) clearTimeout(this.joinTimeout);
			this.status = "Вы в занятии";
			this.emit();
			for (const member of p.peers || []) {
				this.peer(member.participantId, servers);
				if (member.initiator) await this.offer(member.participantId, servers);
			}
		} else if (message.type === "PONG") {
			this.lastPong = Date.now();
		} else if (message.type === "PARTICIPANT_JOINED" && p.participantId) {
			this.peer(p.participantId, servers);
			if (p.initiator) await this.offer(p.participantId, servers);
		} else if (message.type === "PARTICIPANT_LEFT" && p.participantId) {
			this.peers.get(p.participantId)?.close();
			this.peers.delete(p.participantId);
			this.remote.delete(p.participantId);
			this.candidates.delete(p.participantId);
			this.seen.delete(p.participantId);
			this.emit();
		} else if (
			["OFFER", "ANSWER", "ICE_CANDIDATE"].includes(message.type) &&
			message.senderId
		) {
			const id = message.senderId;
			const peer = this.peer(id, servers);
			if (message.type === "ICE_CANDIDATE") {
				const candidate = {
					candidate: p.candidate,
					sdpMid: p.sdpMid,
					sdpMLineIndex: p.sdpMLineIndex,
				};
				const key = JSON.stringify(candidate);
				const seen = this.seen.get(id) || new Set<string>();
				if (seen.has(key)) return;
				if (seen.size >= 512) throw new Error("Too many ICE candidates");
				seen.add(key);
				this.seen.set(id, seen);
				if (peer.remoteDescription) await peer.addIceCandidate(candidate);
				else {
					const pending = this.candidates.get(id) || [];
					pending.push(candidate);
					this.candidates.set(id, pending);
				}
			} else {
				await peer.setRemoteDescription({
					type: message.type === "OFFER" ? "offer" : "answer",
					sdp: p.sdp,
				});
				for (const candidate of this.candidates.get(id) || [])
					await peer.addIceCandidate(candidate);
				this.candidates.delete(id);
				if (message.type === "OFFER") {
					await peer.setLocalDescription(await peer.createAnswer());
					this.send("ANSWER", id, { sdp: peer.localDescription?.sdp });
				}
			}
		} else if (["ERROR", "ROOM_FULL"].includes(message.type)) {
			this.stop(
				p.code === "ACCESS_REVOKED"
					? "Занятие завершено или отменено."
					: "Вход недоступен. Обновите расписание и попробуйте снова.",
			);
		}
	}
	toggle(kind: "audio" | "video") {
		const tracks = this.local?.getTracks().filter((t) => t.kind === kind) || [];
		const enabled = !tracks.some((t) => t.enabled);
		tracks.forEach((t) => {
			t.enabled = enabled;
		});
		return enabled;
	}
	stop(message = "Вы вышли из занятия") {
		if (this.stopped) return;
		this.send("LEAVE");
		this.stopped = true;
		this.joined = false;
		if (this.heartbeat) clearInterval(this.heartbeat);
		if (this.renewal) clearInterval(this.renewal);
		if (this.joinTimeout) clearTimeout(this.joinTimeout);
		if (this.socket) {
			this.socket.onclose = null;
			this.socket.onerror = null;
			this.socket.close();
		}
		for (const peer of this.peers.values()) peer.close();
		this.peers.clear();
		this.remote.clear();
		this.candidates.clear();
		this.seen.clear();
		this.local?.getTracks().forEach((t) => {
			t.stop();
		});
		this.local = null;
		this.status = message;
		this.emit();
	}
}
