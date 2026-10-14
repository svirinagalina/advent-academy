import { NextResponse } from "next/server";
import { sessionKey, upstream } from "@/lib/server-session";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export async function GET() {
	try {
		sessionKey();
		const response = await upstream("/actuator/health/readiness");
		if (!response.ok) throw new Error("Gateway is not ready");
		return NextResponse.json(
			{ status: "UP" },
			{ headers: { "Cache-Control": "no-store" } },
		);
	} catch {
		return NextResponse.json(
			{ status: "DOWN" },
			{ status: 503, headers: { "Cache-Control": "no-store" } },
		);
	}
}
