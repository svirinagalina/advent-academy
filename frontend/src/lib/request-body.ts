import "server-only";
import type {NextRequest} from "next/server";
import {ApiError} from "@/lib/server-session";

/** Includes chunked requests; login must be bounded before JSON parsing or forwarding. */
export async function readBoundedJson(request: NextRequest, limit=8192): Promise<unknown> {
    if(Number(request.headers.get("content-length"))>limit)throw new ApiError(413,"Слишком большой запрос");
    const reader=request.body?.getReader();
    if(!reader)throw new ApiError(400,"Проверьте данные");
    const chunks:Uint8Array[]=[];let length=0;
    try {
        for(;;) {
            const part=await reader.read();if(part.done)break;
            length+=part.value.byteLength;
            if(length>limit){await reader.cancel();throw new ApiError(413,"Слишком большой запрос");}
            chunks.push(part.value);
        }
    } finally {reader.releaseLock();}
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}
