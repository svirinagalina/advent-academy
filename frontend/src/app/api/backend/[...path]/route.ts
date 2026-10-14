import { type NextRequest, NextResponse } from "next/server";
import {
	ApiError,
	authorized,
	requireOrigin,
	upstream,
} from "@/lib/server-session";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const uuid =
	"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const reading = new RegExp(
	`^(learning/assignments|learning/courses(?:/${uuid}(?:/lessons/${uuid}(?:/assignment)?)?)?|teaching/submissions(?:/${uuid})?|assets/${uuid}|catalog|teaching/courses|users/teaching/courses/${uuid}/students|meetings/config|meetings/scheduled(?:/teaching|/${uuid})?)$`,
);
const completing = new RegExp(
	`^(learning/courses/${uuid}/lessons/${uuid}/(complete|submissions)|teaching/submissions/${uuid}/review|meetings/scheduled(?:/${uuid}/(?:start|finish|cancel|capabilities))?)$`,
);
const management: Record<string, RegExp> = {
	GET: new RegExp(
		`^(courses(?:/${uuid})?|components(?:/${uuid})?|lessons/${uuid}/content|users/teaching/courses/${uuid}/candidates)$`,
	),
	POST: new RegExp(
		`^(courses|components|courses/${uuid}/(?:components|publish|archive)|components/${uuid}/(?:lessons|publish)|lessons/${uuid}/content(?:/publish)?)$`,
	),
	PATCH: new RegExp(`^courses/${uuid}$`),
	DELETE: new RegExp(`^courses/${uuid}/components/${uuid}$`),
	PUT: new RegExp(
		`^(courses/${uuid}/components/order|learning/courses/${uuid}/students/${uuid})$`,
	),
};
async function boundedBody(request: NextRequest, limit = 32768) {
	if (Number(request.headers.get("content-length")) > limit)
		throw new ApiError(413, "Слишком большой запрос");
	if (!request.body) return Buffer.alloc(0);
	const reader = request.body.getReader();
	const chunks: Uint8Array[] = [];
	let length = 0;
	try {
		for (;;) {
			const chunk = await reader.read();
			if (chunk.done) break;
			length += chunk.value.byteLength;
			if (length > limit) {
				await reader.cancel();
				throw new ApiError(413, "Слишком большой запрос");
			}
			chunks.push(chunk.value);
		}
		return Buffer.concat(chunks);
	} finally {
		reader.releaseLock();
	}
}
async function proxy(
	request: NextRequest,
	context: { params: Promise<{ path: string[] }> },
) {
	try {
		const path = (await context.params).path.join("/");
		const existing =
			request.method === "GET"
				? reading
				: request.method === "PUT"
					? new RegExp(`^meetings/scheduled/${uuid}$`)
					: request.method === "POST"
						? completing
						: null;
		if (!existing?.test(path) && !management[request.method]?.test(path))
			throw new ApiError(404, "Запрос не поддерживается");
		if (request.method !== "GET") requireOrigin(request);
		const values = new URLSearchParams();
		const names =
			path === "courses"
				? ["managed"]
				: path.endsWith("/candidates")
					? ["query", "limit"]
					: path === "teaching/submissions" ||
							path.startsWith("meetings/scheduled")
						? ["status", "tab", "page", "size"]
						: [];
		for (const name of names) {
			const value = request.nextUrl.searchParams.get(name);
			if (value !== null) values.set(name, value);
		}
		const query = values.size ? `?${values}` : "";
		const init: RequestInit = { method: request.method };
		if (request.method !== "GET" && request.method !== "DELETE") {
			const upload =
				request.method === "POST" &&
				new RegExp(`^lessons/${uuid}/content$`).test(path);
			if (upload) {
				const type = request.headers.get("content-type") || "";
				if (!/^multipart\/form-data;.*boundary=/i.test(type))
					throw new ApiError(400, "Выберите файл материала");
				const body = await boundedBody(request, 6 * 1024 * 1024);
				init.body = new Uint8Array(body);
				init.headers = { "Content-Type": type };
			} else {
				const body = await boundedBody(request);
				if (body.length) {
					init.body = body.toString("utf8");
					init.headers = { "Content-Type": "application/json" };
				}
			}
		}
		const response =
			path === "catalog"
				? await upstream("/api/catalog")
				: await authorized(`/api/${path}${query}`, init);
		if (path === "meetings/config" && response.ok) {
			const url =
				process.env.MEETING_SIGNALING_URL || "ws://localhost:8090/ws/meetings";
			const signal = new URL(url);
			if (!["ws:", "wss:"].includes(signal.protocol))
				throw new ApiError(503, "Звонки временно недоступны");
			return NextResponse.json(
				{ ...(await response.json()), signalingUrl: url },
				{ headers: { "Cache-Control": "private, no-store" } },
			);
		}
		const headers = new Headers({ "Cache-Control": "private, no-store" });
		for (const name of ["content-type", "content-disposition"]) {
			const value = response.headers.get(name);
			if (value) headers.set(name, value);
		}
		headers.set("X-Content-Type-Options", "nosniff");
		return new NextResponse(
			[204, 205, 304].includes(response.status)
				? null
				: await response.arrayBuffer(),
			{
				status: response.status,
				headers,
			},
		);
	} catch (error) {
		return NextResponse.json(
			{ message: error instanceof ApiError ? error.message : "Ошибка сервера" },
			{ status: error instanceof ApiError ? error.status : 500 },
		);
	}
}
export const GET = proxy;
export const POST = proxy;

export const PUT = proxy;

export const PATCH = proxy;
export const DELETE = proxy;
