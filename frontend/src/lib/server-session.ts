import "server-only";
import {
	createCipheriv,
	createDecipheriv,
	createHash,
	randomBytes,
} from "node:crypto";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const cookieName = "advent_session";
const lifetime = 7 * 24 * 60 * 60;
type Session = { accessToken: string; refreshToken: string; expiresAt: number };
type AuthResponse = {
	accessToken: string;
	refreshToken: string;
	expiresIn: number;
	mfaRequired: boolean;
};
export class ApiError extends Error {
	constructor(
		public status: number,
		message: string,
	) {
		super(message);
	}
}
export function sessionKey() {
	const value = process.env.SESSION_ENCRYPTION_KEY;
	if (!value || Buffer.from(value, "base64").length !== 32)
		throw new ApiError(503, "Кабинет временно недоступен");
	return Buffer.from(value, "base64");
}
function encode(session: Session) {
	const iv = randomBytes(12);
	const cipher = createCipheriv("aes-256-gcm", sessionKey(), iv);
	const data = Buffer.concat([
		cipher.update(JSON.stringify(session), "utf8"),
		cipher.final(),
	]);
	return Buffer.concat([iv, cipher.getAuthTag(), data]).toString("base64url");
}
function decode(value: string): Session | null {
	const encryptionKey = sessionKey();
	try {
		const bytes = Buffer.from(value, "base64url");
		const cipher = createDecipheriv(
			"aes-256-gcm",
			encryptionKey,
			bytes.subarray(0, 12),
		);
		cipher.setAuthTag(bytes.subarray(12, 28));
		const data = JSON.parse(
			Buffer.concat([
				cipher.update(bytes.subarray(28)),
				cipher.final(),
			]).toString("utf8"),
		);
		if (
			typeof data.accessToken !== "string" ||
			typeof data.refreshToken !== "string" ||
			typeof data.expiresAt !== "number"
		)
			return null;
		return data;
	} catch {
		return null;
	}
}
async function save(session: Session) {
	const origin = process.env.FRONTEND_ORIGIN || "http://localhost:3000";
	(await cookies()).set(cookieName, encode(session), {
		httpOnly: true,
		secure: origin.startsWith("https://"),
		sameSite: "lax",
		path: "/",
		maxAge: lifetime,
	});
}
export function requireOrigin(request: NextRequest) {
	const expected = process.env.FRONTEND_ORIGIN || "http://localhost:3000";
	if (request.headers.get("origin") !== new URL(expected).origin)
		throw new ApiError(403, "Недопустимый источник запроса");
}
export async function upstream(path: string, init: RequestInit = {}) {
	try {
		return await fetch(
			`${process.env.API_GATEWAY_URL || "http://localhost:8090"}${path}`,
			{
				...init,
				cache: "no-store",
				redirect: "error",
				signal: AbortSignal.timeout(15000),
			},
		);
	} catch {
		throw new ApiError(502, "Сервис временно недоступен. Попробуйте ещё раз.");
	}
}
export async function ensureOk(response: Response) {
	if (response.ok) return;
	const body = await response.json().catch(() => ({}));
	throw new ApiError(
		response.status,
		body.message || body.error || "Не удалось выполнить запрос",
	);
}
function fromAuth(auth: AuthResponse): Session {
	if (!auth.accessToken || !auth.refreshToken || auth.expiresIn <= 0)
		throw new ApiError(502, "Некорректный ответ сервиса авторизации");
	return {
		accessToken: auth.accessToken,
		refreshToken: auth.refreshToken,
		expiresAt: Date.now() + auth.expiresIn * 1000,
	};
}
export async function login(body: unknown) {
	sessionKey();
	const response = await upstream("/api/auth/login", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(body),
	});
	await ensureOk(response);
	const auth: AuthResponse = await response.json();
	if (auth.mfaRequired) return { mfaRequired: true };
	await save(fromAuth(auth));
	return { mfaRequired: false };
}
// One standalone frontend instance: concurrent calls share a single rotation, including requests
// carrying the previous cookie for a short overlap. Multi-replica deployments need a shared store.
const rotations = new Map<string, Promise<Session>>();
async function rotate(session: Session) {
	const digest = createHash("sha256")
		.update(session.refreshToken)
		.digest("hex");
	let pending = rotations.get(digest);
	if (!pending) {
		pending = (async () => {
			const response = await upstream("/api/auth/refresh", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ refreshToken: session.refreshToken }),
			});
			await ensureOk(response);
			return fromAuth(await response.json());
		})();
		rotations.set(digest, pending);
		setTimeout(() => rotations.delete(digest), 10000).unref();
	}
	const updated = await pending;
	await save(updated);
	return updated;
}
async function read() {
	const value = (await cookies()).get(cookieName)?.value;
	const session = value ? decode(value) : null;
	if (!session) throw new ApiError(401, "Войдите в кабинет");
	return session;
}
export async function authorized(path: string, init: RequestInit = {}) {
	let session = await read();
	if (session.expiresAt < Date.now() + 5000) session = await rotate(session);
	const call = () =>
		upstream(path, {
			...init,
			headers: {
				...init.headers,
				Authorization: `Bearer ${session.accessToken}`,
			},
		});
	let response = await call();
	if (response.status === 401) {
		session = await rotate(session);
		response = await call();
	}
	return response;
}
export async function logout() {
	const value = (await cookies()).get(cookieName)?.value;
	const session = value ? decode(value) : null;
	if (session) {
		const digest = createHash("sha256")
			.update(session.refreshToken)
			.digest("hex");
		const current = rotations.has(digest)
			? await rotations.get(digest)
			: session;
		const response = await upstream("/api/auth/logout", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ refreshToken: current?.refreshToken }),
		});
		await ensureOk(response);
		rotations.delete(digest);
	}
	(await cookies()).delete(cookieName);
}
