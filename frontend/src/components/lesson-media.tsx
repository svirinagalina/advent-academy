"use client";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";

type Asset = { filename: string; url: string };

export function LessonVideo({ asset }: { asset: Asset }) {
	const [attempt, setAttempt] = useState(0);
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	return (
		<section
			className="lesson-video-panel"
			aria-label={`Видео: ${asset.filename}`}
			aria-busy={loading}
		>
			{loading && <p role="status">Загружаем видео…</p>}
			<video
				key={attempt}
				className="lesson-video"
				controls
				preload="metadata"
				hidden={failed}
				aria-label={asset.filename}
				src={asset.url.replace("/api/assets/", "/api/backend/assets/")}
				onLoadedMetadata={() => {
					setLoading(false);
					setFailed(false);
				}}
				onWaiting={() => setLoading(true)}
				onCanPlay={() => setLoading(false)}
				onError={() => {
					setLoading(false);
					setFailed(true);
				}}
			>
				<track kind="captions" />
			</video>
			{failed && (
				<div className="feedback error" role="alert">
					<p>
						Не удалось загрузить видео. Проверьте соединение и попробуйте ещё
						раз.
					</p>
					<button
						type="button"
						className="button secondary"
						onClick={() => {
							setFailed(false);
							setLoading(true);
							setAttempt((value) => value + 1);
						}}
					>
						Повторить загрузку видео
					</button>
				</div>
			)}
		</section>
	);
}

export function DownloadAttachment({
	asset,
	icon,
}: {
	asset: Asset;
	icon?: ReactNode;
}) {
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const pending = useRef<AbortController | null>(null);
	const errorId = useId();
	useEffect(() => () => pending.current?.abort(), []);
	async function download() {
		if (pending.current) return;
		const controller = new AbortController();
		pending.current = controller;
		setBusy(true);
		setError("");
		setNotice("");
		try {
			const response = await fetch(
				asset.url.replace("/api/assets/", "/api/backend/assets/"),
				{
					cache: "no-store",
					signal: controller.signal,
				},
			);
			if (!response.ok) throw new Error("Download failed");
			const blob = await response.blob();
			if (controller.signal.aborted) return;
			const url = URL.createObjectURL(blob);
			const link = document.createElement("a");
			link.href = url;
			link.download = asset.filename;
			document.body.append(link);
			link.click();
			link.remove();
			setTimeout(() => URL.revokeObjectURL(url), 1000);
			setNotice("Скачивание началось.");
		} catch {
			if (!controller.signal.aborted)
				setError(
					"Не удалось скачать файл. Проверьте соединение и попробуйте ещё раз.",
				);
		} finally {
			if (!controller.signal.aborted) {
				setBusy(false);
				pending.current = null;
			}
		}
	}
	return (
		<div className="lesson-attachment" aria-busy={busy}>
			<button
				type="button"
				className="card attachment attachment-download"
				disabled={busy}
				aria-label={`${error ? "Повторить скачивание" : "Скачать"} ${asset.filename}`}
				aria-describedby={error ? errorId : undefined}
				onClick={() => void download()}
			>
				{icon}
				<span>{asset.filename}</span>
				<span className="text-link">
					{busy ? "Скачиваем…" : error ? "Повторить скачивание" : "Скачать"}
				</span>
			</button>
			{error && (
				<p id={errorId} className="feedback error" role="alert">
					{error}
				</p>
			)}
			{notice && <p role="status">{notice}</p>}
		</div>
	);
}
