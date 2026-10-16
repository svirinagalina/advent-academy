import { NextResponse, type NextRequest } from "next/server";
import { ApiError, requireOrigin, upstream } from "@/lib/server-session";
export const runtime = "nodejs";
export async function POST(request: NextRequest) {
	try {
		requireOrigin(request);
		const reader = request.body?.getReader();
		if (!reader) throw new ApiError(400, "Проверьте данные приглашения");
		const chunks: Uint8Array[] = [];
		let size = 0;
		try {
			for (;;) {
				const part = await reader.read();
				if (part.done) break;
				size += part.value.byteLength;
				if (size > 8192) {
					await reader.cancel();
					throw new ApiError(413, "Слишком большой запрос");
				}
				chunks.push(part.value);
			}
		} finally {
			reader.releaseLock();
		}
		const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
		const response = await upstream("/api/auth/invitations/accept", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				token: body.token,
				password: body.password,
				confirmPassword: body.confirmPassword,
			}),
		});
		return new NextResponse(
			response.status === 204 ? null : await response.text(),
			{
				status: response.status,
				headers: {
					"Content-Type": "application/json",
					"Cache-Control": "no-store",
					"Referrer-Policy": "no-referrer",
				},
			},
		);
	} catch (error) {
		return NextResponse.json(
			{
				message:
					error instanceof ApiError
						? error.message
						: "Проверьте данные приглашения",
			},
			{
				status: error instanceof ApiError ? error.status : 400,
				headers: { "Cache-Control": "no-store" },
			},
		);
	}
}
