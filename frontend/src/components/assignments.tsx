"use client";
import Link from "next/link";
import {
	type FormEvent,
	useCallback,
	useEffect,
	useRef,
	useState,
} from "react";
import {
	api,
	ClientError,
	type AssignmentSummary,
	type AssignmentView,
	type Attempt,
	type ReviewItem,
	type ReviewPage,
} from "@/lib/learning";

const labels = {
	draft: "Не отправлено",
	review: "На проверке",
	changes: "Нужны исправления",
	accepted: "Принято",
};
function errorText(e: unknown) {
	return e instanceof Error ? e.message : "Не удалось выполнить запрос";
}
function Badge({ status }: { status: keyof typeof labels }) {
	return (
		<span
			className={`badge ${status === "accepted" ? "success" : status === "changes" ? "warning" : ""}`}
		>
			{labels[status]}
		</span>
	);
}
function date(value: string) {
	return new Date(value).toLocaleString("ru-RU");
}
function History({ attempts }: { attempts: Attempt[] }) {
	if (!attempts.length) return null;
	return (
		<details className="attempt-history">
			<summary>История попыток ({attempts.length})</summary>
			{[...attempts].reverse().map((attempt) => (
				<article className="attempt-entry" key={attempt.id}>
					<h3>
						Попытка {attempt.attempt} · <Badge status={attempt.status} />
					</h3>
					<p className="muted">{date(attempt.submittedAt)}</p>
					<a
						className="text-link solution-link"
						href={attempt.url}
						target="_blank"
						rel="noopener noreferrer"
					>
						{attempt.url}
					</a>
					{attempt.comment && (
						<p className="preserve-text">{attempt.comment}</p>
					)}
					{attempt.feedback && (
						<div className="feedback preserve-text">
							<strong>Ответ преподавателя</strong>
							<p>{attempt.feedback}</p>
							<p className="muted">
								{attempt.reviewedAt && date(attempt.reviewedAt)}
							</p>
						</div>
					)}
				</article>
			))}
		</details>
	);
}
type Draft = { url: string; comment: string; requestId: string };
export function AssignmentPanel({
	userId,
	courseId,
	lessonId,
	onChanged,
	onExpired,
	focusOnOpen = false,
	navigationKey = "",
}: {
	userId: string;
	courseId: string;
	lessonId: string;
	onChanged: () => Promise<void>;
	onExpired: () => void;
	focusOnOpen?: boolean;
	navigationKey?: string;
}) {
	const path = `/api/backend/learning/courses/${courseId}/lessons/${lessonId}`;
	const storage = `advent-assignment-draft/${userId}/${courseId}/${lessonId}`;
	const [data, setData] = useState<AssignmentView | null>(null);
	const [draft, setDraft] = useState<Draft>({
		url: "",
		comment: "",
		requestId: "",
	});
	const [ready, setReady] = useState(false);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [storageError, setStorageError] = useState(false);
	const [busy, setBusy] = useState(false);
	const [notice, setNotice] = useState("");
	const [urlError, setUrlError] = useState("");
	const [commentError, setCommentError] = useState("");
	const heading = useRef<HTMLHeadingElement>(null);
	const focused = useRef<string | null>(null);
	useEffect(() => {
		if (!focusOnOpen || focused.current === navigationKey || loading || !data)
			return;
		const frame = requestAnimationFrame(() => {
			heading.current?.scrollIntoView({ block: "start", behavior: "instant" });
			heading.current?.focus({ preventScroll: true });
			focused.current = navigationKey;
		});
		return () => cancelAnimationFrame(frame);
	}, [data, loading, focusOnOpen, navigationKey]);
	useEffect(() => {
		if (!busy && (urlError || commentError))
			document
				.getElementById(urlError ? "live-solution" : "live-comment")
				?.focus();
	}, [busy, urlError, commentError]);
	const alive = useRef(true);
	const reload = useCallback(async () => {
		setLoading(true);
		setError("");
		setNotice("");
		try {
			const result = await api<AssignmentView>(`${path}/assignment`);
			if (alive.current) {
				setData(result);
				const latest = result.attempts.at(-1);
				if (latest?.status === "changes") {
					// A server-confirmed result ends the previous idempotency cycle. A fix may
					// keep the same repository URL, including after a lost response/reload.
					setDraft((old) => ({
						url: old.url || latest.url,
						comment: old.url ? old.comment : latest.comment,
						requestId: crypto.randomUUID(),
					}));
				} else if (latest) {
					setDraft({ url: "", comment: "", requestId: crypto.randomUUID() });
				}
			}
		} catch (e) {
			if (alive.current) {
				if (e instanceof ClientError && e.status === 401) onExpired();
				else setError(errorText(e));
			}
		} finally {
			if (alive.current) setLoading(false);
		}
	}, [path, onExpired]);
	useEffect(() => {
		alive.current = true;
		let initial: Draft = {
			url: "",
			comment: "",
			requestId: crypto.randomUUID(),
		};
		try {
			const raw = localStorage.getItem(storage);
			if (raw) {
				const value = JSON.parse(raw);
				if (
					typeof value.url === "string" &&
					typeof value.comment === "string" &&
					typeof value.requestId === "string" &&
					/^[0-9a-f-]{36}$/i.test(value.requestId)
				)
					initial = value;
			}
		} catch {
			setStorageError(true);
		}
		setDraft(initial);
		setReady(true);
		void reload();
		return () => {
			alive.current = false;
		};
	}, [storage, reload]);
	useEffect(() => {
		if (!ready) return;
		try {
			localStorage.setItem(storage, JSON.stringify(draft));
		} catch {
			setStorageError(true);
		}
	}, [draft, ready, storage]);
	async function refresh() {
		if (loading || busy) return;
		await reload();
		try {
			await onChanged();
		} catch (e) {
			if (alive.current) setError(errorText(e));
		}
	}
	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (busy) return;
		const url = draft.url.trim();
		setUrlError("");
		setCommentError("");
		setError("");
		if (draft.comment.length > 4000) {
			setCommentError("Комментарий должен быть не длиннее 4000 символов.");
			document.getElementById("live-comment")?.focus();
			return;
		}
		try {
			const value = new URL(url);
			if (
				url.length > 2048 ||
				value.protocol !== "https:" ||
				value.username ||
				value.password
			)
				throw new Error();
		} catch {
			setUrlError(
				!url
					? "Укажите ссылку на решение."
					: url.length > 2048
						? "Ссылка должна быть не длиннее 2048 символов."
						: "Укажите HTTPS-ссылку без логина и пароля.",
			);
			document.getElementById("live-solution")?.focus();
			return;
		}
		setBusy(true);
		setError("");
		setNotice("");
		try {
			const result = await api<AssignmentView>(`${path}/submissions`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ ...draft, url }),
			});
			if (alive.current) {
				setData(result);
				setDraft({ url: "", comment: "", requestId: crypto.randomUUID() });
				setNotice(
					result.attempts.at(-1)?.status === "changes"
						? "Предыдущая отправка уже проверена. Отправьте исправленное решение."
						: result.attempts.at(-1)?.status === "accepted"
							? "Задание уже принято."
							: "Решение отправлено на проверку.",
				);
			}
			// Submission is already committed. A later summary failure must not masquerade as a failed send.
			try {
				await onChanged();
			} catch {
				if (alive.current)
					setNotice(
						"Решение отправлено. Обновите список заданий, чтобы увидеть новый статус.",
					);
			}
		} catch (e) {
			if (alive.current) {
				if (e instanceof ClientError && e.status === 401) onExpired();
				else if (e instanceof ClientError && Object.keys(e.fields).length) {
					if (e.fields.url)
						setUrlError(
							"Укажите корректную HTTPS-ссылку длиной до 2048 символов.",
						);
					if (e.fields.comment)
						setCommentError(
							"Комментарий должен быть не длиннее 4000 символов.",
						);
					if (
						Object.keys(e.fields).some(
							(key) => !["url", "comment"].includes(key),
						)
					)
						setError(errorText(e));
				} else setError(errorText(e));
			}
		} finally {
			if (alive.current) setBusy(false);
		}
	}
	const latest = data?.attempts.at(-1);
	return (
		<section className="card assignment-form" aria-busy={loading || busy}>
			<div className="assignment-heading">
				<div>
					<span className="eyebrow">Практическое задание</span>
					<h2 ref={heading} tabIndex={-1}>
						{data?.title || "Обязательное задание"}
					</h2>
				</div>
				<Badge status={latest?.status || "draft"} />
			</div>
			{loading && <p role="status">Загружаем задание…</p>}
			{error && (
				<p className="feedback error" role="alert">
					{error}
				</p>
			)}
			{notice && (
				<p className="feedback" role="status">
					{notice}
				</p>
			)}
			{storageError && (
				<p className="muted">
					Черновик сохранится только до закрытия страницы.
				</p>
			)}
			{data && (
				<>
					<p className="preserve-text">{data.description}</p>
					{latest?.status === "changes" && (
						<div className="feedback warning">
							<strong>Нужны исправления</strong>
							<p className="preserve-text">{latest.feedback}</p>
						</div>
					)}
					{latest?.status === "accepted" ? (
						<div className="feedback success">
							<strong>Задание принято. Урок завершён.</strong>
							{latest.feedback && (
								<p className="preserve-text">{latest.feedback}</p>
							)}
						</div>
					) : latest?.status === "review" ? (
						<p className="feedback">
							Решение отправлено. Ожидайте результата проверки.
						</p>
					) : (
						<form onSubmit={submit} noValidate aria-busy={busy}>
							<label htmlFor="live-solution">Ссылка на решение</label>
							<input
								id="live-solution"
								type="url"
								required
								maxLength={2048}
								aria-invalid={!!urlError}
								aria-describedby={urlError ? "live-solution-error" : undefined}
								value={draft.url}
								disabled={busy || !ready || loading}
								onChange={(e) => {
									setUrlError("");
									setDraft((old) => ({
										...old,
										url: e.target.value,
										requestId: crypto.randomUUID(),
									}));
								}}
								placeholder="https://github.com/…"
							/>
							{urlError && (
								<p
									id="live-solution-error"
									className="feedback error"
									role="alert"
								>
									{urlError}
								</p>
							)}
							<label htmlFor="live-comment">Комментарий к решению</label>
							<textarea
								id="live-comment"
								maxLength={4000}
								aria-invalid={!!commentError}
								aria-describedby={
									commentError ? "live-comment-error" : undefined
								}
								value={draft.comment}
								disabled={busy}
								onChange={(e) => {
									setCommentError("");
									setDraft((old) => ({
										...old,
										comment: e.target.value,
										requestId: crypto.randomUUID(),
									}));
								}}
							/>
							{commentError && (
								<p
									id="live-comment-error"
									className="feedback error"
									role="alert"
								>
									{commentError}
								</p>
							)}
							<button
								type="submit"
								className="button primary full"
								disabled={busy || !ready || loading}
							>
								{busy
									? "Отправляем…"
									: latest?.status === "changes"
										? "Отправить повторно"
										: "Отправить на проверку"}
							</button>
						</form>
					)}
					<History attempts={data.attempts} />
				</>
			)}
			<button
				type="button"
				className="button secondary"
				aria-disabled={loading || busy}
				onClick={() => void refresh()}
			>
				{loading
					? "Обновляем…"
					: error && !data
						? "Повторить"
						: "Обновить статус"}
			</button>
		</section>
	);
}
export function AssignmentList({
	items,
	loading,
	error,
	onRefresh,
	attention = false,
}: {
	items: AssignmentSummary[];
	loading: boolean;
	error: string;
	onRefresh: () => Promise<void>;
	attention?: boolean;
}) {
	const [filter, setFilter] = useState("all");
	const rows = items.filter((item) =>
		attention
			? item.available && ["draft", "changes"].includes(item.status)
			: filter === "all" || item.status === filter,
	);
	return (
		<section className="attention-section">
			<div className="section-heading">
				<h2>{attention ? "Требует внимания" : "Задания"}</h2>
				<button
					type="button"
					className="button secondary"
					disabled={loading}
					onClick={() => void onRefresh()}
				>
					Обновить задания
				</button>
			</div>
			{!attention && (
				<fieldset className="filters" aria-label="Фильтр заданий">
					{["all", ...Object.keys(labels)].map((value) => (
						<button
							key={value}
							className={filter === value ? "filter selected" : "filter"}
							type="button"
							aria-pressed={filter === value}
							onClick={() => setFilter(value)}
						>
							{value === "all" ? "Все" : labels[value as keyof typeof labels]}
						</button>
					))}
				</fieldset>
			)}
			{loading && <p role="status">Загружаем задания…</p>}
			{error && (
				<p className="feedback error" role="alert">
					{error}
				</p>
			)}
			{!loading && !error && !rows.length && (
				<section className="card empty-state">
					<p>
						{attention
							? "Сейчас нет заданий, требующих внимания."
							: "Нет заданий с выбранным статусом."}
					</p>
				</section>
			)}
			{rows.map((item) => (
				<article
					key={`${item.courseId}/${item.lessonId}`}
					className="card assignment-row"
				>
					<div>
						<h3>{item.title}</h3>
						<p className="muted">
							{item.courseTitle} · {item.lessonTitle}
						</p>
						<Badge status={item.status} />
						{!item.configured && (
							<p className="muted">Условие пока не добавлено.</p>
						)}
						{!item.available && (
							<p className="muted">Откроется после предыдущего урока.</p>
						)}
					</div>
					<Link
						className="button secondary"
						href={`/?view=lesson&course=${item.courseId}&lesson=${item.lessonId}&assignment=1`}
						scroll={false}
					>
						Открыть задание
					</Link>
				</article>
			))}
		</section>
	);
}
export function ReviewPanel({ onExpired }: { onExpired: () => void }) {
	const [status, setStatus] = useState("review");
	const [page, setPage] = useState(0);
	const [data, setData] = useState<ReviewPage | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const generation = useRef(0);
	const reload = useCallback(async () => {
		const current = ++generation.current;
		setLoading(true);
		setError("");
		try {
			const result = await api<ReviewPage>(
				`/api/backend/teaching/submissions?status=${status}&page=${page}&size=20`,
			);
			if (generation.current === current) setData(result);
		} catch (e) {
			if (generation.current === current) {
				if (e instanceof ClientError && e.status === 401) onExpired();
				else setError(errorText(e));
			}
		} finally {
			if (generation.current === current) setLoading(false);
		}
	}, [status, page, onExpired]);
	useEffect(() => {
		void reload();
		return () => {
			generation.current++;
		};
	}, [reload]);
	return (
		<section>
			<div className="section-heading">
				<h1>Проверка заданий</h1>
				<button
					type="button"
					className="button secondary"
					disabled={loading}
					onClick={() => void reload()}
				>
					Обновить очередь
				</button>
			</div>
			<fieldset className="filters" aria-label="Статус проверки">
				{(["review", "changes", "accepted"] as const).map((value) => (
					<button
						key={value}
						type="button"
						className={status === value ? "filter selected" : "filter"}
						aria-pressed={status === value}
						onClick={() => {
							setStatus(value);
							setPage(0);
							setData(null);
						}}
					>
						{labels[value]}
					</button>
				))}
			</fieldset>
			{loading && <p role="status">Загружаем очередь…</p>}
			{error && (
				<p className="feedback error" role="alert">
					{error}
				</p>
			)}
			{!loading && !error && data?.items.length === 0 && (
				<section className="card empty-state">
					<p>Заданий с этим статусом нет.</p>
				</section>
			)}
			{!loading &&
				!error &&
				data?.items.map((item) => (
					<ReviewCard
						key={`${item.submission.id}/${item.submission.version}`}
						item={item}
						onChanged={reload}
						onExpired={onExpired}
					/>
				))}
			{data && data.total > 20 && (
				<nav className="lesson-pagination" aria-label="Страницы проверки">
					<button
						className="button secondary"
						type="button"
						disabled={page === 0 || loading}
						onClick={() => setPage((old) => old - 1)}
					>
						Назад
					</button>
					<span>Страница {page + 1}</span>
					<button
						className="button secondary"
						type="button"
						disabled={(page + 1) * 20 >= data.total || loading}
						onClick={() => setPage((old) => old + 1)}
					>
						Далее
					</button>
				</nav>
			)}
		</section>
	);
}
function ReviewCard({
	item,
	onChanged,
	onExpired,
}: {
	item: ReviewItem;
	onChanged: () => Promise<void>;
	onExpired: () => void;
}) {
	const [feedback, setFeedback] = useState(item.submission.feedback || "");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const [done, setDone] = useState("");
	async function decide(status: "changes" | "accepted") {
		if (busy || done) return;
		if (status === "changes" && !feedback.trim()) {
			setError("Опишите, что нужно исправить.");
			return;
		}
		setBusy(true);
		setError("");
		try {
			await api<ReviewItem>(
				`/api/backend/teaching/submissions/${item.submission.id}/review`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({
						status,
						version: item.submission.version,
						feedback,
					}),
				},
			);
			setDone(
				status === "accepted"
					? "Задание принято"
					: "Задание возвращено на доработку",
			);
			await onChanged();
		} catch (e) {
			if (e instanceof ClientError && e.status === 401) onExpired();
			else setError(errorText(e));
		} finally {
			setBusy(false);
		}
	}
	const id = `feedback-${item.submission.id}`;
	return (
		<article className="card review-card" aria-busy={busy}>
			<div className="assignment-heading">
				<div>
					<h2>{item.title}</h2>
					<p className="muted">
						{item.courseTitle} · {item.lessonTitle}
					</p>
				</div>
				<Badge status={item.submission.status} />
			</div>
			<p className="muted">
				Попытка {item.submission.attempt} · {date(item.submission.submittedAt)}
			</p>
			<details>
				<summary>Условие задания</summary>
				<p className="preserve-text">{item.description}</p>
			</details>
			<a
				className="text-link solution-link"
				href={item.submission.url}
				target="_blank"
				rel="noopener noreferrer"
			>
				Открыть решение
			</a>
			{item.submission.comment && (
				<p className="preserve-text">{item.submission.comment}</p>
			)}
			{error && (
				<p role="alert" className="feedback error">
					{error}
				</p>
			)}
			{done && (
				<p role="status" className="feedback success">
					{done}
				</p>
			)}
			{item.submission.status === "review" && !done ? (
				<>
					<label htmlFor={id}>Комментарий преподавателя</label>
					<textarea
						id={id}
						maxLength={4000}
						disabled={busy}
						value={feedback}
						onChange={(e) => setFeedback(e.target.value)}
					/>
					<div className="review-actions">
						<button
							className="button secondary"
							type="button"
							disabled={busy}
							onClick={() => void decide("changes")}
						>
							Вернуть на доработку
						</button>
						<button
							className="button primary"
							type="button"
							disabled={busy}
							onClick={() => void decide("accepted")}
						>
							Принять задание
						</button>
					</div>
				</>
			) : (
				item.submission.feedback && (
					<div className="feedback preserve-text">
						<strong>Ответ преподавателя</strong>
						<p>{item.submission.feedback}</p>
					</div>
				)
			)}
		</article>
	);
}
