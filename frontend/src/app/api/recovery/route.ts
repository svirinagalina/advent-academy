import { NextResponse, type NextRequest } from "next/server";
import { ApiError, requireOrigin, upstream } from "@/lib/server-session";
export const runtime = "nodejs";
export async function POST(request: NextRequest) {
    try {
        requireOrigin(request);
        const reader = request.body?.getReader();
        if (!reader) throw new ApiError(400, "Проверьте данные");
        const chunks: Uint8Array[] = []; let length = 0;
        try {
            for (;;) {
                const part = await reader.read(); if (part.done) break;
                length += part.value.byteLength;
                if (length > 8192) {await reader.cancel(); throw new ApiError(413, "Слишком большой запрос");}
                chunks.push(part.value);
            }
        } finally {reader.releaseLock();}
        const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
        const routes: Record<string,string> = {request:"password-reset", reset:"password-reset/confirm", verify:"confirm-email", resend:"resend-verification"};
        const route = routes[body.action];
        if (!route) throw new ApiError(400,"Неизвестное действие");
        const payload = body.action === "reset" ? {token:body.token,newPassword:body.password,confirmPassword:body.confirmPassword}
            : body.action === "verify" ? {token:body.token} : {email:body.email};
        // confirm-email accepts a servlet request parameter; keep the token in the body.
        const verifying = body.action === "verify";
        if (verifying && typeof body.token !== "string") throw new ApiError(400,"Проверьте данные");
        const response = await upstream(`/api/auth/${route}`, {method:"POST",
            headers:{"Content-Type":verifying ? "application/x-www-form-urlencoded" : "application/json"},
            body:verifying ? new URLSearchParams({token:body.token}).toString() : JSON.stringify(payload)});
        return new NextResponse(response.status === 204 ? null : await response.text(), {status:response.status,headers:{"Content-Type":"application/json","Cache-Control":"no-store","Referrer-Policy":"no-referrer"}});
    } catch (error) {
        return NextResponse.json({message:error instanceof ApiError ? error.message : "Проверьте данные"}, {status:error instanceof ApiError ? error.status : 400,headers:{"Cache-Control":"no-store"}});
    }
}
