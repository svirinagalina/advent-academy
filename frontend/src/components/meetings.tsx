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
	type CallView,
	GroupCall,
	type Meeting,
	type MeetingPage,
} from "@/lib/group-call";
import { api, ClientError, type Profile } from "@/lib/learning";

const path = "/api/backend/meetings/scheduled";
const teacher = (profile: Profile) =>
	profile.roles.some((role) =>
		["ADMIN", "CONTENT_EDITOR", "INSTRUCTOR"].includes(role),
	);
const labels: Record<string, string> = {
	PLANNED: "Запланировано",
	ACTIVE: "Идёт занятие",
	FINISHED: "Завершено",
	CANCELLED: "Отменено",
	EXPIRED: "Не состоялось",
};
const date = (value: string) =>
	new Date(value).toLocaleString("ru-RU", {
		dateStyle: "medium",
		timeStyle: "short",
	});
const json = (method: string, body: unknown) => ({
	method,
	headers: { "Content-Type": "application/json" },
	body: JSON.stringify(body),
});
export function NearestMeeting({
	profile,
	onExpired,
}: {
	profile: Profile;
	onExpired: () => void;
}) {
	const [item, setItem] = useState<Meeting | null>(null);
	const [error, setError] = useState("");
	useEffect(() => {
		let active = true;
		api<MeetingPage>(`${path}${teacher(profile) ? "/teaching" : ""}?size=1`)
			.then((page) => {
				if (active) setItem(page.items[0] || null);
			})
			.catch((e) => {
				if (!active) return;
				if (e instanceof ClientError && e.status === 401) onExpired();
				else setError("Расписание временно недоступно");
			});
		return () => {
			active = false;
		};
	}, [profile, onExpired]);
	return (
		<section className="card meeting-card">
			<div className="meeting-info">
				<h3>{item ? item.title : "Онлайн-занятия"}</h3>
				<p>
					{error ||
						(item
							? `${date(item.startsAt)} · ${labels[item.status]}`
							: "Ближайших занятий пока нет")}
				</p>
			</div>
			<Link className="button secondary" href="/?view=meetings">
				Расписание
			</Link>
		</section>
	);
}
export function Meetings({
	profile,
	onExpired,
}: {
	profile: Profile;
	onExpired: () => void;
}) {
	const isTeacher = teacher(profile);
	const [tab, setTab] = useState("upcoming");
	const [page, setPage] = useState(0);
	const [data, setData] = useState<MeetingPage | null>(null);
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const [form, setForm] = useState<Meeting | "new" | null>(null);
	const [call, setCall] = useState<Meeting | null>(null);
	const epoch = useRef(0);
	const failure = useCallback(
		(e: unknown) => {
			if (e instanceof ClientError && e.status === 401) onExpired();
			else
				setError(
					e instanceof Error ? e.message : "Не удалось загрузить расписание",
				);
		},
		[onExpired],
	);
	const load = useCallback(async () => {
		const current = ++epoch.current;
		setBusy(true);
		setError("");
		try {
			const result = await api<MeetingPage>(
				`${path}${isTeacher ? "/teaching" : ""}?tab=${tab}&page=${page}`,
			);
			if (epoch.current === current) setData(result);
		} catch (e) {
			if (epoch.current === current) failure(e);
		} finally {
			if (epoch.current === current) setBusy(false);
		}
	}, [isTeacher, tab, page, failure]);
	useEffect(() => {
		void load();
		const interval = setInterval(() => void load(), 15000);
		return () => {
			clearInterval(interval);
			epoch.current++;
		};
	}, [load]);
	async function action(item: Meeting, command: string) {
		setBusy(true);
		setError("");
		try {
			await api(
				`${path}/${item.id}/${command}`,
				json("POST", { version: item.version }),
			);
			await load();
		} catch (e) {
			failure(e);
			setBusy(false);
		}
	}
	return (
		<>
			<div className="page-heading">
				<div>
					<h1>Онлайн-занятия</h1>
					<p>Групповые встречи с преподавателем</p>
				</div>
				{isTeacher && (
					<button
						type="button"
						className="button primary"
						onClick={() => setForm("new")}
					>
						Запланировать занятие
					</button>
				)}
			</div>
			<div className="filters">
				{[
					["upcoming", "Предстоящие"],
					["past", "Завершённые"],
				].map(([id, label]) => (
					<button
						type="button"
						className={tab === id ? "filter selected" : "filter"}
						aria-pressed={tab === id}
						key={id}
						onClick={() => {
							setTab(id);
							setPage(0);
							setData(null);
						}}
					>
						{label}
					</button>
				))}
				<button
					className="button secondary"
					type="button"
					disabled={busy}
					onClick={() => void load()}
				>
					Обновить
				</button>
			</div>
			{error && (
				<p role="alert" className="field-error">
					{error}
				</p>
			)}
			{busy && <p role="status">Обновляем расписание…</p>}
			{!busy && !error && !data?.items.length && (
				<section className="card empty-state">
					<h2>Занятий пока нет</h2>
					<p>
						{isTeacher
							? "Выберите курс и учеников, чтобы запланировать встречу."
							: "Здесь появятся встречи, на которые вас пригласит преподаватель."}
					</p>
				</section>
			)}
			{data?.items.map((item) => (
				<section className="card scheduled-card" key={item.id}>
					<div>
						<span className="eyebrow">{item.courseTitle}</span>
						<h2>{item.title}</h2>
						<p>
							{date(item.startsAt)} —{" "}
							{new Date(item.endsAt).toLocaleTimeString("ru-RU", {
								hour: "2-digit",
								minute: "2-digit",
							})}
						</p>
						<p>
							{labels[item.status]} · До {item.participantCount} участников
						</p>
					</div>
					<div className="meeting-actions">
						{item.canJoin && (
							<button
								className="button primary"
								type="button"
								disabled={Boolean(call)}
								onClick={() => setCall(item)}
							>
								Подключиться
							</button>
						)}
						{item.canStart && (
							<button
								className="button primary"
								type="button"
								disabled={busy}
								onClick={() => void action(item, "start")}
							>
								Начать занятие
							</button>
						)}
						{item.canManage && item.status === "PLANNED" && (
							<button
								className="button secondary"
								type="button"
								onClick={() => setForm(item)}
							>
								Перенести
							</button>
						)}
						{item.canManage && item.status === "ACTIVE" && (
							<button
								className="button secondary"
								type="button"
								disabled={busy}
								onClick={() => void action(item, "finish")}
							>
								Завершить
							</button>
						)}
						{item.canManage && ["PLANNED", "ACTIVE"].includes(item.status) && (
							<button
								className="button secondary"
								type="button"
								disabled={busy}
								onClick={() => void action(item, "cancel")}
							>
								Отменить
							</button>
						)}
					</div>
				</section>
			))}
			{data && data.total > data.size && (
				<div className="meeting-actions">
					<button
						type="button"
						className="button secondary"
						disabled={page === 0 || busy}
						onClick={() => setPage(page - 1)}
					>
						Назад
					</button>
					<span>Страница {page + 1}</span>
					<button
						type="button"
						className="button secondary"
						disabled={(page + 1) * data.size >= data.total || busy}
						onClick={() => setPage(page + 1)}
					>
						Далее
					</button>
				</div>
			)}
			{form && (
				<MeetingForm
					meeting={form === "new" ? null : form}
					onClose={() => setForm(null)}
					onSaved={async () => {
						setForm(null);
						await load();
					}}
					onExpired={onExpired}
				/>
			)}
			{call && (
				<CallPanel
					meeting={call}
					onClose={() => {
						setCall(null);
						void load();
					}}
				/>
			)}
		</>
	);
}
type Student = {
	id: string;
	firstName: string;
	lastName: string;
	email: string;
};
const localDate = (value: string) => {
	const d = new Date(value);
	return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
		.toISOString()
		.slice(0, 16);
};
function MeetingForm({
	meeting,
	onClose,
	onSaved,
	onExpired,
}: {
	meeting: Meeting | null;
	onClose: () => void;
	onSaved: () => Promise<void>;
	onExpired: () => void;
}) {
	const [courses, setCourses] = useState<{ id: string; title: string }[]>([]);
	const [course, setCourse] = useState(meeting?.courseId || "");
	const [students, setStudents] = useState<Student[]>([]);
	const [selected, setSelected] = useState<string[]>(
		meeting?.participants || [],
	);
	const [title, setTitle] = useState(meeting?.title || "");
	const [start, setStart] = useState(
		localDate(meeting?.startsAt || new Date(Date.now() + 60000).toISOString()),
	);
	const [end, setEnd] = useState(
		localDate(meeting?.endsAt || new Date(Date.now() + 3660000).toISOString()),
	);
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	const [rosterReady, setRosterReady] = useState(false);
	const requestId = useRef(crypto.randomUUID());
	const draft = useRef("");
	const dialog = useRef<HTMLDialogElement>(null);
	useEffect(() => {
		const node = dialog.current;
		const previous = document.activeElement;
		node?.showModal();
		return () => {
			node?.close();
			if (previous instanceof HTMLElement && previous.isConnected)
				previous.focus();
		};
	}, []);
	useEffect(() => {
		let active = true;
		api<{ id: string; title: string }[]>("/api/backend/teaching/courses")
			.then((list) => {
				if (active) {
					setCourses(list);
					if (!meeting) setCourse(list[0]?.id || "");
				}
			})
			.catch((e) => {
				if (active) {
					if (e instanceof ClientError && e.status === 401) onExpired();
					else setError(e.message);
				}
			});
		return () => {
			active = false;
		};
	}, [meeting, onExpired]);
	useEffect(() => {
		let active = true;
		setStudents([]);
		setRosterReady(false);
		if (course)
			api<Student[]>(`/api/backend/users/teaching/courses/${course}/students`)
				.then((list) => {
					if (active) {
						setStudents(list);
						setRosterReady(true);
					}
				})
				.catch((e) => {
					if (active) {
						if (e instanceof ClientError && e.status === 401) onExpired();
						else setError(e.message);
					}
				});
		return () => {
			active = false;
		};
	}, [course, onExpired]);
	async function save(event: FormEvent) {
		event.preventDefault();
		setBusy(true);
		setError("");
		try {
			const body = {
				title,
				courseId: course,
				startsAt: new Date(start).toISOString(),
				endsAt: new Date(end).toISOString(),
				participants: selected,
			};
			const content = JSON.stringify(body);
			if (draft.current && draft.current !== content)
				requestId.current = crypto.randomUUID();
			draft.current = content;
			await api(
				meeting ? `${path}/${meeting.id}` : path,
				json(
					meeting ? "PUT" : "POST",
					meeting
						? { ...body, version: meeting.version }
						: { ...body, requestId: requestId.current },
				),
			);
			await onSaved();
		} catch (e) {
			if (e instanceof ClientError && e.status === 401) onExpired();
			else
				setError(
					e instanceof Error ? e.message : "Не удалось сохранить занятие",
				);
		} finally {
			setBusy(false);
		}
	}
	return (
		<dialog
			ref={dialog}
			className="modal meeting-editor"
			aria-labelledby="meeting-form-title"
			onCancel={(event) => {
				event.preventDefault();
				if (!busy) onClose();
			}}
		>
			<section>
				<h2 id="meeting-form-title">
					{meeting ? "Перенести занятие" : "Новое занятие"}
				</h2>
				<form onSubmit={save} className="meeting-form" aria-busy={busy}>
					<label>
						Курс
						<select
							required
							value={course}
							disabled={Boolean(meeting) || busy}
							onChange={(e) => {
								setCourse(e.target.value);
								setSelected([]);
								setError("");
							}}
						>
							<option value="">Выберите курс</option>
							{courses.map((c) => (
								<option key={c.id} value={c.id}>
									{c.title}
								</option>
							))}
						</select>
					</label>
					<label>
						Название
						<input
							required
							maxLength={255}
							value={title}
							onChange={(e) => setTitle(e.target.value)}
						/>
					</label>
					<label>
						Начало
						<input
							required
							type="datetime-local"
							value={start}
							onChange={(e) => setStart(e.target.value)}
						/>
					</label>
					<label>
						Окончание
						<input
							required
							type="datetime-local"
							value={end}
							onChange={(e) => setEnd(e.target.value)}
						/>
					</label>
					<p>
						Время в вашем часовом поясе:{" "}
						{Intl.DateTimeFormat().resolvedOptions().timeZone}
					</p>
					<fieldset>
						<legend>Ученики ({selected.length}/7)</legend>
						{!rosterReady ? (
							<p role="status">Загружаем учеников…</p>
						) : !students.length ? (
							<p>Курсу пока не назначены ученики.</p>
						) : (
							students.map((student) => (
								<label className="student-choice" key={student.id}>
									<input
										type="checkbox"
										checked={selected.includes(student.id)}
										disabled={
											busy ||
											(!selected.includes(student.id) && selected.length >= 7)
										}
										onChange={(e) =>
											setSelected((old) =>
												e.target.checked
													? [...old, student.id]
													: old.filter((id) => id !== student.id),
											)
										}
									/>
									<span>
										{[student.firstName, student.lastName]
											.filter(Boolean)
											.join(" ") || student.email}
										<small>{student.email}</small>
									</span>
								</label>
							))
						)}
					</fieldset>
					{error && (
						<p role="alert" className="field-error">
							{error}
						</p>
					)}
					<div className="meeting-actions">
						<button
							className="button primary"
							type="submit"
							disabled={busy || !rosterReady || !selected.length}
						>
							{busy ? "Сохраняем…" : "Сохранить"}
						</button>
						<button
							className="button secondary"
							type="button"
							disabled={busy}
							onClick={onClose}
						>
							Закрыть
						</button>
					</div>
				</form>
			</section>
		</dialog>
	);
}
function StreamVideo({
	stream,
	local,
}: {
	stream: MediaStream;
	local?: boolean;
}) {
	const video = useRef<HTMLVideoElement>(null);
	useEffect(() => {
		const node = video.current;
		if (node) {
			node.srcObject = stream;
			void node.play().catch(() => {});
		}
		return () => {
			if (node) node.srcObject = null;
		};
	}, [stream]);
	return (
		<video
			ref={video}
			autoPlay
			playsInline
			muted={local}
			aria-label={local ? "Ваше видео" : "Видео участника"}
		/>
	);
}
function CallPanel({
	meeting,
	onClose,
}: {
	meeting: Meeting;
	onClose: () => void;
}) {
	const [attempt, setAttempt] = useState(0);
	return (
		<CallSession
			key={attempt}
			meeting={meeting}
			onClose={onClose}
			onReconnect={() => setAttempt(attempt + 1)}
		/>
	);
}
function CallSession({
	meeting,
	onClose,
	onReconnect,
}: {
	meeting: Meeting;
	onClose: () => void;
	onReconnect: () => void;
}) {
	const client = useRef<GroupCall | null>(null);
	const [state, setState] = useState<CallView>({
		local: null,
		peers: [],
		status: "Подключаемся…",
		connected: false,
	});
	const [audio, setAudio] = useState(true);
	const [video, setVideo] = useState(true);
	useEffect(() => {
		const call = new GroupCall(setState);
		client.current = call;
		setAudio(true);
		setVideo(true);
		void call.connect(meeting.id);
		return () => {
			call.stop();
			client.current = null;
		};
	}, [meeting.id]);
	return (
		<section className="card call-panel" aria-label="Групповое занятие">
			<h2>{meeting.title}</h2>
			<p role="status">{state.status}</p>
			<div className="call-grid">
				{state.local && (
					<div>
						<StreamVideo stream={state.local} local />
						<span>Вы</span>
					</div>
				)}
				{state.peers.map((peer, index) => (
					<div key={peer.id}>
						<StreamVideo stream={peer.stream} />
						<span>Участник {index + 1}</span>
					</div>
				))}
			</div>
			<div className="meeting-actions">
				{state.local && (
					<>
						<button
							className="button secondary"
							type="button"
							aria-pressed={audio}
							onClick={() => setAudio(client.current?.toggle("audio") ?? false)}
						>
							{audio ? "Выключить микрофон" : "Включить микрофон"}
						</button>
						<button
							className="button secondary"
							type="button"
							aria-pressed={video}
							onClick={() => setVideo(client.current?.toggle("video") ?? false)}
						>
							{video ? "Выключить камеру" : "Включить камеру"}
						</button>
					</>
				)}
				{(!state.local || state.connected) && (
					<button
						className="button primary"
						type="button"
						onClick={onReconnect}
					>
						Переподключиться
					</button>
				)}
				<button className="button secondary" type="button" onClick={onClose}>
					Выйти из занятия
				</button>
			</div>
		</section>
	);
}
