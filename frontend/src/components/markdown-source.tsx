"use client";
import { Compartment, EditorState } from "@codemirror/state";
import {
	EditorView,
	keymap,
	lineNumbers,
	highlightActiveLineGutter,
} from "@codemirror/view";
import {
	defaultKeymap,
	history,
	historyKeymap,
	isolateHistory,
} from "@codemirror/commands";
import { markdown } from "@codemirror/lang-markdown";
import { html } from "@codemirror/lang-html";
import {
	syntaxHighlighting,
	defaultHighlightStyle,
	HighlightStyle,
} from "@codemirror/language";
import { tags } from "@lezer/highlight";
import { forwardRef, useEffect, useImperativeHandle, useRef } from "react";

export type SourceHandle = {
	replace: (text: string) => void;
	insert: (before: string, after?: string, fallback?: string) => void;
};
// Mounted once per dialog. Hiding panes never recreates state, selection or history.
const MarkdownSource = forwardRef<
	SourceHandle,
	{
		initial: string;
		onChange: (value: string) => void;
		language?: "markdown" | "html";
	}
>(({ initial, onChange, language = "markdown" }, ref) => {
	const host = useRef<HTMLDivElement>(null);
	const view = useRef<EditorView | null>(null);
	const change = useRef(onChange);
	const initialSource = useRef(initial);
	change.current = onChange;
	useImperativeHandle(
		ref,
		() => ({
			replace(text) {
				const editor = view.current;
				if (editor) {
					editor.dispatch({
						changes: { from: 0, to: editor.state.doc.length, insert: text },
						annotations: isolateHistory.of("full"),
					});
					editor.focus();
				}
			},
			insert(before, after = "", fallback = "") {
				const editor = view.current;
				if (!editor || language === "html") return;
				const { from, to } = editor.state.selection.main;
				let text = editor.state.sliceDoc(from, to) || fallback;
				if (before === "\n- ") text = text.replaceAll("\n", "\n- ");
				editor.dispatch({
					changes: { from, to, insert: before + text + after },
					selection: {
						anchor: from + before.length,
						head: from + before.length + text.length,
					},
					scrollIntoView: true,
				});
				editor.focus();
			},
		}),
		[language],
	);
	useEffect(() => {
		if (!host.current) return;
		const appearance = new Compartment();
		const isDark = () => document.documentElement.dataset.theme === "dark";
		const darkHighlight = HighlightStyle.define([
			{ tag: tags.keyword, color: "#c4b5fd" },
			{ tag: tags.heading, color: "#67e8f9", fontWeight: "bold" },
			{ tag: tags.strong, fontWeight: "bold" },
			{ tag: tags.emphasis, fontStyle: "italic" },
			{ tag: tags.link, color: "#93c5fd" },
			{ tag: tags.string, color: "#86efac" },
			{ tag: tags.comment, color: "#cbd5e1" },
		]);
		const appearanceExtensions = () => [
			EditorView.theme({}, { dark: isDark() }),
			syntaxHighlighting(isDark() ? darkHighlight : defaultHighlightStyle),
		];
		const editor = new EditorView({
			parent: host.current,
			state: EditorState.create({
				doc: initialSource.current,
				extensions: [
					appearance.of(appearanceExtensions()),

					lineNumbers(),
					highlightActiveLineGutter(),
					history(),
					keymap.of([...defaultKeymap, ...historyKeymap]),
					language === "html" ? html() : markdown(),
					EditorView.lineWrapping,
					EditorView.contentAttributes.of({
						"aria-label":
							language === "html"
								? "Сгенерированный HTML"
								: "Исходник Markdown",
					}),
					EditorState.readOnly.of(language === "html"),
					EditorView.editable.of(language !== "html"),
					EditorView.updateListener.of((update) => {
						if (update.docChanged) change.current(update.state.doc.toString());
					}),
				],
			}),
		});
		view.current = editor;
		const observer = new MutationObserver(() =>
			editor.dispatch({
				effects: appearance.reconfigure(appearanceExtensions()),
			}),
		);
		observer.observe(document.documentElement, {
			attributes: true,
			attributeFilter: ["data-theme"],
		});
		return () => {
			observer.disconnect();
			editor.destroy();
			view.current = null;
		};
	}, [language]);
	useEffect(() => {
		if (language !== "html" || !view.current) return;
		const editor = view.current;
		editor.dispatch({
			changes: { from: 0, to: editor.state.doc.length, insert: initial },
		});
	}, [initial, language]);
	return <div className="markdown-source" ref={host} />;
});
MarkdownSource.displayName = "MarkdownSource";
export default MarkdownSource;
