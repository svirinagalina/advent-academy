import { NextResponse, type NextRequest } from "next/server";
import {
	ApiError,
	authorized,
	ensureOk,
	login,
	logout,
	requireOrigin,
} from "@/lib/server-session";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
function failure(error: unknown) {
	return NextResponse.json(
		{ message: error instanceof ApiError ? error.message : "Ошибка сервера" },
		{
			status:
				error instanceof ApiError
					? error.status
					: error instanceof SyntaxError
						? 400
						: 500,
			headers: { "Cache-Control": "no-store" },
		},
	);
}
export async function GET() {
	try {
		const response = await authorized("/api/users/me");
		await ensureOk(response);
		return NextResponse.json(await response.json(), {
			headers: { "Cache-Control": "no-store" },
		});
	} catch (error) {
		return failure(error);
	}
}
export async function POST(request: NextRequest) {
	try {
		requireOrigin(request);
		return NextResponse.json(await login(await request.json()), {
			headers: { "Cache-Control": "no-store" },
		});
	} catch (error) {
		return failure(error);
	}
}
export async function DELETE(request: NextRequest) {
	try {
		requireOrigin(request);
		await logout();
		return new NextResponse(null, { status: 204 });
	} catch (error) {
		return failure(error);
	}
}
