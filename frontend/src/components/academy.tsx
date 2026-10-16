"use client";
import { SafeHtml } from "@/components/safe-html";

import NextLink from "next/link";
import { ThemeToggle } from "@/components/theme";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
	useEffect,
	useRef,
	useState,
	type FormEvent,
	type ReactNode,
	type ComponentProps,
} from "react";
import {
	completedCount,
	courseHref,
	courses as demoCourses,
	currentLesson,
	initialState,
	isAvailable,
	lessonHref,
	lessonKey,
	lessons,
	parseState,
	storageKey,
	type Course,
	type DemoState,
	type SubmissionStatus,
} from "@/lib/demo";

import type { LiveData } from "@/lib/learning";

function Link(props: ComponentProps<typeof NextLink>) {
	const path = usePathname();
	const href =
		path === "/demo" &&
		typeof props.href === "string" &&
		props.href.startsWith("/")
			? `/demo${props.href.slice(1)}`
			: props.href === "/" ? "/cabinet" : props.href;
	return <NextLink {...props} href={href} />;
}

type IconName =
	| "home"
	| "book"
	| "file"
	| "calendar"
	| "arrow"
	| "back"
	| "chevron"
	| "check"
	| "lock"
	| "clock"
	| "close"
	| "alert"
	| "download"
	| "play"
	| "refresh"
	| "external";
function Icon({
	name,
	className = "",
}: {
	name: IconName;
	className?: string;
}) {
	const paths: Record<IconName, ReactNode> = {
		home: (
			<>
				<path d="m3 10 9-7 9 7v11h-6v-7H9v7H3Z" />
			</>
		),
		book: (
			<>
				<path d="M12 5v16M3 3h4a5 5 0 0 1 5 2 5 5 0 0 1 5-2h4v16h-4a5 5 0 0 0-5 2 5 5 0 0 0-5-2H3Z" />
			</>
		),
		file: (
			<>
				<path d="M5 3h10l4 4v14H5ZM14 3v5h5M8 12h8M8 16h6" />
			</>
		),
		calendar: (
			<>
				<rect x="3" y="5" width="18" height="16" rx="2" />
				<path d="M7 2v6M17 2v6M3 11h18M7 15h2M13 15h2" />
			</>
		),
		arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
		back: <path d="M20 12H4m6-6-6 6 6 6" />,
		chevron: <path d="m6 9 6 6 6-6" />,
		check: <path d="m5 12 4 4L19 6" />,
		lock: (
			<>
				<rect x="5" y="10" width="14" height="11" rx="2" />
				<path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
			</>
		),
		clock: (
			<>
				<circle cx="12" cy="12" r="9" />
				<path d="M12 6v6l4 2" />
			</>
		),
		close: <path d="m6 6 12 12M6 18 18 6" />,
		alert: (
			<>
				<circle cx="12" cy="12" r="9" />
				<path d="M12 7v6M12 16h.01" />
			</>
		),
		download: (
			<>
				<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5" />
			</>
		),
		play: <path d="m8 4 12 8-12 8Z" />,
		refresh: (
			<>
				<path d="M20 7v5h-5M4 17v-5h5" />
				<path d="M6 6a8 8 0 0 1 14 6M4 12a8 8 0 0 0 14 6" />
			</>
		),
		external: (
			<>
				<path d="M14 3h7v7m0-7L10 14M10 3H3v18h18v-7" />
			</>
		),
	};
	return (
		<svg
			className={`icon ${className}`}
			width="24"
			height="24"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="1.8"
			strokeLinecap="round"
			strokeLinejoin="round"
			aria-hidden="true"
		>
			{paths[name]}
		</svg>
	);
}
function Progress({ course, state }: { course: Course; state: DemoState }) {
	const count = completedCount(course, state);
	const percent = (count / course.total) * 100;
	return (
		<div className="progress-block">
			<div className="progress-caption">
				<span>
					Завершено {count} из {course.total} уроков
				</span>
				<span>{Math.round(percent)}%</span>
			</div>
			<div
				className="progress-track"
				role="progressbar"
				aria-label={`Прогресс курса «${course.title}»`}
				aria-valuemin={0}
				aria-valuemax={course.total}
				aria-valuenow={count}
			>
				<span style={{ width: `${percent}%` }} />
			</div>
		</div>
	);
}
function Badge({
	children,
	tone = "",
}: {
	children: ReactNode;
	tone?: string;
}) {
	return <span className={`badge ${tone}`}>{children}</span>;
}
function CourseBadge({ course, state }: { course: Course; state: DemoState }) {
	const count = completedCount(course, state);
	return count === course.total ? (
		<Badge tone="success">
			<Icon name="check" />
			Завершён
		</Badge>
	) : state.started.includes(course.id) ? (
		<Badge tone="accent">В процессе</Badge>
	) : (
		<Badge>Не начат</Badge>
	);
}
function Modal({
	title,
	children,
	onClose,
	sheet = false,
}: {
	title: string;
	children: ReactNode;
	onClose: () => void;
	sheet?: boolean;
}) {
	const ref = useRef<HTMLDialogElement>(null);
	const heading = useRef<HTMLHeadingElement>(null);
	useEffect(() => {
		const dialog = ref.current;
		const previous =
			document.activeElement instanceof HTMLElement
				? document.activeElement
				: null;
		const overflow = document.body.style.overflow;
		dialog?.showModal();
		heading.current?.focus();
		document.body.style.overflow = "hidden";
		return () => {
			dialog?.close();
			document.body.style.overflow = overflow;
			if (previous?.isConnected) previous.focus();
		};
	}, []);
	return (
		<dialog
			ref={ref}
			className={`modal ${sheet ? "sheet" : ""}`}
			aria-labelledby="modal-title"
			onCancel={(event) => {
				event.preventDefault();
				onClose();
			}}
			onKeyDown={(event) => {
				if (event.key === "Tab") {
					const items = Array.from(
						event.currentTarget.querySelectorAll<HTMLElement>(
							'button:not(:disabled), a[href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), summary, [tabindex="0"]',
						),
					).filter((item) => item.getClientRects().length > 0);
					const first = items[0];
					const last = items[items.length - 1];
					if (!first) {
						event.preventDefault();
						heading.current?.focus();
					} else if (
						event.shiftKey &&
						(document.activeElement === first ||
							document.activeElement === heading.current)
					) {
						event.preventDefault();
						last.focus();
					} else if (!event.shiftKey && document.activeElement === last) {
						event.preventDefault();
						first.focus();
					}
				}
				if (event.key === "Escape") {
					event.preventDefault();
					onClose();
				}
			}}
			onClick={(event) => {
				const rect = event.currentTarget.getBoundingClientRect();
				if (
					event.target === event.currentTarget &&
					(event.clientX < rect.left ||
						event.clientX > rect.right ||
						event.clientY < rect.top ||
						event.clientY > rect.bottom)
				)
					onClose();
			}}
		>
			<header className="modal-header">
				<h2 ref={heading} tabIndex={-1} id="modal-title">
					{title}
				</h2>
				<button
					type="button"
					className="icon-button"
					aria-label={`Закрыть: ${title}`}
					onClick={onClose}
				>
					<Icon name="close" />
				</button>
			</header>
			<div className="modal-content">{children}</div>
		</dialog>
	);
}
function MeetingCard({
	onDetails,
	compact = false,
}: {
	onDetails: () => void;
	compact?: boolean;
}) {
	return (
		<section className={`card meeting-card ${compact ? "compact" : ""}`}>
			<h2>Ближайшее занятие</h2>
			<div className="meeting-info">
				<span className="meeting-icon">
					<Icon name="calendar" />
				</span>
				<div>
					<h3>Разбор практических задач по Java</h3>
					<p className="meta">
						<Icon name="calendar" />
						18 ноября 2026
					</p>
					<p className="meta">
						<Icon name="clock" />
						19:00–20:30 МСК
					</p>
					<Badge>
						<Icon name="clock" />
						Ожидает начала
					</Badge>
				</div>
			</div>
			<button
				type="button"
				className="button secondary full"
				onClick={onDetails}
			>
				Подробнее
			</button>
		</section>
	);
}
function Program({
	course,
	state,
	selected,
}: {
	course: Course;
	state: DemoState;
	selected?: string;
}) {
	const current = currentLesson(course, state);
	return (
		<div className="program">
			{course.modules.map((module, index) => {
				const done = module.lessons.filter((lesson) =>
					state.completed.includes(lessonKey(course.id, lesson.id)),
				).length;
				const available = isAvailable(course, module.lessons[0], state);
				return (
					<details
						key={module.title}
						open={module.lessons.some(
							(lesson) => lesson.id === (selected ?? current.id),
						)}
					>
						<summary>
							<span
								className={`module-indicator ${done === module.lessons.length ? "done" : ""}`}
							>
								<Icon
									name={
										done === module.lessons.length
											? "check"
											: available
												? "book"
												: "lock"
									}
								/>
							</span>
							<span>
								<strong>
									{index + 1}. {module.title}
								</strong>
								<small>
									{done
										? `${done} из ${module.lessons.length} завершены`
										: `${module.lessons.length} уроков`}
								</small>
							</span>
							<Icon name="chevron" className="disclosure" />
						</summary>
						<div>
							{module.lessons.map((lesson, lessonIndex) => {
								const complete = state.completed.includes(
									lessonKey(course.id, lesson.id),
								);
								const unlocked = isAvailable(course, lesson, state);
								const active = lesson.id === (selected ?? current.id);
								const list = lessons(course);
								const previous =
									list[list.findIndex((item) => item.id === lesson.id) - 1];
								const contents = (
									<>
										<span className="lesson-number">
											{index + 1}.{lessonIndex + 1}
										</span>
										<span
											className={`lesson-indicator ${complete ? "done" : active ? "active" : ""}`}
										>
											<Icon
												name={complete ? "check" : unlocked ? "play" : "lock"}
											/>
										</span>
										<span className="lesson-row-title">
											{lesson.title}
											{!unlocked && (
												<small>
													После завершения урока «{previous?.title}»
												</small>
											)}
										</span>
										{active ? (
											<Badge tone="accent">Текущий урок</Badge>
										) : complete ? (
											<small className="lesson-status">Завершён</small>
										) : null}
										{unlocked && <Icon name="arrow" className="row-arrow" />}
									</>
								);
								return unlocked ? (
									<Link
										key={lesson.id}
										className={`lesson-row ${active ? "selected" : ""}`}
										href={lessonHref(course, lesson)}
										aria-current={active ? "page" : undefined}
									>
										{contents}
									</Link>
								) : (
									<div key={lesson.id} className="lesson-row locked">
										{contents}
									</div>
								);
							})}
						</div>
					</details>
				);
			})}
		</div>
	);
}
function CourseCard({
	course,
	state,
	featured = false,
}: {
	course: Course;
	state: DemoState;
	featured?: boolean;
}) {
	const current = currentLesson(course, state);
	const done = completedCount(course, state) === course.total;
	return (
		<article className={`card course-card ${featured ? "featured" : ""}`}>
			<div className="course-card-top">
				<h2>
					<Link href={courseHref(course)}>
						{course.title}
						<Icon name="arrow" />
					</Link>
				</h2>
				<CourseBadge course={course} state={state} />
			</div>
			<p className="muted">{course.description}</p>
			<div className="course-card-bottom">
				<div>
					{state.started.includes(course.id) && (
						<Progress course={course} state={state} />
					)}
					{featured && !done && (
						<div className="lesson-preview">
							<div>
								<span className="eyebrow">Текущий урок</span>
								<strong>{current.title}</strong>
							</div>
							<div>
								<span className="eyebrow">Следующий урок</span>
								<strong>
									{lessons(course)[
										lessons(course).findIndex(
											(item) => item.id === current.id,
										) + 1
									]?.title ?? "Завершение курса"}
								</strong>
							</div>
						</div>
					)}
				</div>
				<Link
					className={`button ${featured ? "primary" : "secondary"}`}
					href={done ? courseHref(course) : lessonHref(course, current)}
				>
					{done
						? "Повторить материал"
						: state.started.includes(course.id)
							? "Продолжить"
							: "Начать"}
					<Icon name="arrow" />
				</Link>
			</div>
		</article>
	);
}
const statusText: Record<SubmissionStatus, string> = {
	draft: "Не отправлено",
	review: "На проверке",
	changes: "Нужны исправления",
	accepted: "Принято",
};
function SubmissionBadge({ status }: { status: SubmissionStatus }) {
	return (
		<Badge
			tone={
				status === "accepted"
					? "success"
					: status === "changes"
						? "warning"
						: ""
			}
		>
			{status !== "draft" && (
				<Icon
					name={
						status === "accepted"
							? "check"
							: status === "changes"
								? "alert"
								: "clock"
					}
				/>
			)}
			{statusText[status]}
		</Badge>
	);
}

export default function Academy({
	live,
	empty = false,
	invalidRoute = false,
}: {
	live?: LiveData;
	empty?: boolean;
	invalidRoute?: boolean;
}) {
	const isLive = Boolean(live);
	const courses = live?.courses ?? demoCourses;
	const name = live?.profile.firstName || "Александр";

	const search = useSearchParams();
	const router = useRouter();
	const view = search.get("view") ?? "home";
	const [demoState, setState] = useState<DemoState>(initialState);
	const state = live?.state ?? demoState;
	const [ready, setReady] = useState(false);
	const [storageError, setStorageError] = useState(false);
	const [modal, setModal] = useState<"program" | "meeting" | null>(null);
	const [profileOpen, setProfileOpen] = useState(false);
	const [filter, setFilter] = useState("all");
	const [assignmentFilter, setAssignmentFilter] = useState("all");
	const [meetingTab, setMeetingTab] = useState("upcoming");
	const [preview, setPreview] = useState("normal");
	const [simulateError, setSimulateError] = useState(false);
	const [pending, setPending] = useState<"submit" | "complete" | null>(null);
	const [formError, setFormError] = useState("");
	const [fieldError, setFieldError] = useState("");
	const [notice, setNotice] = useState("");
	const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
	const heading = useRef<HTMLHeadingElement>(null);
	const assignmentHeading = useRef<HTMLHeadingElement>(null);
	const programScroll = useRef(0);
	const course =
		courses.find((item) => item.id === search.get("course")) ?? courses[0];
	const lesson =
		lessons(course).find((item) => item.id === search.get("lesson")) ??
		currentLesson(course, state);
	const activeSection = ["course", "lesson"].includes(view) ? "courses" : view;
	const validView = [
		"home",
		"courses",
		"course",
		"lesson",
		"assignments",
		"reviews",
		"meetings",
		"login",
	].includes(view);
	const routeKey = search.toString();
	useEffect(() => {
		if (isLive) {
			setReady(true);
			return;
		}
		try {
			setState(parseState(localStorage.getItem(storageKey)));
		} catch {
			setStorageError(true);
		}
		setReady(true);
		return () => {
			if (timer.current) clearTimeout(timer.current);
		};
	}, [isLive]);
	useEffect(() => {
		if (!ready || live) return;
		try {
			localStorage.setItem(storageKey, JSON.stringify(state));
		} catch {
			setStorageError(true);
		}
	}, [state, ready, live]);
	useEffect(() => {
		if (!routeKey && !ready) return;
		setModal(null);
		setProfileOpen(false);
		setFormError("");
		setFieldError("");
		setNotice("");
		const focusTimer = setTimeout(() => {
			if (new URLSearchParams(routeKey).get("assignment") === "1") {
				assignmentHeading.current?.scrollIntoView({ block: "start" });
				assignmentHeading.current?.focus();
			} else {
				heading.current?.focus({ preventScroll: true });
			}
		}, 50);
		return () => clearTimeout(focusTimer);
	}, [routeKey, ready]);
	useEffect(() => {
		if (
			live ||
			!ready ||
			view !== "lesson" ||
			!isAvailable(course, lesson, state) ||
			state.started.includes(course.id)
		)
			return;
		setState((previous) => ({
			...previous,
			started: [...new Set([...previous.started, course.id])],
		}));
	}, [ready, view, course, lesson, state, live]);
	function run(action: "submit" | "complete", onSuccess: () => void) {
		if (timer.current) return;
		setPending(action);
		setFormError("");
		setNotice("");
		timer.current = setTimeout(() => {
			timer.current = null;
			setPending(null);
			if (simulateError) {
				setFormError(
					action === "submit"
						? "Не удалось отправить задание. Введённые данные сохранены."
						: "Не удалось завершить урок. Попробуйте ещё раз.",
				);
				return;
			}
			onSuccess();
		}, 700);
	}
	function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		setFieldError("");
		try {
			const url = new URL(state.submission.url);
			if (url.protocol !== "https:") throw new Error();
		} catch {
			setFieldError("Укажите корректную HTTPS-ссылку на Git-репозиторий.");
			return;
		}
		run("submit", () => {
			setState((previous) => ({
				...previous,
				submission: { ...previous.submission, status: "review" },
			}));
			setNotice("Решение отправлено на проверку.");
		});
	}
	function completeLesson() {
		if (live) {
			if (pending) return;
			setPending("complete");
			setFormError("");
			void live
				.complete(course.id, lesson.id)
				.then(() => setNotice("Урок завершён. Следующий урок доступен."))
				.catch((error) =>
					setFormError(
						error instanceof Error
							? error.message
							: "Не удалось завершить урок",
					),
				)
				.finally(() => setPending(null));
			return;
		}

		run("complete", () => {
			setState((previous) => ({
				...previous,
				completed: [
					...new Set([...previous.completed, lessonKey(course.id, lesson.id)]),
				],
			}));
			setNotice("Урок завершён. Следующий урок доступен.");
		});
	}
	function review(status: "changes" | "accepted") {
		if (state.submission.status !== "review" || pending) return;
		setState((previous) => ({
			...previous,
			submission: { ...previous.submission, status },
			completed:
				status === "accepted"
					? [...new Set([...previous.completed, "java/conditions"])]
					: previous.completed,
		}));
		setNotice(
			status === "accepted"
				? "Демонстрация: преподаватель принял задание. Урок завершён."
				: "Демонстрация: преподаватель вернул задание на доработку.",
		);
	}
	const java =
		courses.find(
			(c) => completedCount(c, state) < c.total && state.started.includes(c.id),
		) ??
		courses.find((c) => completedCount(c, state) < c.total) ??
		courses[0];
	const javaCurrent = currentLesson(java, state);
	const done = state.completed.includes(lessonKey(course.id, lesson.id));
	const list = lessons(course);
	const index = list.findIndex((item) => item.id === lesson.id);
	const next = list[index + 1];
	const previous = list[index - 1];
	const nav: { id: string; label: string; short: string; icon: IconName }[] = [
		{ id: "home", label: "Главная", short: "Главная", icon: "home" },
		{ id: "courses", label: "Мои курсы", short: "Курсы", icon: "book" },
		{ id: "assignments", label: "Задания", short: "Задания", icon: "file" },
		{
			id: "meetings",
			label: "Онлайн-занятия",
			short: "Занятия",
			icon: "calendar",
		},
	];
	function title(text: string, subtitle?: string) {
		return (
			<div className="page-title">
				<h1 ref={heading} tabIndex={-1}>
					{text}
				</h1>
				{subtitle && <p>{subtitle}</p>}
			</div>
		);
	}
	function assignmentLink() {
		return lessonHref(
			java,
			lessons(java).find((item) => item.id === "conditions") ?? javaCurrent,
			true,
		);
	}
	function assignmentRow() {
		return (
			<div className="card assignment-row">
				<Icon name="file" />
				<div>
					<strong>Проверка високосного года</strong>
					{view === "assignments" && <p className="muted">{java.title}</p>}
				</div>
				<SubmissionBadge status={state.submission.status} />
				<Link className="text-link" href={assignmentLink()}>
					Открыть задание
					<Icon name="arrow" />
				</Link>
			</div>
		);
	}
	function statePanel() {
		if (preview === "error")
			return (
				<section className="card empty-state" role="alert">
					<Icon name="alert" />
					<h2>Не удалось загрузить курсы</h2>
					<p>Проверьте соединение и попробуйте ещё раз.</p>
					<button
						type="button"
						className="button secondary"
						onClick={() => setPreview("normal")}
					>
						Повторить
						<Icon name="refresh" />
					</button>
				</section>
			);
		if (preview === "empty")
			return (
				<section className="card empty-state">
					<Icon name="book" />
					<h2>Курсы пока не назначены</h2>
					<p>Когда вам назначат курс, он появится здесь.</p>
				</section>
			);
		return (
			<div
				className="card skeleton"
				role="status"
				aria-label="Загружаем курсы"
				aria-busy="true"
			>
				<span />
				<span />
				<span />
				<span />
				<p>Загружаем курсы…</p>
			</div>
		);
	}
	const showPreview =
		preview !== "normal" && ["home", "courses", "assignments"].includes(view);

	return (
		<div className={`academy ${view === "lesson" ? "lesson-mode" : ""}`}>
			<a href="#main-content" className="skip-link">
				К содержимому
			</a>
			<aside className="sidebar">
				<Link className="brand" href="/">
					Advent
					<br />
					Academy
				</Link>
				<nav aria-label="Основная навигация">
					{nav.map((item) => (
						<Link
							key={item.id}
							href={item.id === "home" ? "/" : `/?view=${item.id}`}
							className={
								activeSection === item.id ? "nav-link active" : "nav-link"
							}
							aria-current={activeSection === item.id ? "page" : undefined}
						>
							<Icon name={item.icon} />
							{item.label}
						</Link>
					))}
					{live?.canReview && (
						<Link
							href="/?view=reviews"
							className={view === "reviews" ? "active" : ""}
							aria-current={view === "reviews" ? "page" : undefined}
						>
							<Icon name="file" />
							Проверка заданий
						</Link>
					)}
				</nav>
				<div className="sidebar-bottom">
					<span className="sidebar-dot" />
					Ваш следующий шаг — здесь.
				</div>
			</aside>
			<div className="workspace">
				<header className="topbar">
					<Link className="mobile-brand" href="/">
						Advent Academy
					</Link>
					<span className="desktop-title">
						{view === "course" ? (
							<>
								<Link href="/?view=courses">Мои курсы</Link>
								<span className="breadcrumb-divider">/</span>
								{live ? course.title : course.id.toUpperCase()}
							</>
						) : (
							"Кабинет ученика"
						)}
					</span>
					<div className="header-actions">
						{!live && (
							<details className="demo-tools">
								<summary>Демо</summary>
								<div className="demo-popover">
									<strong>Демонстрационные данные</strong>
									<p>Изменения сохраняются только в этом браузере.</p>
									<label>
										Состояние списка
										<select
											value={preview}
											onChange={(event) => setPreview(event.target.value)}
										>
											<option value="normal">Обычное</option>
											<option value="empty">Нет курсов</option>
											<option value="loading">Загрузка</option>
											<option value="error">Ошибка загрузки</option>
										</select>
									</label>
									<label className="checkbox-label">
										<input
											type="checkbox"
											checked={simulateError}
											onChange={(event) =>
												setSimulateError(event.target.checked)
											}
										/>
										Ошибка отправки / завершения
									</label>
									<div className="demo-review">
										<span>Ответ преподавателя</span>
										<button
											type="button"
											className="button secondary"
											disabled={
												state.submission.status !== "review" || !!pending
											}
											onClick={() => review("accepted")}
										>
											Принять задание
										</button>
										<button
											type="button"
											className="button secondary"
											disabled={
												state.submission.status !== "review" || !!pending
											}
											onClick={() => review("changes")}
										>
											Нужны исправления
										</button>
									</div>
									<button
										type="button"
										className="text-link"
										disabled={!!pending}
										onClick={() => {
											setState(initialState);
											setPreview("normal");
											setSimulateError(false);
											setNotice("Демонстрационные данные сброшены.");
											router.push("/demo");
										}}
									>
										Сбросить прогресс
										<Icon name="refresh" />
									</button>
								</div>
							</details>
						)}
						<ThemeToggle />
						{live?.canReview && (
							<Link className="text-link" href="/?view=manage">
								Управление курсами
							</Link>
						)}
						{live?.profile.roles.includes("ADMIN") && (
							<Link className="text-link" href="/?view=users">
								Пользователи
							</Link>
						)}
						<div className="profile">
							<button
								type="button"
								className="profile-button"
								aria-label={`Меню пользователя ${name}`}
								aria-expanded={profileOpen}
								aria-controls="profile-menu"
								onClick={() => setProfileOpen(!profileOpen)}
								onKeyDown={(event) => {
									if (event.key === "Escape") setProfileOpen(false);
								}}
							>
								<span className="avatar">{name.slice(0, 1)}</span>
								<span className="profile-name">{name}</span>
								<Icon name="chevron" />
							</button>
							{profileOpen && (
								<>
									<button
										type="button"
										className="menu-backdrop"
										aria-label="Закрыть меню профиля"
										onClick={() => setProfileOpen(false)}
									/>
									<div id="profile-menu" className="profile-menu">
										<strong>{name}</strong>
										<p>{live?.profile.email ?? "Демонстрационный кабинет"}</p>
										<Link
											href="/?view=login"
											onKeyDown={(event) => {
												if (event.key === "Escape") {
													setProfileOpen(false);
													document
														.querySelector<HTMLButtonElement>(".profile-button")
														?.focus();
												}
											}}
											onClick={(event) => {
												setProfileOpen(false);
												if (live) {
													event.preventDefault();
													void live
														.logout()
														.catch((error) =>
															setNotice(
																error instanceof Error
																	? error.message
																	: "Не удалось выйти",
															),
														);
												}
											}}
										>
											Выйти
										</Link>
									</div>
								</>
							)}
						</div>
					</div>
				</header>
				<main id="main-content" className="main-content">
					{storageError && (
						<p className="inline-warning" role="status">
							Хранилище браузера недоступно. Изменения сохранятся только до
							перезагрузки.
						</p>
					)}
					<div className="live-message" role="status" aria-live="polite">
						{notice}
					</div>
					{!ready ? (
						statePanel()
					) : live && view === "reviews" ? (
						live.canReview ? (
							live.reviewPanel
						) : (
							<section className="card empty-state">
								{title("Доступ запрещён")}
								<p>Этот экран доступен преподавателям.</p>
							</section>
						)
					) : live && view === "assignments" ? (
						live.assignmentList
					) : live && view === "meetings" ? (
						live.meetingList
					) : live && (invalidRoute || empty || view === "login") ? (
						<section className="card empty-state">
							{title(
								invalidRoute
									? "Курс или урок недоступен"
									: empty
										? "Курсы пока не назначены"
										: "Вы вошли в кабинет",
							)}
							<p>
								{invalidRoute
									? "Проверьте ссылку и доступ к курсу."
									: empty
										? "Назначенный вам курс появится здесь."
										: "Перейдите к своим курсам."}
							</p>
							<Link href="/?view=courses" className="button primary">
								Мои курсы
							</Link>
							{empty && view === "home" && live.nearestMeeting}
						</section>
					) : !validView ? (
						<>
							{title("Страница не найдена")}
							<Link href="/" className="button primary">
								На главную
							</Link>
						</>
					) : view === "login" ? (
						<section className="card demo-login">
							{title("До встречи, Александр!")}
							<p className="muted">
								Это демонстрационный кабинет. Авторизация появится после
								подключения сервера.
							</p>
							<Link className="button primary" href="/">
								Вернуться в демоверсию
								<Icon name="arrow" />
							</Link>
						</section>
					) : showPreview ? (
						<>
							{title(
								view === "home"
									? "Привет, Александр!"
									: view === "courses"
										? "Мои курсы"
										: "Задания",
							)}
							{statePanel()}
						</>
					) : view === "home" ? (
						<>
							{title(
								`Привет, ${name}!`,
								live ? "Продолжим обучение." : "Продолжим изучать Java.",
							)}
							<div className="dashboard-grid">
								<section className="card continue-card">
									<p className="eyebrow">Текущий курс</p>
									<h2>{java.title}</h2>
									<div className="current-lesson">
										<span className="muted">Текущий урок</span>
										<h3>{javaCurrent.title}</h3>
									</div>
									<Progress course={java} state={state} />
									<Link
										className="button primary full"
										href={
											completedCount(java, state) === java.total
												? courseHref(java)
												: lessonHref(java, javaCurrent)
										}
									>
										{completedCount(java, state) === java.total
											? "Повторить материал"
											: "Продолжить обучение"}
										<Icon name="arrow" />
									</Link>
									<p className="next-preview">
										Следующий урок:{" "}
										{lessons(java)[
											lessons(java).findIndex(
												(item) => item.id === javaCurrent.id,
											) + 1
										]?.title ?? "Завершение курса"}
									</p>
								</section>
								{live ? (
									live.nearestMeeting
								) : (
									<MeetingCard onDetails={() => setModal("meeting")} />
								)}
							</div>
							{live?.attention}
							{!live && (
								<section className="attention-section">
									<h2>Требует внимания</h2>
									{state.submission.status === "accepted" ? (
										<div className="card accepted-summary">
											<Icon name="check" />
											<span>Задание принято. Можно продолжать обучение.</span>
											<Link
												className="text-link"
												href={lessonHref(java, javaCurrent)}
											>
												К уроку
												<Icon name="arrow" />
											</Link>
										</div>
									) : (
										assignmentRow()
									)}
								</section>
							)}
							<section>
								<div className="section-heading">
									<h2>Другие курсы</h2>
									<Link className="text-link" href="/?view=courses">
										Все курсы
										<Icon name="arrow" />
									</Link>
								</div>
								<div className="other-courses">
									{courses
										.filter((item) => item.id !== java.id)
										.map((item) => (
											<CourseCard key={item.id} course={item} state={state} />
										))}
								</div>
							</section>
						</>
					) : view === "courses" ? (
						<>
							{title("Мои курсы", "Ваши учебные программы.")}
							<fieldset className="filters" aria-label="Фильтр курсов">
								{[
									["all", "Все"],
									["active", "В процессе"],
									["new", "Не начаты"],
									["complete", "Завершены"],
								].map(([id, label]) => (
									<button
										type="button"
										key={id}
										className={filter === id ? "filter selected" : "filter"}
										aria-pressed={filter === id}
										onClick={() => setFilter(id)}
									>
										{label}
									</button>
								))}
							</fieldset>
							<div className="course-list">
								{courses
									.filter(
										(item) =>
											filter === "all" ||
											(filter === "complete"
												? completedCount(item, state) === item.total
												: filter === "new"
													? !state.started.includes(item.id)
													: state.started.includes(item.id) &&
														completedCount(item, state) < item.total),
									)
									.map((item) => (
										<CourseCard
											key={item.id}
											course={item}
											state={state}
											featured={item.id === "java"}
										/>
									))}
							</div>
							{!courses.some(
								(item) =>
									filter === "all" ||
									(filter === "complete"
										? completedCount(item, state) === item.total
										: filter === "new"
											? !state.started.includes(item.id)
											: state.started.includes(item.id) &&
												completedCount(item, state) < item.total),
							) && (
								<section className="card empty-state">
									<Icon name="book" />
									<h2>Здесь пока нет курсов</h2>
									<p>Курсы с выбранным статусом появятся здесь.</p>
									<button
										type="button"
										className="button secondary"
										onClick={() => setFilter("all")}
									>
										Показать все
									</button>
								</section>
							)}
						</>
					) : view === "course" ? (
						<>
							<section className="card course-overview">
								{title(course.title, course.description)}
								<div className="overview-bottom">
									<Progress course={course} state={state} />
									<div>
										<Link
											className="button primary full"
											href={lessonHref(course, currentLesson(course, state))}
										>
											{completedCount(course, state) === course.total
												? "Повторить материал"
												: "Продолжить обучение"}
											<Icon name="arrow" />
										</Link>
										<p className="next-preview">
											Текущий урок: {currentLesson(course, state).title}
										</p>
									</div>
								</div>
							</section>
							<section>
								<h2>Программа курса</h2>
								<Program course={course} state={state} />
								<p className="program-note">
									<Icon name="alert" />
									Следующий урок откроется после завершения предыдущего.
								</p>
							</section>
						</>
					) : view === "lesson" ? (
						!isAvailable(course, lesson, state) ? (
							<section className="card empty-state">
								{title("Урок недоступен")}
								<Icon name="lock" />
								<p>Завершите урок «{previous?.title}», чтобы продолжить.</p>
								<Link
									className="button primary"
									href={lessonHref(course, currentLesson(course, state))}
								>
									К текущему уроку
								</Link>
							</section>
						) : (
							<div className="lesson-layout">
								<aside className="lesson-program">
									<h2>Программа курса</h2>
									<Progress course={course} state={state} />
									<Program course={course} state={state} selected={lesson.id} />
								</aside>
								<article className="lesson-article">
									<div className="lesson-toolbar">
										<Link className="text-link" href={courseHref(course)}>
											<Icon name="back" />К курсу
										</Link>
										<button
											type="button"
											className="button secondary program-toggle"
											onClick={() => setModal("program")}
										>
											<Icon name="file" />
											Программа курса
										</button>
									</div>
									{title(lesson.title)}
									<p className="mobile-course-name">{course.title}</p>
									{live ? (
										live.lessonLoading ? (
											<section className="card" role="status">
												Загружаем материал…
											</section>
										) : live.lessonError ? (
											<section className="card" role="alert">
												<p>{live.lessonError}</p>
												<button
													className="button secondary"
													type="button"
													onClick={() => window.location.reload()}
												>
													Повторить
												</button>
											</section>
										) : live.lesson ? (
											<>
												{live.lesson.assets
													.filter((asset) =>
														asset.contentType.startsWith("video/"),
													)
													.map((asset) => (
														<video
															key={asset.id}
															className="lesson-video"
															controls
															preload="metadata"
															aria-label={asset.filename}
															src={asset.url.replace(
																"/api/assets/",
																"/api/backend/assets/",
															)}
														>
															<track kind="captions" />
														</video>
													))}
												<SafeHtml className="lesson-material" html={live.lesson.html.replaceAll(
															"/api/assets/",
															"/api/backend/assets/",
														)} />
												{live.lesson.assets
													.filter(
														(asset) => !asset.contentType.startsWith("video/"),
													)
													.map((asset) => (
														<a
															key={asset.id}
															className="card attachment"
															href={asset.url.replace(
																"/api/assets/",
																"/api/backend/assets/",
															)}
														>
															<Icon name="file" />
															<span>{asset.filename}</span>
															<span className="text-link">
																Скачать
																<Icon name="download" />
															</span>
														</a>
													))}
											</>
										) : null
									) : (
										<>
											<div className="demo-video">
												<span className="video-play">
													<Icon name="play" />
												</span>
												<strong>Видеоурок: {lesson.title.toLowerCase()}</strong>
												<p>Видео появится вместе с материалами курса</p>
												<Badge>Демонстрация</Badge>
											</div>
											<section className="lesson-material">
												<h2>
													{lesson.id === "conditions"
														? "Как работает if / else"
														: lesson.title}
												</h2>
												<p>
													{lesson.id === "conditions"
														? "Условие определяет, какой блок кода выполнится. Если условие истинно, выполняется if; иначе — else."
														: "В этом демонстрационном уроке показана структура учебного материала. Полное содержание будет добавлено вместе с курсом."}
												</p>
												{!live && course.id === "java" && (
													<section aria-label="Пример кода Java">
														{/* biome-ignore lint/a11y/noNoninteractiveTabindex: Scrollable code must be keyboard accessible. */}
														<pre tabIndex={0}>
															<code>
																<span className="code-keyword">int</span> age ={" "}
																<span className="code-number">18</span>;{"\n"}
																<span className="code-keyword">if</span> (age
																&gt;= <span className="code-number">18</span>){" "}
																{"{"}
																{"\n"} System.out.println(
																<span className="code-string">
																	"Доступ разрешён"
																</span>
																);{"\n"}
																{"}"} <span className="code-keyword">else</span>{" "}
																{"{"}
																{"\n"} System.out.println(
																<span className="code-string">
																	"Доступ закрыт"
																</span>
																);{"\n"}
																{"}"}
															</code>
														</pre>
													</section>
												)}
											</section>
											<a
												className="card attachment"
												href={`data:text/plain;charset=utf-8,${encodeURIComponent(`Advent Academy — демонстрационный конспект\n${lesson.title}\n\nЭто пример вложения. Учебные материалы будут добавлены позже.`)}`}
												download="advent-lesson-demo.txt"
											>
												<Icon name="file" />
												<span>Демонстрационный конспект.txt</span>
												<span className="text-link">
													Скачать
													<Icon name="download" />
												</span>
											</a>
										</>
									)}
									{live && lesson.assignment ? (
										live.assignmentPanel
									) : lesson.assignment ? (
										<section
											className="card assignment-form"
											aria-busy={pending === "submit"}
										>
											<div className="assignment-heading">
												<div>
													<span className="eyebrow">Практическое задание</span>
													<h2 ref={assignmentHeading} tabIndex={-1}>
														Проверка високосного года
													</h2>
												</div>
												<SubmissionBadge status={state.submission.status} />
											</div>
											<p>
												Напишите программу, которая определяет, является ли
												указанный год високосным.
											</p>
											<p className="muted">
												Отправьте ссылку на решение в Git-репозитории.
											</p>
											{state.submission.status === "changes" && (
												<div className="feedback warning">
													<Icon name="alert" />
													<div>
														<strong>Нужны исправления</strong>
														<p>
															Учтите годы, которые делятся на 100, но не делятся
															на 400.
														</p>
													</div>
												</div>
											)}
											{["review", "accepted"].includes(
												state.submission.status,
											) ? (
												<div
													className={`feedback ${state.submission.status === "accepted" ? "success" : ""}`}
												>
													<Icon
														name={
															state.submission.status === "accepted"
																? "check"
																: "clock"
														}
													/>
													<div>
														<strong>
															{statusText[state.submission.status]}
														</strong>
														<p>
															{state.submission.status === "accepted"
																? "Задание принято. Урок завершён."
																: "Решение отправлено. Ожидайте результата проверки."}
														</p>
														<a
															className="text-link"
															href={state.submission.url}
															target="_blank"
															rel="noopener noreferrer"
														>
															Открыть решение
															<span className="sr-only"> в новой вкладке</span>
															<Icon name="external" />
														</a>
													</div>
												</div>
											) : (
												<form onSubmit={submit} noValidate>
													<label htmlFor="solution">Ссылка на решение</label>
													<input
														id="solution"
														type="url"
														required
														value={state.submission.url}
														disabled={!!pending}
														aria-invalid={!!fieldError}
														aria-describedby={
															fieldError ? "solution-error" : "solution-help"
														}
														placeholder="https://github.com/…"
														onChange={(event) => {
															setFieldError("");
															setState((old) => ({
																...old,
																submission: {
																	...old.submission,
																	url: event.target.value,
																},
															}));
														}}
													/>
													{fieldError ? (
														<p
															id="solution-error"
															className="field-error"
															role="alert"
														>
															{fieldError}
														</p>
													) : (
														<p id="solution-help" className="helper">
															Укажите ссылку на репозиторий или ветку.
														</p>
													)}
													<label htmlFor="comment">
														Комментарий{" "}
														<span className="muted">(необязательно)</span>
													</label>
													<textarea
														id="comment"
														value={state.submission.comment}
														disabled={!!pending}
														placeholder="Что стоит учесть при проверке?"
														onChange={(event) =>
															setState((old) => ({
																...old,
																submission: {
																	...old.submission,
																	comment: event.target.value,
																},
															}))
														}
													/>
													{formError && (
														<p className="feedback error" role="alert">
															<Icon name="alert" />
															{formError}
														</p>
													)}
													<button
														type="submit"
														className="button primary full"
														disabled={!!pending}
													>
														{pending === "submit"
															? "Отправляем…"
															: formError
																? "Повторить отправку"
																: state.submission.status === "changes"
																	? "Отправить повторно"
																	: "Отправить на проверку"}
													</button>
													<p className="helper">
														Отправка не означает принятия задания.
													</p>
												</form>
											)}
											{state.submission.status === "review" && (
												<p className="helper">
													Следующий урок откроется после принятия задания
													преподавателем.
												</p>
											)}
										</section>
									) : (
										<section className="card completion-card">
											{done ? (
												<p className="success-text">
													<Icon name="check" />
													Урок завершён
												</p>
											) : (
												<>
													<h2>Готовы двигаться дальше?</h2>
													<p className="muted">
														Завершите урок, чтобы открыть следующий.
													</p>
													{formError && (
														<p className="feedback error" role="alert">
															{formError}
														</p>
													)}
													<button
														type="button"
														className="button primary"
														disabled={
															!!pending ||
															Boolean(
																live &&
																	(!live.lesson ||
																		live.lessonLoading ||
																		live.lessonError),
															)
														}
														onClick={completeLesson}
													>
														{pending === "complete"
															? "Завершаем…"
															: formError
																? "Повторить"
																: "Завершить урок"}
														<Icon name="check" />
													</button>
												</>
											)}
										</section>
									)}
									<nav
										className="lesson-pagination"
										aria-label="Переходы между уроками"
									>
										{previous && (
											<Link
												className="button secondary"
												href={lessonHref(course, previous)}
											>
												<Icon name="back" />
												{previous.title}
											</Link>
										)}
										{next ? (
											isAvailable(course, next, state) ? (
												<Link
													className="button primary"
													href={lessonHref(course, next)}
												>
													Следующий урок: {next.title}
													<Icon name="arrow" />
												</Link>
											) : (
												<div className="locked-next">
													<Icon name="lock" />
													<span>
														{next.title}
														<small>
															{lesson.assignment
																? "Откроется после принятия задания"
																: "Откроется после завершения урока"}
														</small>
													</span>
												</div>
											)
										) : (
											<Link
												className="button secondary"
												href={courseHref(course)}
											>
												К программе курса
												<Icon name="arrow" />
											</Link>
										)}
									</nav>
								</article>
							</div>
						)
					) : view === "assignments" ? (
						<>
							{title("Задания", "Практика, которая превращает знания в навык.")}
							<fieldset className="filters" aria-label="Фильтр заданий">
								{[
									["all", "Все"],
									["draft", "Не отправлено"],
									["review", "На проверке"],
									["changes", "Нужны исправления"],
									["accepted", "Принято"],
								].map(([id, label]) => (
									<button
										key={id}
										type="button"
										className={
											assignmentFilter === id ? "filter selected" : "filter"
										}
										aria-pressed={assignmentFilter === id}
										onClick={() => setAssignmentFilter(id)}
									>
										{label}
									</button>
								))}
							</fieldset>
							{assignmentFilter === "all" ||
							assignmentFilter === state.submission.status ? (
								assignmentRow()
							) : (
								<section className="card empty-state">
									<Icon name="file" />
									<h2>Нет заданий с таким статусом</h2>
									<button
										type="button"
										className="button secondary"
										onClick={() => setAssignmentFilter("all")}
									>
										Показать все
									</button>
								</section>
							)}
						</>
					) : (
						<>
							{title(
								"Онлайн-занятия",
								"Встречаемся, разбираем задачи и задаём вопросы.",
							)}
							<div className="filters">
								{[
									["upcoming", "Предстоящие"],
									["past", "Завершённые"],
								].map(([id, label]) => (
									<button
										key={id}
										type="button"
										className={meetingTab === id ? "filter selected" : "filter"}
										aria-pressed={meetingTab === id}
										onClick={() => setMeetingTab(id)}
									>
										{label}
									</button>
								))}
							</div>
							{meetingTab === "upcoming" ? (
								<MeetingCard compact onDetails={() => setModal("meeting")} />
							) : (
								<section className="card empty-state">
									<Icon name="calendar" />
									<h2>Завершённых занятий пока нет</h2>
									<p>После окончания занятия появятся здесь.</p>
								</section>
							)}
						</>
					)}
				</main>
				<footer className="workspace-footer">
					<span>Advent Academy</span>
					<span>Каждый урок — шаг вперёд.</span>
				</footer>
			</div>
			{view !== "lesson" && (
				<nav
					className={`bottom-nav ${live?.canReview ? "with-review" : ""}`}
					aria-label="Мобильная навигация"
				>
					{nav.map((item) => (
						<Link
							key={item.id}
							href={item.id === "home" ? "/" : `/?view=${item.id}`}
							className={activeSection === item.id ? "active" : ""}
							aria-current={activeSection === item.id ? "page" : undefined}
						>
							<Icon name={item.icon} />
							<span>{item.short}</span>
						</Link>
					))}
					{live?.canReview && (
						<Link
							href="/?view=reviews"
							className={view === "reviews" ? "active" : ""}
						>
							<Icon name="file" />
							<span>Проверка</span>
						</Link>
					)}
				</nav>
			)}
			{modal === "program" && (
				<Modal
					title="Программа курса"
					sheet
					onClose={() => {
						const element = document.querySelector(".modal-content");
						if (element) programScroll.current = element.scrollTop;
						setModal(null);
					}}
				>
					<div
						ref={(element) => {
							if (element?.parentElement)
								element.parentElement.scrollTop = programScroll.current;
						}}
					>
						<h3 className="sheet-course-title">{course.title}</h3>
						<Progress course={course} state={state} />
						<Program course={course} state={state} selected={lesson.id} />
					</div>
				</Modal>
			)}
			{modal === "meeting" && (
				<Modal title="Онлайн-занятие" onClose={() => setModal(null)}>
					<span className="meeting-icon">
						<Icon name="calendar" />
					</span>
					<h3 className="meeting-modal-title">
						Разбор практических задач по Java
					</h3>
					<p className="meta">
						<Icon name="calendar" />
						18 ноября 2026 года
					</p>
					<p className="meta">
						<Icon name="clock" />
						19:00–20:30 МСК · UTC+3
					</p>
					<p className="muted">Длительность: 90 минут</p>
					<Badge>
						<Icon name="clock" />
						Ожидает начала
					</Badge>
					<p className="meeting-disclaimer">
						Демонстрационное занятие. Подключение появится после добавления
						реального расписания и ссылки.
					</p>
				</Modal>
			)}
		</div>
	);
}
