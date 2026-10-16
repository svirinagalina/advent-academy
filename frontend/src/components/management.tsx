"use client";
import Link from "next/link";
import dynamic from "next/dynamic";
import type { Revision } from "@/components/lesson-editor";
import { useSearchParams } from "next/navigation";
import {
	type FormEvent,
	type ReactNode,
	useCallback,
	useEffect,
	useRef,
	useState,
} from "react";
import { ThemeToggle } from "@/components/theme";
import { ClientError, type Profile } from "@/lib/learning";
import {
	type Component,
	type CourseTree,
	type LessonNode,
	type ManagedCourse,
	type ModuleNode,
	managementError,
	request,
	type Status,
	type Student,
	statusLabel,
} from "@/lib/management";

const LessonEditor = dynamic(() => import("@/components/lesson-editor"), { ssr: false });

function Badge({ status }: { status: Status }) {
	return (
		<span className={`badge ${status === "PUBLISHED" ? "success" : ""}`}>
			<span aria-hidden="true">
				{status === "PUBLISHED" ? "✓" : status === "ARCHIVED" ? "▣" : "✎"}
			</span>
			{statusLabel[status]}
		</span>
	);
}
function Alert({ children }: { children: ReactNode }) {
	return (
		<p className="management-alert" role="alert">
			{children}
		</p>
	);
}
function Dialog({
	title,
	busy,
	close,
	children,
}: {
	title: string;
	busy: boolean;
	close: () => void;
	children: ReactNode;
}) {
	const ref = useRef<HTMLDialogElement>(null);
	useEffect(() => {
		const dialog = ref.current;
		const previous = document.activeElement as HTMLElement | null;
		dialog?.showModal();
		const old = document.body.style.overflow;
		document.body.style.overflow = "hidden";
		return () => {
			dialog?.close();
			document.body.style.overflow = old;
			previous?.focus();
		};
	}, []);
	return (
		<dialog
			ref={ref}
			onKeyDown={event => {if(event.key === "Escape"){event.preventDefault();if(!busy)close();}}}
			className="management-dialog"
			aria-labelledby="dialog-title"
			onCancel={(event) => {
				event.preventDefault();
				if (!busy) close();
			}}
			onClick={(event) => {
				if (event.target === ref.current && !busy) {
					const r = ref.current.getBoundingClientRect();
					if (
						event.clientX < r.left ||
						event.clientX > r.right ||
						event.clientY < r.top ||
						event.clientY > r.bottom
					)
						close();
				}
			}}
		>
			<div className="management-heading">
				<h2 id="dialog-title">{title}</h2>
				<button
					className="icon-button"
					type="button"
					disabled={busy}
					aria-label="Закрыть окно"
					onClick={close}
				>
					×
				</button>
			</div>
			{children}
		</dialog>
	);
}
type FormKind = "course" | "edit" | "module" | "lesson";
function Editor({
	kind,
	initial,
	close,
	submit,
}: {
	kind: FormKind;
	initial?: ManagedCourse;
	close: () => void;
	submit: (title: string, description: string) => Promise<void>;
}) {
	const [title, setTitle] = useState(initial?.title || "");
	const [description, setDescription] = useState(initial?.description || "");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const titles = {
		course: "Создать курс",
		edit: "Изменить название и описание",
		module: "Создать модуль",
		lesson: "Создать урок",
	};
	async function save(event: FormEvent) {
		event.preventDefault();
		if (!title.trim()) {
			setError("Введите название");
			return;
		}
		setBusy(true);
		setError("");
		try {
			await submit(title, description);
		} catch (e) {
			setError(managementError(e));
		} finally {
			setBusy(false);
		}
	}
	return (
		<Dialog title={titles[kind]} busy={busy} close={close}>
			<form className="management-form" onSubmit={save} aria-busy={busy}>
				<label htmlFor="management-title">
					{kind === "lesson"
						? "Название урока"
						: kind === "module"
							? "Название модуля"
							: "Название курса"}
				</label>
				<input
					id="management-title"
					required
					maxLength={kind === "course" || kind === "edit" ? 255 : undefined}
					value={title}
					onChange={(e) => setTitle(e.target.value)}
					aria-describedby={error ? "form-error" : undefined}
				/>
				{kind !== "lesson" && (
					<>
						<label htmlFor="management-description">Описание</label>
						<textarea
							id="management-description"
							value={description}
							onChange={(e) => setDescription(e.target.value)}
						/>
					</>
				)}
				{error && (
					<div id="form-error">
						<Alert>{error}</Alert>
					</div>
				)}
				<div className="management-actions">
					<button
						className="button secondary"
						type="button"
						disabled={busy}
						onClick={close}
					>
						Отмена
					</button>
					<button className="button primary" type="submit" disabled={busy}>
						{busy
							? "Сохраняем…"
							: kind === "edit"
								? "Сохранить изменения"
								: titles[kind]}
					</button>
				</div>
			</form>
		</Dialog>
	);
}
function ModulePicker({
	course,
	close,
	added,
}: {
	course: CourseTree;
	close: () => void;
	added: () => Promise<void>;
}) {
	const [modules, setModules] = useState<Component[]>([]);
	const [selected, setSelected] = useState("");
	const [busy, setBusy] = useState(false);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const load = useCallback(async () => {
		setLoading(true);
		setError("");
		try {
			setModules(await request<Component[]>("components"));
		} catch (e) {
			setError(managementError(e));
		} finally {
			setLoading(false);
		}
	}, []);
	useEffect(() => {
		void load();
	}, [load]);
	async function add(event: FormEvent) {
		event.preventDefault();
		setBusy(true);
		setError("");
		try {
			await request(`courses/${course.id}/components`, "POST", {
				componentId: selected,
			});
			await added();
			close();
		} catch (e) {
			setError(managementError(e));
		} finally {
			setBusy(false);
		}
	}
	return (
		<Dialog title="Добавить существующий модуль" busy={busy} close={close}>
			{loading ? (
				<p role="status">Загружаем модули…</p>
			) : (
				<form className="management-form" onSubmit={add}>
					<label htmlFor="module-selection">Модуль</label>
					<select
						id="module-selection"
						value={selected}
						required
						onChange={(e) => setSelected(e.target.value)}
					>
						<option value="">Выберите модуль</option>
						{modules.map((m) => {
							const included = course.components.some(
								(x) => x.componentId === m.id,
							);
							return (
								<option key={m.id} value={m.id} disabled={included}>
									{m.title} · {statusLabel[m.status]}
									{included ? " · Уже в программе" : ""}
								</option>
							);
						})}
					</select>
					{!modules.length && <p>Нет доступных модулей</p>}
					{error && <Alert>{error}</Alert>}
					<div className="management-actions">
						<button
							className="button secondary"
							type="button"
							disabled={busy}
							onClick={close}
						>
							Отмена
						</button>
						<button
							className="button primary"
							disabled={busy || !selected}
							type="submit"
						>
							{busy ? "Добавляем…" : "Добавить в программу"}
						</button>
						{error && (
							<button
								className="button secondary"
								type="button"
								onClick={() => void load()}
							>
								Обновить список
							</button>
						)}
					</div>
				</form>
			)}
		</Dialog>
	);
}
export default function Management({
	profile,
	logout,
}: {
	profile: Profile;
	logout: () => Promise<void>;
}) {
	const search = useSearchParams();
	const courseId = search.get("course");
	const curator = profile.roles.some((r) =>
		["ADMIN", "CONTENT_EDITOR", "ROLE_ADMIN", "ROLE_CONTENT_EDITOR"].includes(
			r,
		),
	);
	const allowed =
		curator ||
		profile.roles.some((r) => ["INSTRUCTOR", "ROLE_INSTRUCTOR"].includes(r));
	const [courses, setCourses] = useState<ManagedCourse[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [filter, setFilter] = useState("ALL");
	const [create, setCreate] = useState(false);
	const load = useCallback(async () => {
		if (!allowed) return;
		setLoading(true);
		setError("");
		try {
			setCourses(await request<ManagedCourse[]>("courses?managed=true"));
		} catch (e) {
			setError(managementError(e));
		} finally {
			setLoading(false);
		}
	}, [allowed]);
	useEffect(() => {
		void load();
	}, [load]);
	const selected = courses.find((c) => c.id === courseId);
	return (
		<div className="management-shell">
			<aside className="sidebar">
				<Link className="brand" href="/?view=manage">
					Advent
					<br />
					Academy
				</Link>
				<nav>
					<Link className="nav-link active" href="/?view=manage">
						▤ Управление курсами
					</Link>
					<Link className="nav-link" href="/">
						Кабинет ученика
					</Link>
				</nav>
			</aside>
			<div className="workspace">
				<header className="topbar">
					<Link href="/?view=manage" className="management-brand">
						Advent Academy
					</Link>
					<div className="header-actions">
						<ThemeToggle />
						<div className="management-profile">
							<strong>{profile.firstName}</strong>
							<small>
								{profile.roles.some((r) => r === "ADMIN" || r === "ROLE_ADMIN")
									? "Администратор"
									: curator
										? "Редактор контента"
										: "Преподаватель"}
							</small>
						</div>
						<button
							className="button secondary"
							type="button"
							onClick={() => void logout()}
						>
							Выйти
						</button>
					</div>
				</header>
				<main className="main-content management-content">
					{!allowed ? (
						<section className="card empty-state">
							<h1>Нет доступа к управлению курсами</h1>
							<Link className="text-link" href="/">
								В кабинет ученика
							</Link>
						</section>
					) : loading ? (
						<section className="card empty-state" aria-busy="true">
							<p role="status">Загружаем курсы…</p>
							<div className="management-skeleton" />
						</section>
					) : error ? (
						<section className="card empty-state">
							<Alert>{error}</Alert>
							<button
								className="button secondary"
								type="button"
								onClick={() => void load()}
							>
								Повторить
							</button>
							<Link className="text-link" href="/">
								Войти заново / кабинет ученика
							</Link>
						</section>
					) : courseId && !selected ? (
						<section className="card empty-state">
							<h1>Нет доступа к этому курсу</h1>
							<Link className="text-link" href="/?view=manage">
								К доступным курсам
							</Link>
						</section>
					) : selected ? (
						<CourseCard
							key={selected.id}
							id={selected.id}
							profile={profile}
							curator={curator}
							listChanged={load}
						/>
					) : (
						<>
							<div className="management-heading">
								<div>
									<p className="eyebrow">Кабинет управления</p>
									<h1>{curator ? "Курсы" : "Доступные курсы"}</h1>
								</div>
								{curator && (
									<button
										className="button primary"
										type="button"
										onClick={() => setCreate(true)}
									>
										+ Создать курс
									</button>
								)}
							</div>
							<fieldset
								className="management-actions"
								aria-label="Фильтр курсов"
							>
								{["ALL", "DRAFT", "PUBLISHED", "ARCHIVED"].map((value) => (
									<button
										className={`button ${filter === value ? "primary" : "secondary"}`}
										type="button"
										key={value}
										aria-pressed={filter === value}
										onClick={() => setFilter(value)}
									>
										{value === "ALL" ? "Все" : statusLabel[value as Status]}
									</button>
								))}
							</fieldset>
							<div className="management-course-list">
								{courses
									.filter((c) => filter === "ALL" || c.status === filter)
									.map((c) => (
										<Link
											className="card management-course"
											key={c.id}
											href={`/?view=manage&course=${c.id}`}
										>
											<div>
												<h2>{c.title}</h2>
												<p>{c.description}</p>
											</div>
											<Badge status={c.status} />
											<span className="text-link">Открыть курс →</span>
										</Link>
									))}
							</div>
							{!courses.length ? (
								<section className="card empty-state">
									<h2>
										{curator ? "Курсов пока нет" : "Нет доступных курсов"}
									</h2>
									<p>
										{curator
											? "Создайте первый курс, чтобы начать."
											: "Здесь появятся курсы, к которым вам предоставлен доступ."}
									</p>
								</section>
							) : (
								!courses.some(
									(c) => filter === "ALL" || c.status === filter,
								) && <p>В этой категории пока нет курсов</p>
							)}
						</>
					)}
					{create && (
						<Editor
							kind="course"
							close={() => setCreate(false)}
							submit={async (title, description) => {
								const created = await request<ManagedCourse>(
									"courses",
									"POST",
									{ title, description },
								);
								setCreate(false);
								await load();
								window.history.pushState(
									null,
									"",
									`/?view=manage&course=${created.id}`,
								);
							}}
						/>
					)}
				</main>
			</div>
		</div>
	);
}
function CourseCard({
	id,
	profile,
	curator,
	listChanged,
}: {
	id: string;
	profile: Profile;
	curator: boolean;
	listChanged: () => Promise<void>;
}) {
	const [course, setCourse] = useState<CourseTree | null>(null);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);
	const [busy, setBusy] = useState(false);
	const [tab, setTab] = useState("program");
	const [notice, setNotice] = useState("");
	const [form, setForm] = useState<FormKind | null>(null);
	const [moduleId, setModuleId] = useState("");
	const [picker, setPicker] = useState(false);
	const [returnModule, setReturnModule] = useState("");
	const [material, setMaterial] = useState<{
		lesson: LessonNode;
		module: ModuleNode;
	} | null>(null);
	const [confirm, setConfirm] = useState<{
		label: string;
		detail: string;
		run: () => Promise<unknown>;
	} | null>(null);
	const load = useCallback(async () => {
		const value = await request<CourseTree>(`courses/${id}`);
		setCourse(value);
	}, [id]);
	useEffect(() => {
		let active = true;
		request<CourseTree>(`courses/${id}`)
			.then((c) => {
				if (active) setCourse(c);
			})
			.catch((e) => {
				if (active) setError(managementError(e));
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [id]);
	async function mutate(
		action: () => Promise<unknown>,
		message = "Изменения сохранены",
	) {
		if (busy) return false;
		setBusy(true);
		setError("");
		setNotice("");
		try {
			await action();
			await load();
			setNotice(message);
			return true;
		} catch (e) {
			setError(managementError(e));
			try {
				await load();
			} catch {}
			return false;
		} finally {
			setBusy(false);
		}
	}
	if (loading) return <p role="status">Загружаем программу…</p>;
	if (!course)
		return (
			<>
				<Alert>{error}</Alert>
				<Link className="text-link" href="/?view=manage">
					К доступным курсам
				</Link>
			</>
		);
	const draft = course.status === "DRAFT";
	const blockers = course.components.filter((c) => c.status !== "PUBLISHED");
	const mayManage = (m: ModuleNode) => curator || m.ownerId === profile.id;
	if (material)
		return (
			<MaterialPanel
				key={material.lesson.id}
				lesson={material.lesson}
				module={material.module}
				course={course}
				canManage={mayManage(material.module)}
				back={() => {
					setReturnModule(material.module.componentId);
					setMaterial(null);
					void load();
				}}
				changed={load}
			/>
		);
	return (
		<>
			<Link className="text-link" href="/?view=manage">
				← Курсы
			</Link>
			<div className="management-heading">
				<h1>{course.title}</h1>
				<Badge status={course.status} />
			</div>
			{error && <Alert>{error}</Alert>}
			{notice && (
				<p className="management-success" role="status">
					{notice}
				</p>
			)}
			<div className="management-actions">
				{draft && (
					<button
						className="button primary"
						type="button"
						disabled={busy || !course.components.length || !!blockers.length}
						onClick={() =>
							void mutate(
								() => request(`courses/${id}/publish`, "POST"),
								"Курс опубликован",
							)
						}
					>
						{busy ? "Сохраняем…" : "Опубликовать курс"}
					</button>
				)}
				{course.status === "PUBLISHED" && (
					<button
						className="button secondary"
						type="button"
						disabled={busy}
						onClick={() =>
							setConfirm({
								label: "Архивировать курс",
								detail: `Архивировать курс «${course.title}»? Курс станет недоступен ученикам; назначения сохранятся.`,
								run: () => request(`courses/${id}/archive`, "POST"),
							})
						}
					>
						Архивировать курс
					</button>
				)}
				<button
					className="button secondary"
					type="button"
					disabled={busy}
					onClick={() => void mutate(load, "Данные обновлены")}
				>
					Обновить курс
				</button>
			</div>
			{draft && !course.components.length && (
				<p className="management-warning">
					Добавьте модуль в программу перед публикацией.
				</p>
			)}
			{draft && blockers.length > 0 && (
				<div className="management-warning">
					<p>Не опубликованы модули программы</p>
					{blockers.map((m) => (
						<a
							className="text-link"
							key={m.componentId}
							href={`#module-${m.componentId}`}
							onClick={() => {
								const element = document.getElementById(
									`module-${m.componentId}`,
								) as HTMLDetailsElement | null;
								if (element) {
									element.open = true;
									element.focus();
								}
							}}
						>
							К модулю «{m.title}» →
						</a>
					))}
				</div>
			)}
			<div className="management-tabs" role="tablist" aria-label="Курс">
				{[
					{ id: "program", label: "Программа" },
					{ id: "students", label: "Ученики" },
				].map((item, index) => (
					<button
						key={item.id}
						id={`tab-${item.id}`}
						type="button"
						role="tab"
						aria-selected={tab === item.id}
						aria-controls={`panel-${item.id}`}
						tabIndex={tab === item.id ? 0 : -1}
						onClick={() => setTab(item.id)}
						onKeyDown={(e) => {
							if (["ArrowLeft", "ArrowRight", "Home", "End"].includes(e.key)) {
								e.preventDefault();
								const next =
									e.key === "Home"
										? "program"
										: e.key === "End"
											? "students"
											: index === 0
												? "students"
												: "program";
								setTab(next);
								document.getElementById(`tab-${next}`)?.focus();
							}
						}}
					>
						{item.label}
					</button>
				))}
			</div>
			{tab === "students" ? (
				<section
					role="tabpanel"
					id="panel-students"
					aria-labelledby="tab-students"
				>
					<Students course={course} stale={load} />
				</section>
			) : (
				<section
					role="tabpanel"
					id="panel-program"
					aria-labelledby="tab-program"
					className="management-program"
				>
					<div className="card management-description">
						<div className="management-heading">
							<h2>Описание курса</h2>
							{draft && (
								<button
									className="button secondary"
									type="button"
									disabled={busy}
									onClick={() => setForm("edit")}
								>
									Изменить название и описание
								</button>
							)}
						</div>
						<p>{course.description || "Описание пока не добавлено"}</p>
					</div>
					<h2>Программа курса</h2>
					{draft && (
						<div className="management-actions">
							<button
								className="button secondary"
								type="button"
								disabled={busy}
								onClick={() => setPicker(true)}
							>
								+ Добавить существующий модуль
							</button>
							{curator && (
								<button
									className="button secondary"
									type="button"
									disabled={busy}
									onClick={() => setForm("module")}
								>
									+ Создать модуль
								</button>
							)}
						</div>
					)}
					{course.status === "ARCHIVED" && (
						<p className="management-warning">
							Курс в архиве. Программа доступна только для просмотра.
						</p>
					)}
					{course.components.map((m, index) => {
						const unpublished = m.lessons.filter(
							(l) => l.contentStatus !== "PUBLISHED",
						);
						return (
							<details
								className="card management-module"
								id={`module-${m.componentId}`}
								key={m.componentId}
								open={index === 0 || returnModule === m.componentId}
								tabIndex={-1}
							>
								<summary>
									<strong>
										{index + 1}. {m.title}
									</strong>
									<Badge status={m.status} />
								</summary>
								{draft && (
									<div className="management-actions">
										<button
											className="button secondary"
											type="button"
											disabled={busy || index === 0}
											onClick={() => {
												const ids = course.components.map((x) => x.componentId);
												[ids[index - 1], ids[index]] = [
													ids[index],
													ids[index - 1],
												];
												void mutate(() =>
													request(`courses/${id}/components/order`, "PUT", {
														orderedIds: ids,
													}),
												);
											}}
										>
											↑ Выше
										</button>
										<button
											className="button secondary"
											type="button"
											disabled={busy || index === course.components.length - 1}
											onClick={() => {
												const ids = course.components.map((x) => x.componentId);
												[ids[index + 1], ids[index]] = [
													ids[index],
													ids[index + 1],
												];
												void mutate(() =>
													request(`courses/${id}/components/order`, "PUT", {
														orderedIds: ids,
													}),
												);
											}}
										>
											↓ Ниже
										</button>
										<button
											className="button secondary"
											type="button"
											disabled={busy}
											onClick={() =>
												setConfirm({
													label: "Убрать из программы",
													detail: `Убрать модуль «${m.title}» из программы курса? Модуль и его уроки сохранятся.`,
													run: () =>
														request(
															`courses/${id}/components/${m.componentId}`,
															"DELETE",
														),
												})
											}
										>
											Убрать из программы
										</button>
									</div>
								)}
								{m.lessons.map((l) => (
									<div className="management-lesson" key={l.id}>
										<strong>{l.title}</strong>
										<span className="badge">
											{l.contentPresent
												? `Материал: ${statusLabel[l.contentStatus as Status]}`
												: "Материал не загружен"}
										</span>
										<button
											className="button secondary"
											type="button"
											onClick={() => setMaterial({ lesson: l, module: m })}
										>
											Редактировать содержимое
										</button>
									</div>
								))}
								{draft && mayManage(m) && m.status === "DRAFT" && (
									<>
										<button
											className="button secondary"
											type="button"
											disabled={busy}
											onClick={() => {
												setModuleId(m.componentId);
												setForm("lesson");
											}}
										>
											+ Создать урок
										</button>
										<button
											className="button primary"
											type="button"
											disabled={
												busy || !m.lessons.length || !!unpublished.length
											}
											onClick={() =>
												void mutate(
													() =>
														request(
															`components/${m.componentId}/publish`,
															"POST",
														),
													"Модуль опубликован",
												)
											}
										>
											Опубликовать модуль
										</button>
										{!m.lessons.length && (
											<p className="management-warning">
												Сначала создайте урок и опубликуйте его материал.
											</p>
										)}
										{unpublished.length > 0 && (
											<div className="management-warning">
												{unpublished.map((l) => (
													<p key={l.id}>
														Материал урока «{l.title}» не опубликован.{" "}
														<button
															className="text-link"
															type="button"
															onClick={() =>
																setMaterial({ lesson: l, module: m })
															}
														>
															Открыть материал урока →
														</button>
													</p>
												))}
											</div>
										)}
									</>
								)}
								{!mayManage(m) && (
									<p className="muted">
										Общий модуль. Управление материалами доступно его владельцу,
										редактору и администратору.
									</p>
								)}
							</details>
						);
					})}
				</section>
			)}
			{picker && (
				<ModulePicker
					course={course}
					close={() => setPicker(false)}
					added={load}
				/>
			)}
			{form && (
				<Editor
					kind={form}
					initial={form === "edit" ? course : undefined}
					close={() => setForm(null)}
					submit={async (title, description) => {
						if (form === "edit")
							await request(`courses/${id}`, "PATCH", { title, description });
						else if (form === "module") {
							await request("components", "POST", { title, description });
							setNotice(
								"Модуль создан. Добавьте его в программу через выбор существующего модуля.",
							);
						} else {
							const currentModule = course.components.find(
								(m) => m.componentId === moduleId,
							);
							const updated = await request<Component>(
								`components/${moduleId}/lessons`,
								"POST",
								{ title },
							);
							const created = updated.lessons.find(
								(l) => !currentModule?.lessons.some((old) => old.id === l.id),
							);
							if (currentModule && created)
								setMaterial({
									module: currentModule,
									lesson: {
										...created,
										position: updated.lessons.indexOf(created),
										contentPresent: false,
										contentStatus: null,
									},
								});
						}
						setForm(null);
						await load();
						if (form === "module") setPicker(true);
					}}
				/>
			)}
			{confirm && (
				<Dialog
					title={confirm.label}
					busy={busy}
					close={() => setConfirm(null)}
				>
					<p>{confirm.detail}</p>
                    {error && <Alert>{error}</Alert>}
					<div className="management-actions">
						<button
							className="button secondary"
							type="button"
							disabled={busy}
							onClick={() => setConfirm(null)}
						>
							Отмена
						</button>
						<button
							className="button primary"
							type="button"
							disabled={busy}
							onClick={() => {
								void mutate(
									confirm.run,
									confirm.label === "Архивировать курс"
										? "Курс архивирован"
										: "Модуль убран из программы",
								).then((success) => {
									if (!success) return;
									setConfirm(null);
									if (confirm.label === "Архивировать курс") void listChanged();
								});
							}}
						>
							{busy ? "Сохраняем…" : confirm.label}
						</button>
					</div>
				</Dialog>
			)}
		</>
	);
}
function MaterialPanel({ lesson, module, course, canManage, back, changed }: {
    lesson: LessonNode; module: ModuleNode; course: CourseTree; canManage: boolean;
    back: () => void; changed: () => Promise<void>;
}) {
    const [material, setMaterial] = useState<Revision | null>(null);
    const [editing, setEditing] = useState<Revision | null>(null);
    const [loading, setLoading] = useState(true);
    const [file, setFile] = useState<File | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const opened = useRef(false);
    const editButton = useRef<HTMLButtonElement>(null);
    const path = `lessons/${lesson.id}/content/draft`;
    const load = useCallback(async () => {
        setLoading(true); setError("");
        try {
            const value = await request<Revision>(path); setMaterial(value);
            if (canManage && !opened.current) { opened.current = true; setEditing(value); }
        }
        catch (e) { setError(managementError(e)); }
        finally { setLoading(false); }
    }, [path, canManage]);
    useEffect(() => { void load(); }, [load]);
    async function upload(event: FormEvent) {
        event.preventDefault();
        if (!file || busy) return;
        if (file.size > 5 * 1024 * 1024) { setError("Файл слишком большой. Максимум 5 MiB."); return; }
        if (!/\.(md|markdown|zip)$/i.test(file.name)) { setError("Выберите файл .md или ZIP с Markdown и вложениями"); return; }
        setBusy(true); setError(""); setNotice("");
        const body = new FormData(); body.append("file", file);
        try {
            await import("@/lib/learning").then(({ api }) => api(`/api/backend/lessons/${lesson.id}/content`, { method: "POST", body }));
            await load(); await changed(); setFile(null);
            setNotice("Материал загружен. HTML подготовлен и сохранён как черновик.");
        } catch (e) {
            // A failed response may follow a committed upload. Read back without discarding any editor text.
            try {
                const value = await request<Revision>(path); setMaterial(value);
                setError(value.version > 0 ? "Материал уже существует. Откройте редактор и проверьте сохранённый текст." : managementError(e));
            } catch { setError(managementError(e)); }
        } finally { setBusy(false); }
    }
    async function closeEditor() {
        setEditing(null);
        await load();
        requestAnimationFrame(() => editButton.current?.focus());
    }
    async function publish() {
        if (!material) return;
        setBusy(true); setError("");
        try {
            setMaterial(await request<Revision>(`${path}/publish`, "POST", { version: material.version }));
            await changed(); setNotice("Материал опубликован. Модуль и курс публикуются отдельно.");
        } catch (e) { setError(managementError(e)); }
        finally { setBusy(false); }
    }
    return <section className="management-material">
        <button className="text-link" type="button" disabled={busy} onClick={back}>← К программе курса</button>
        <p className="eyebrow">{course.title} / {module.title}</p><h1>{lesson.title}</h1>
        {error && <Alert>{error}</Alert>}
        {notice && <p className="management-success" role="status">{notice}</p>}
        {loading ? <p role="status">Загружаем материал…</p> : material ? <>
            <div className="management-actions">
                <Badge status={material.published ? "PUBLISHED" : "DRAFT"} />
                {canManage && <button ref={editButton} className="button primary" type="button" disabled={busy} onClick={() => setEditing(material)}>Редактировать содержимое</button>}
                {canManage && material.markdown.trim() && <button className="button secondary" type="button" disabled={busy} onClick={() => void publish()}>Опубликовать материал</button>}
                <button className="button secondary" type="button" disabled={busy} onClick={() => void load()}>Обновить материал</button>
            </div>
            <p>Сохранение черновика не изменяет опубликованный материал. Модуль и курс публикуются отдельно.</p>
            {material.version > 0 ? <section className="card management-preview"><h2>Предпросмотр HTML</h2><div className="lesson-article lesson-material"
                // biome-ignore lint/security/noDangerouslySetInnerHtml: authorized HTML sanitized by content-service.
                dangerouslySetInnerHTML={{ __html: material.html.replaceAll("/api/assets/", "/api/backend/assets/") }} /></section> : canManage && <form className="card management-form" onSubmit={upload}>
                <h2>Импорт материала урока</h2><label htmlFor="lesson-file">Markdown или ZIP с Markdown и вложениями</label>
                <input id="lesson-file" type="file" accept=".md,.markdown,.zip" disabled={busy} onChange={e => setFile(e.target.files?.[0] || null)} />
                <p>Файл до 5 MiB. ZIP: один Markdown-файл в корне, вложения в assets/.</p>
                <button className="button primary" type="submit" disabled={busy || !file}>{busy ? "Загружаем…" : "Загрузить материал"}</button>
            </form>}
        </> : <button className="button secondary" type="button" onClick={() => void load()}>Повторить</button>}
        {editing && <LessonEditor lessonId={lesson.id} title={lesson.title} initial={editing} close={() => void closeEditor()} changed={changed} />}
    </section>;
}
function Students({
	course,
	stale,
}: {
	course: CourseTree;
	stale: () => Promise<void>;
}) {
	const [students, setStudents] = useState<Student[]>([]);
	const [rosterReady, setRosterReady] = useState(false);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [query, setQuery] = useState("");
	const [results, setResults] = useState<Student[]>([]);
	const [searching, setSearching] = useState(false);
	const [searchError, setSearchError] = useState("");
	const [attempt, setAttempt] = useState(0);
	const [busy, setBusy] = useState("");
	const [assignError, setAssignError] = useState<{
		id: string;
		message: string;
	} | null>(null);
	const [notice, setNotice] = useState("");
	const published = course.status === "PUBLISHED";
	const load = useCallback(async () => {
		setLoading(true);
		setError("");
		setRosterReady(false);
		if (course.status === "DRAFT") {
			setLoading(false);
			return;
		}
		try {
			setStudents(
				await request<Student[]>(
					`users/teaching/courses/${course.id}/students`,
				),
			);
			setRosterReady(true);
		} catch (e) {
			setError(managementError(e));
		} finally {
			setLoading(false);
		}
	}, [course.id, course.status]);
	useEffect(() => {
		void load();
	}, [load]);
	// biome-ignore lint/correctness/useExhaustiveDependencies: attempt explicitly retries the same search query.
	useEffect(() => {
		let active = true;
		setResults([]);
		setSearchError("");
		const term = query.trim();
		if (!published || term.length < 2) {
			setSearching(false);
			return;
		}
		setSearching(true);
		const timer = setTimeout(() => {
			request<Student[]>(
				`users/teaching/courses/${course.id}/candidates?query=${encodeURIComponent(term)}&limit=20`,
			)
				.then((list) => {
					if (active) setResults(list);
				})
				.catch((e) => {
					if (active) {
						setSearchError(managementError(e));
						if (e instanceof ClientError && e.status === 403) void stale();
					}
				})
				.finally(() => {
					if (active) setSearching(false);
				});
		}, 300);
		return () => {
			active = false;
			clearTimeout(timer);
		};
	}, [course.id, published, query, attempt, stale]);
	async function assign(student: Student) {
		if (busy) return;
		setBusy(student.id);
		setAssignError(null);
		setNotice("");
		try {
			await request(
				`learning/courses/${course.id}/students/${student.id}`,
				"PUT",
			);
			setStudents((old) =>
				old.some((s) => s.id === student.id) ? old : [...old, student],
			);
			setNotice(
				`${student.firstName} ${student.lastName} назначен(а) на курс.`,
			);
		} catch (e) {
			setAssignError({ id: student.id, message: e instanceof ClientError && e.status === 422 ? "Пользователь больше недоступен для назначения. Обновите результаты поиска." : managementError(e) });
			if (e instanceof ClientError && (e.status === 403 || e.status === 409))
				void stale();
		} finally {
			setBusy("");
		}
	}
	return (
		<div className="management-students">
			<h2>Назначенные ученики</h2>
			{loading ? (
				<p role="status">Загружаем учеников…</p>
			) : error ? (
				<>
					<Alert>{error}</Alert>
					<button
						className="button secondary"
						type="button"
						onClick={() => void load()}
					>
						Повторить
					</button>
				</>
			) : !students.length ? (
				<p>На курс пока никто не назначен</p>
			) : (
				<ul className="management-people">
					{students.map((s) => (
						<li key={s.id}>
							<strong>
								{s.firstName} {s.lastName}
							</strong>
							<span>{s.email}</span>
						</li>
					))}
				</ul>
			)}
			{!published ? (
				<p className="management-warning">
					{course.status === "DRAFT"
						? "Назначение учеников станет доступно после публикации курса"
						: "Курс в архиве. Поиск и назначение учеников недоступны."}
				</p>
			) : (
				<section className="card management-search">
					<h2>Назначить ученика</h2>
					<label htmlFor="candidate-query">Имя или email</label>
					<input
						id="candidate-query"
						value={query}
						maxLength={100}
						onChange={(e) => {
							setQuery(e.target.value);
							setAssignError(null);
						}}
					/>
					{query.trim().length < 2 ? (
						<p>Введите минимум 2 символа</p>
					) : searching ? (
						<p role="status">Ищем учеников…</p>
					) : searchError ? (
						<>
							<Alert>{searchError}</Alert>
							<button
								className="button secondary"
								type="button"
								onClick={() => setAttempt((x) => x + 1)}
							>
								Повторить поиск
							</button>
						</>
					) : !results.length ? (
						<p>Никого не нашли. Попробуйте другое имя или email.</p>
					) : (
						<ul className="management-people">
							{results.map((s) => (
								<li key={s.id}>
									<div>
										<strong>
											{s.firstName} {s.lastName}
										</strong>
										<span>{s.email}</span>
									</div>
									{students.some((x) => x.id === s.id) ? (
										<span>✓ Уже назначен</span>
									) : (
										<button
											className="button primary"
											type="button"
											disabled={!!busy || !rosterReady}
											onClick={() => void assign(s)}
										>
											{busy === s.id ? "Назначаем…" : "Назначить на курс"}
										</button>
									)}
									{assignError?.id === s.id && (
										<Alert>{assignError.message}</Alert>
									)}
								</li>
							))}
						</ul>
					)}
					{notice && (
						<p className="management-success" role="status">
							{notice}
						</p>
					)}
					<small>
						До 20 кандидатов. Только активные пользователи с подтверждённой
						почтой.
					</small>
				</section>
			)}
		</div>
	);
}
