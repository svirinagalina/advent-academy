"use client";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
	type FormEvent,
	useCallback,
	useEffect,
	useRef,
	useState,
} from "react";
import Academy from "@/components/academy";
import Management from "@/components/management";
import UserAdmin from "@/components/user-admin";
import {
	AssignmentList,
	AssignmentPanel,
	ReviewPanel,
} from "@/components/assignments";
import { Meetings, NearestMeeting } from "@/components/meetings";
import {
	type AssignmentSummary,
	api,
	ClientError,
	type CourseView,
	type LessonView,
	type Profile,
	toDemo,
} from "@/lib/learning";

export default function LiveAcademy() {
	const search = useSearchParams();
	const sessionEpoch = useRef(0);
	const [profile, setProfile] = useState<Profile | null>(null);
	const [courses, setCourses] = useState<CourseView[]>([]);
	const [assignments, setAssignments] = useState<AssignmentSummary[]>([]);
	const [assignmentsLoading, setAssignmentsLoading] = useState(false);
	const [assignmentsError, setAssignmentsError] = useState("");
	const expired = useCallback(() => {
		sessionEpoch.current++;
		setProfile(null);
		setCourses([]);
		setAssignments([]);
		setLoginRequired(true);
	}, []);
	const refreshProgress = useCallback(async () => {
		const epoch = sessionEpoch.current;
		const [list, summary] = await Promise.all([
			api<CourseView[]>("/api/backend/learning/courses"),
			api<AssignmentSummary[]>("/api/backend/learning/assignments"),
		]);
		if (sessionEpoch.current !== epoch) return;
		setCourses(list);
		setAssignments(summary);
	}, []);
	const refreshAssignments = useCallback(async () => {
		setAssignmentsLoading(true);
		setAssignmentsError("");
		try {
			await refreshProgress();
		} catch (e) {
			if (e instanceof ClientError && e.status === 401) expired();
			else
				setAssignmentsError(
					e instanceof Error ? e.message : "Не удалось загрузить задания",
				);
		} finally {
			setAssignmentsLoading(false);
		}
	}, [refreshProgress, expired]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [loginRequired, setLoginRequired] = useState(false);
	const [lesson, setLesson] = useState<LessonView | null>(null);
	const [lessonError, setLessonError] = useState("");
	const [lessonLoading, setLessonLoading] = useState(false);
	const load = useCallback(async () => {
		const epoch = ++sessionEpoch.current;
		setLoading(true);
		setError("");
		try {
			const user = await api<Profile>("/api/session");
			const [list, summary] = await Promise.all([
				api<CourseView[]>("/api/backend/learning/courses"),
				api<AssignmentSummary[]>("/api/backend/learning/assignments"),
			]);
			if (sessionEpoch.current !== epoch) return;
			setAssignments(summary);
			setProfile(user);
			setCourses(list);
			setLoginRequired(false);
		} catch (e) {
			if (e instanceof ClientError && e.status === 401) {
				setProfile(null);
				setLoginRequired(true);
			} else
				setError(
					e instanceof Error ? e.message : "Не удалось загрузить кабинет",
				);
		} finally {
			setLoading(false);
		}
	}, []);
	useEffect(() => {
		void load();
	}, [load]);
	const view = search.get("view");
	const requestedCourse = search.get("course");
	const selected = requestedCourse
		? courses.find((c) => c.id === requestedCourse)
		: courses[0];
	const requestedLesson = search.get("lesson");
	const selectedLesson =
		requestedLesson ||
		selected?.currentLessonId ||
		selected?.modules[0]?.lessons[0]?.id;
	const courseId = selected?.id;
	useEffect(() => {
		let active = true;
		setLesson(null);
		setLessonError("");
		if (view !== "lesson" || !courseId || !selectedLesson) {
			setLessonLoading(false);
			return;
		}
		setLessonLoading(true);
		api<LessonView>(
			`/api/backend/learning/courses/${courseId}/lessons/${selectedLesson}`,
		)
			.then((value) => {
				if (active) {
					setLesson(value);
					setCourses((old) =>
						old.map((c) =>
							c.id === courseId && c.status === "new"
								? { ...c, status: "active" }
								: c,
						),
					);
				}
			})
			.catch((e) => {
				if (active) {
					if (e instanceof ClientError && e.status === 401)
						setLoginRequired(true);
					else
						setLessonError(
							e instanceof Error ? e.message : "Не удалось загрузить урок",
						);
				}
			})
			.finally(() => {
				if (active) setLessonLoading(false);
			});
		return () => {
			active = false;
		};
	}, [view, courseId, selectedLesson]);
	async function logout() {
		await api<void>("/api/session", { method: "DELETE" });
		sessionEpoch.current++;
		setProfile(null);
		setCourses([]);
		setAssignments([]);
		setLoginRequired(true);
	}
	if (loginRequired) return <Login onSuccess={load} />;
	if (loading || error || !profile)
		return (
			<main className="main-content">
				<section className="card empty-state">
					<h1>Advent Academy</h1>
					{loading ? (
						<p role="status">Загружаем кабинет…</p>
					) : (
						<>
							<p role="alert">{error}</p>
							<button
								className="button primary"
								type="button"
								onClick={() => void load()}
							>
								Повторить
							</button>
						</>
					)}
				</section>
			</main>
		);
	if (view === "users")
		return (
			<UserAdmin profile={profile} logout={logout} profileChanged={load} />
		);
	if (view === "manage")
		return <Management profile={profile} logout={logout} />;
	const adapted = toDemo(courses);
	// An empty structure keeps hooks stable without putting demonstration courses in the real UI.
	if (!adapted.courses.length)
		adapted.courses.push({
			id: "empty",
			title: "Курсы пока не назначены",
			description: "",
			total: 0,
			initialCompleted: 0,
			modules: [{ title: "", lessons: [{ id: "empty", title: "" }] }],
		});
	const invalid =
		(view === "course" || view === "lesson") &&
		(!selected ||
			(requestedLesson &&
				!selected.modules.some((module) =>
					module.lessons.some((l) => l.id === requestedLesson),
				)));
	return (
		<Academy
			live={{
				profile,
				...adapted,
				meetingList: <Meetings profile={profile} onExpired={expired} />,
				nearestMeeting: (
					<NearestMeeting profile={profile} onExpired={expired} />
				),
				assignmentPanel:
					selected && selectedLesson && lesson?.assignment ? (
						<AssignmentPanel
							key={`${profile.id}/${selected.id}/${selectedLesson}`}
							userId={profile.id}
							courseId={selected.id}
							lessonId={selectedLesson}
							onChanged={refreshProgress}
							onExpired={expired}
						/>
					) : null,
				assignmentList: (
					<AssignmentList
						items={assignments}
						loading={assignmentsLoading}
						error={assignmentsError}
						onRefresh={refreshAssignments}
					/>
				),
				attention: (
					<AssignmentList
						items={assignments}
						loading={assignmentsLoading}
						error={assignmentsError}
						onRefresh={refreshAssignments}
						attention
					/>
				),
				canReview: profile.roles.some((role) =>
					["ADMIN", "CONTENT_EDITOR", "INSTRUCTOR"].includes(role),
				),
				reviewPanel: <ReviewPanel onExpired={expired} />,
				lesson: lesson?.id === selectedLesson ? lesson : null,
				lessonError: invalid ? "Курс или урок недоступен" : lessonError,
				lessonLoading,
				complete: async (id, lessonId) => {
					const epoch = sessionEpoch.current;
					try {
						const updated = await api<CourseView>(
							`/api/backend/learning/courses/${id}/lessons/${lessonId}/complete`,
							{ method: "POST" },
						);
						if (sessionEpoch.current !== epoch) return;
						setCourses((old) => old.map((c) => (c.id === id ? updated : c)));
						try {
							const summary = await api<AssignmentSummary[]>(
								"/api/backend/learning/assignments",
							);
							if (sessionEpoch.current === epoch) {
								setAssignments(summary);
								setAssignmentsError("");
							}
						} catch (error) {
							if (sessionEpoch.current === epoch) {
								if (error instanceof ClientError && error.status === 401)
									expired();
								else
									setAssignmentsError(
										"Урок завершён. Обновите задания, чтобы увидеть доступные работы.",
									);
							}
						}
					} catch (e) {
						if (e instanceof ClientError && e.status === 401)
							setLoginRequired(true);
						throw e;
					}
				},
				logout,
			}}
			empty={!courses.length}
			invalidRoute={Boolean(invalid)}
		/>
	);
}
function Login({ onSuccess }: { onSuccess: () => Promise<void> }) {
	const [email, setEmail] = useState("");
	const [password, setPassword] = useState("");
	const [code, setCode] = useState("");
	const [mfa, setMfa] = useState(false);
	const [error, setError] = useState("");
	const [busy, setBusy] = useState(false);
	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		setBusy(true);
		setError("");
		try {
			const result = await api<{ mfaRequired: boolean }>("/api/session", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ email, password, mfaCode: mfa ? code : null }),
			});
			if (result.mfaRequired) setMfa(true);
			else {
				setPassword("");
				setCode("");
				await onSuccess();
			}
		} catch (e) {
			setError(e instanceof Error ? e.message : "Не удалось войти");
		} finally {
			setBusy(false);
		}
	}
	return (
		<main className="main-content auth-screen">
			<section className="card demo-login auth-card">
				<p className="eyebrow">Advent Academy</p>
				<h1>Войти в кабинет</h1>
				<form onSubmit={submit} aria-busy={busy}>
					<label htmlFor="email">Email</label>
					<input
						id="email"
						type="email"
						autoComplete="username"
						required
						value={email}
						onChange={(e) => {
							setEmail(e.target.value);
							setMfa(false);
							setCode("");
						}}
					/>
					<label htmlFor="password">Пароль</label>
					<input
						id="password"
						type="password"
						autoComplete="current-password"
						required
						value={password}
						onChange={(e) => setPassword(e.target.value)}
					/>
					{mfa && (
						<>
							<label htmlFor="mfa">Код приложения или код восстановления</label>
							<input
								id="mfa"
								autoComplete="one-time-code"
								required
								value={code}
								onChange={(e) => setCode(e.target.value)}
							/>
						</>
					)}
					{error && (
						<p role="alert" className="field-error">
							{error}
						</p>
					)}
					<button className="button primary full" type="submit" disabled={busy}>
						{busy ? "Входим…" : "Войти"}
					</button>
				</form>
				<Link className="text-link" href="/demo">
					Посмотреть демоверсию
				</Link>
			</section>
		</main>
	);
}
