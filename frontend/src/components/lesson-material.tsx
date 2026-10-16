"use client";
import { useEffect, useRef } from "react";
import hljs from "highlight.js/lib/core";
import java from "highlight.js/lib/languages/java";
import javascript from "highlight.js/lib/languages/javascript";
import typescript from "highlight.js/lib/languages/typescript";
import python from "highlight.js/lib/languages/python";
import sql from "highlight.js/lib/languages/sql";
import xml from "highlight.js/lib/languages/xml";
import css from "highlight.js/lib/languages/css";
import json from "highlight.js/lib/languages/json";
import bash from "highlight.js/lib/languages/bash";

for (const [name, grammar] of Object.entries({
	java,
	javascript,
	typescript,
	python,
	sql,
	xml,
	css,
	json,
	bash,
})) {
	hljs.registerLanguage(name, grammar);
}

// HTML always comes from the server sanitizer. Only textContent enters the highlighter.
export default function LessonMaterial({
	html,
	className = "",
}: {
	html: string;
	className?: string;
}) {
	const host = useRef<HTMLDivElement>(null);
	useEffect(() => {
		if (!html) return;
		const cleanups: (() => void)[] = [];
		for (const code of host.current?.querySelectorAll<HTMLElement>(
			"pre > code",
		) || []) {
			const pre = code.parentElement;
			if (!pre) continue;
			const source = code.textContent || "";
			const language = /\blanguage-([\w+-]+)/
				.exec(code.className)?.[1]
				?.toLowerCase();
			const known = language && hljs.getLanguage(language);
			// Large examples remain plain text rather than blocking interaction on parsing.
			if (known && source.length <= 100_000) {
				code.innerHTML = hljs.highlight(source, {
					language,
					ignoreIllegals: true,
				}).value;
			}
			const frame = document.createElement("figure");
			frame.className = "lesson-code";
			const header = document.createElement("figcaption");
			header.className = "lesson-code-toolbar";
			const label = document.createElement("span");
			label.textContent = known ? language : "Код";
			const copy = document.createElement("button");
			copy.type = "button";
			copy.className = "text-link";
			copy.textContent = "Копировать код";
			const status = document.createElement("span");
			status.className = "lesson-code-status";
			status.setAttribute("role", "status");
			let active = true;
			const onCopy = async () => {
				try {
					await navigator.clipboard.writeText(source);
					if (active) status.textContent = "Код скопирован";
				} catch {
					if (active)
						status.textContent =
							"Не удалось скопировать. Выделите код или повторите.";
				}
			};
			copy.addEventListener("click", onCopy);
			header.append(label, copy, status);
			const numbers = document.createElement("span");
			numbers.className = "lesson-code-numbers";
			numbers.setAttribute("aria-hidden", "true");
			const count = Math.max(1, source.replace(/\n$/, "").split("\n").length);
			numbers.dataset.numbers = Array.from(
				{ length: count },
				(_, index) => index + 1,
			).join("\n");
			pre.tabIndex = 0;
			pre.setAttribute("role", "region");
			pre.setAttribute("aria-label", known ? `Код ${language}` : "Блок кода");
			pre.classList.add("lesson-code-scroll");
			pre.before(frame);
			frame.append(header, pre);
			pre.prepend(numbers);
			cleanups.push(() => {
				active = false;
				copy.removeEventListener("click", onCopy);
				numbers.remove();
				code.textContent = source;
				frame.before(pre);
				frame.remove();
			});
		}
		return () => {
			for (const cleanup of cleanups) cleanup();
		};
	}, [html]);
	return (
		<div
			ref={host}
			className={`lesson-material ${className}`}
			// biome-ignore lint/security/noDangerouslySetInnerHtml: only server-sanitized lesson HTML is accepted.
			dangerouslySetInnerHTML={{
				__html: html.replaceAll("/api/assets/", "/api/backend/assets/"),
			}}
		/>
	);
}
