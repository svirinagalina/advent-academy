"use client";
import {useCallback,useEffect,useRef,useState} from "react";

/** No script, form, popup or top-navigation permission; same-origin permits private image cookies and trusted parent sizing. */
export function SafeHtml({html,title="Материал урока",className=""}: {html:string;title?:string;className?:string}) {
    const frame=useRef<HTMLIFrameElement>(null);
    const cleanup=useRef<() => void>(() => {});
    const [height,setHeight]=useState(180);
    const style=useCallback(() => {
        const doc=frame.current?.contentDocument;
        if (!doc?.body) return;
        const parent=getComputedStyle(document.documentElement);
        doc.documentElement.style.colorScheme=parent.colorScheme;
        for(const name of ["--foreground","--muted","--accent","--border","--field"]) {
            doc.documentElement.style.setProperty(name,parent.getPropertyValue(name));
        }
    },[]);
    const loaded=useCallback(() => {
        cleanup.current();
        const doc=frame.current?.contentDocument;
        if(!doc?.body)return;
        style();
        const resize=()=>setHeight(Math.max(120,Math.min(100000,Math.ceil(doc.querySelector("main")?.getBoundingClientRect().height || 0)+32)));
        const observer=new ResizeObserver(resize);observer.observe(doc.querySelector("main") || doc.body);resize();
        cleanup.current=()=>observer.disconnect();
    },[style]);
    useEffect(()=>{
        const observer=new MutationObserver(style);observer.observe(document.documentElement,{attributes:true,attributeFilter:["data-theme","style"]});
        return ()=>{observer.disconnect();cleanup.current();};
    },[style]);
    const source=`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'none'; img-src 'self' data: https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-src 'none'">
<style>body{margin:0;padding:4px;color:var(--foreground);font:16px/1.6 system-ui,sans-serif;overflow-wrap:anywhere}h1,h2,h3{line-height:1.25}a{color:var(--accent)}img{max-width:100%;height:auto}pre{overflow:auto;padding:1rem;background:var(--field);border-radius:.5rem}code{font-family:monospace}table{border-collapse:collapse;max-width:100%;display:block;overflow:auto}th,td{border:1px solid var(--border);padding:.5rem}blockquote{border-left:3px solid var(--accent);margin-left:0;padding-left:1rem;color:var(--muted)}</style>
</head><body><main class="lesson-material">${html}</main></body></html>`;
    return <iframe ref={frame} title={title} className={`safe-html ${className}`} sandbox="allow-same-origin allow-downloads"
        referrerPolicy="no-referrer" srcDoc={source} onLoad={loaded} style={{height,width:"100%",border:0,display:"block"}}/>;
}
