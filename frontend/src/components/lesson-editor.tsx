"use client";
import { SafeHtml } from "@/components/safe-html";
import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { api, ClientError } from "@/lib/learning";
import { managementError, request } from "@/lib/management";
import type { SourceHandle } from "@/components/markdown-source";
const Source = dynamic(() => import("@/components/markdown-source"), {
	ssr: false,
	loading: () => <p role="status">Загружаем CodeMirror…</p>,
});
export type Revision = {
	markdown: string;
	html: string;
	version: number;
	published: boolean;
};
export default function LessonEditor({
	lessonId,
	title,
	initial,
	close,
	changed,
}: {
	lessonId: string;
	title: string;
	initial: Revision;
	close: () => void;
	changed: () => Promise<void>;
}) {
	const dialog = useRef<HTMLDialogElement>(null);
	const source = useRef<SourceHandle>(null);
	const [text, setText] = useState(initial.markdown);
	const [saved, setSaved] = useState(initial);
	const [tab, setTab] = useState("Markdown");
	const [split, setSplit] = useState(false);
	const [preview, setPreview] = useState({
		text: initial.markdown,
		html: initial.html,
	});
	const [previewError, setPreviewError] = useState("");
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [busy, setBusy] = useState(false);
	const [conflict, setConflict] = useState(false);
	const [replacement, setReplacement] = useState<string | null>(null);
	const dirty = text !== saved.markdown;
	const dirtyNow = useRef(dirty);
	dirtyNow.current = dirty;
	const path = `lessons/${lessonId}/content/draft`;
	useEffect(() => {
		const el = dialog.current;
		const previous = document.activeElement as HTMLElement | null;
		el?.showModal();
		const old = document.body.style.overflow;
		document.body.style.overflow = "hidden";
		return () => {
			el?.close();
			document.body.style.overflow = old;
			previous?.focus();
		};
	}, []);
	useEffect(() => {
		const warn = (event: BeforeUnloadEvent) => {
			if (dirty || conflict) {
				event.preventDefault();
				event.returnValue = "";
			}
		};
		window.addEventListener("beforeunload", warn);
		return () => window.removeEventListener("beforeunload", warn);
	}, [dirty, conflict]);
	useEffect(() => {
		let active = true;
		setPreviewError("");
		const timer = setTimeout(() => {
			request<Revision>(`${path}/preview`, "POST", { markdown: text })
				.then((result) => {
					if (active) setPreview({ text, html: result.html });
				})
				.catch((e) => {
					if (active) setPreviewError(managementError(e));
				});
		}, 400);
		return () => {
			active = false;
			clearTimeout(timer);
		};
	}, [path, text]);
	function dismiss() {
		if (
			!busy &&
			(!(dirty || conflict) ||
				window.confirm(
					"Есть несохранённые изменения. Закрыть редактор и потерять их?",
				))
		)
			close();
	}
	async function save(publish: boolean) {
		if (busy || conflict) return;
		setBusy(true);
		setError("");
		setNotice("");
		try {
			let revision = saved;
			if (dirty || saved.version === 0) {
				revision = await request<Revision>(path, "PUT", {
					markdown: text,
					version: saved.version,
				});
				setSaved(revision);
			}
			if (publish) {
				revision = await request<Revision>(`${path}/publish`, "POST", {
					version: revision.version,
				});
				setSaved(revision);
			}
			setNotice(
				publish
					? "Материал опубликован. Модуль и курс публикуются отдельно."
					: "Черновик сохранён. Опубликованный материал не изменился.",
			);
			await changed();
		} catch (e) {
			if (e instanceof ClientError && e.status === 409) {
				setConflict(true);
				setError(
					"Материал изменён в другом редакторе. Ваш текст сохранён в этом окне. Скачайте Markdown, затем откройте актуальную редакцию и перенесите изменения.",
				);
			} else setError(`${managementError(e)} Ваш текст остаётся в редакторе.`);
		} finally {
			setBusy(false);
		}
	}
	async function copyHtml() {
		try {
			await navigator.clipboard.writeText(preview.html);
			setNotice("HTML скопирован");
		} catch {
			setError("Не удалось скопировать HTML. Выделите текст вручную.");
		}
	}

	function download() {
		const url = URL.createObjectURL(
			new Blob([text], { type: "text/markdown;charset=utf-8" }),
		);
		const a = document.createElement("a");
		a.href = url;
		a.download = "lesson.md";
		a.click();
		setTimeout(() => URL.revokeObjectURL(url), 1000);
	}
	async function importFile(file?: File) {
		if (!file) return;
		if (file.size > 5 * 1024 * 1024) {
			setError("Файл слишком большой. Максимум 5 MiB.");
			return;
		}
		if (!/\.(md|markdown)$/i.test(file.name)) {
			setError("Выберите Markdown-файл.");
			return;
		}
		try {
			const content = new TextDecoder("utf-8", { fatal: true }).decode(
				await file.arrayBuffer(),
			);
			if (
				dirtyNow.current &&
				!window.confirm(
					"Заменить несохранённый текст импортированным Markdown?",
				)
			)
				return;
			setReplacement(content);
			setTab("Markdown");
			setError("");
		} catch {
			setError("Не удалось прочитать Markdown в UTF-8.");
		}
	}
	useEffect(() => {
		if (replacement === null || !source.current) return;
		// Replace through the same editor transaction so Undo restores the previous source.
		// The source handle's replacement operation is explicit to preserve history.
		source.current.replace(replacement);
		setReplacement(null);
	}, [replacement]);
	async function image(file?: File) {
		if (!file) return;
		if (file.size > 10 * 1024 * 1024) {
			setError("Изображение слишком большое. Максимум 10 MiB.");
			return;
		}
		setBusy(true);
		setError("");
		try {
			const body = new FormData();
			body.append("file", file);
			const asset = await api<{ id: string }>(
				`/api/backend/lessons/${lessonId}/assets`,
				{ method: "POST", body },
			);
			source.current?.insert("![", `](asset:${asset.id})`, "Изображение");
			setTab("Markdown");
		} catch (e) {
			setError(managementError(e));
		} finally {
			setBusy(false);
		}
	}
	return (
		<dialog
			ref={dialog}
			className="lesson-editor"
			aria-labelledby="lesson-editor-title"
			onCancel={(e) => {
				e.preventDefault();
				dismiss();
			}}
		>
			<div className="management-heading">
				<h2 id="lesson-editor-title">Редактировать содержимое: {title}</h2>
				<button
					type="button"
					className="icon-button"
					aria-label="Закрыть редактор"
					disabled={busy}
					onClick={dismiss}
				>
					×
				</button>
			</div>
			<div className="management-actions">
				<span role="status">
					{busy
						? "Выполняем запрос…"
						: dirty
							? "Есть несохранённые изменения"
							: "Все изменения сохранены"}
				</span>
				<button
					className="button secondary"
					type="button"
					disabled={busy || conflict}
					onClick={() => void save(false)}
				>
					Сохранить черновик
				</button>
				<button
					className="button primary"
					type="button"
					disabled={busy || conflict || !text.trim()}
					onClick={() => void save(true)}
				>
					Опубликовать
				</button>
				<button className="button secondary" type="button" onClick={download}>
					Скачать Markdown
				</button>
			</div>
			{error && (
				<p className="management-alert" role="alert">
					{error}
				</p>
			)}
			{notice && (
				<p className="management-success" role="status">
					{notice}
				</p>
			)}
			<div className="management-actions">
				<label>
					Импорт .md
					<input
						aria-label="Импорт .md"
						type="file"
						accept=".md,.markdown"
						disabled={busy}
						onChange={(e) => {
							void importFile(e.target.files?.[0]);
							e.target.value = "";
						}}
					/>
				</label>
				<label>
					Вставить изображение
					<input
						aria-label="Вставить изображение"
						type="file"
						accept="image/png,image/jpeg,image/gif,image/webp,image/svg+xml"
						disabled={busy}
						onChange={(e) => {
							void image(e.target.files?.[0]);
							e.target.value = "";
						}}
					/>
				</label>
				<label className="editor-split-toggle">
					<input
						type="checkbox"
						checked={split}
						onChange={(e) => {
						setSplit(e.target.checked);
						setTab("Markdown");
					}}
					/>
					Markdown и предпросмотр рядом
				</label>
			</div>
			<fieldset className="management-actions editor-toolbar">
				<legend>Вставка Markdown</legend>
				{[
					["Заголовок", "\n## ", "", "Заголовок"],
					["Жирный", "**", "**", "текст"],
					["Курсив", "*", "*", "текст"],
					["Ссылка", "[", "](https://example.com)", "текст ссылки"],
					["Список", "\n- ", "", "элемент"],
					["Блок кода", "\n```\n", "\n```\n", "код"],
				].map(([label, before, after, fallback]) => (
					<button
						key={label}
						className="button secondary"
						type="button"
						disabled={busy}
						onClick={() => {
							source.current?.insert(before, after, fallback);
							setTab("Markdown");
						}}
					>
						{label}
					</button>
				))}
			</fieldset>
			<div
				className="management-tabs"
				role="tablist"
				aria-label="Вид материала"
			>
				{["Markdown", "Предпросмотр", "HTML"].map((name, i) => (
					<button
						key={name}
						id={`editor-tab-${i}`}
						aria-controls={`editor-pane-${i}`}
						type="button"
						role="tab"
						aria-selected={tab === name}
						tabIndex={tab === name ? 0 : -1}
						onClick={() => setTab(name)}
						onKeyDown={(event) => {
							const names = ["Markdown", "Предпросмотр", "HTML"];
							const next =
								event.key === "ArrowRight"
									? (i + 1) % 3
									: event.key === "ArrowLeft"
										? (i + 2) % 3
										: event.key === "Home"
											? 0
											: event.key === "End"
												? 2
												: -1;
							if (next >= 0) {
								event.preventDefault();
								setTab(names[next]);
								document.getElementById(`editor-tab-${next}`)?.focus();
							}
						}}
					>
						{name}
					</button>
				))}
			</div>
			<div
				className={`editor-panes ${split && tab === "Markdown" ? "editor-split" : ""}`}
			>
				<section
					id="editor-pane-0"
					role="tabpanel"
					aria-labelledby="editor-tab-0"
					className={`editor-markdown ${tab !== "Markdown" ? "editor-inactive" : ""}`}
				>
					<Source ref={source} initial={initial.markdown} onChange={setText} />
				</section>
				<section
					id="editor-pane-1"
					role="tabpanel"
					aria-labelledby="editor-tab-1"
					className={`editor-preview ${tab !== "Предпросмотр" ? "editor-inactive" : ""}`}
				>
					{previewError ? (
						<p role="alert">{previewError}</p>
					) : preview.text !== text ? (
						<p role="status">Обновляем предпросмотр…</p>
					) : (
						<SafeHtml className="lesson-article lesson-material" title="Предпросмотр материала" html={preview.html.replaceAll(
									"/api/assets/",
									"/api/backend/assets/",
								)} />
					)}
				</section>
				<section
					id="editor-pane-2"
					role="tabpanel"
					aria-labelledby="editor-tab-2"
					hidden={tab !== "HTML"}
				>
					<button
						className="button secondary"
						type="button"
						disabled={preview.text !== text || !!previewError}
						onClick={() => void copyHtml()}
					>
						Копировать HTML
					</button>
					{previewError ? (
						<p role="alert">{previewError}</p>
					) : preview.text !== text ? (
						<p role="status">Обновляем HTML…</p>
					) : (
						<Source
							language="html"
							initial={preview.html}
							onChange={() => {}}
						/>
					)}
				</section>
			</div>
		</dialog>
	);
}
