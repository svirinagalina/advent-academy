"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
	type FormEvent,
	type ReactNode,
	useEffect,
	useRef,
	useState,
} from "react";
import {
	Users,
	BookOpen,
	Check,
	Clock,
	LockKeyhole,
	Ban,
	AlertCircle,
	Plus,
	ArrowLeft,
	ChevronLeft,
	ChevronRight,
	History,
	UserRound,
	LoaderCircle,
	Mail,
} from "lucide-react";
import { ThemeToggle } from "@/components/theme";
import AdminDialog from "@/components/user-admin-dialog";
import {
	AdminError,
	adminRequest,
	roleNames,
	statusNames,
	moscowDate,
	readableValue,
	type AdminUser,
	type AdminPage,
	type AdminEvent,
} from "@/lib/user-admin";
import type { Profile } from "@/lib/learning";

function Feedback({
	children,
	success = false,
}: {
	children: ReactNode;
	success?: boolean;
}) {
	return (
		<div
			className={`feedback ${success ? "success" : "error"}`}
			role={success ? "status" : "alert"}
		>
			<span className="admin-feedback-icon">
				{success ? (
					<Check size={20} aria-hidden="true" />
				) : (
					<AlertCircle size={20} aria-hidden="true" />
				)}
			</span>
			<div>{children}</div>
		</div>
	);
}
function Status({ user }: { user: AdminUser }) {
	const Icon =
		user.status === "ACTIVE"
			? Check
			: user.status === "PENDING"
				? Clock
				: user.status === "LOCKED"
					? LockKeyhole
					: Ban;
	return (
		<span
			className={`badge admin-status ${user.status === "ACTIVE" ? "success" : user.status === "PENDING" ? "warning" : ""}`}
		>
			<Icon size={18} aria-hidden="true" />
			{statusNames[user.status] || "Доступ ограничен"}
		</span>
	);
}
function Roles({ roles }: { roles: string[] }) {
	return (
		<div className="admin-role-tags">
			{roles.length ? (
				roles.map((role) => (
					<span className="admin-role-tag" key={role}>
						{roleNames[role] || role}
					</span>
				))
			) : (
				<span className="muted">Роли не назначены</span>
			)}
		</div>
	);
}
function RoleChoices({
	value,
	setValue,
	disabled,
	lastAdmin = false,
}: {
	value: string[];
	setValue: (roles: string[]) => void;
	disabled: boolean;
	lastAdmin?: boolean;
}) {
	return (
		<fieldset className="admin-roles-fieldset" disabled={disabled}>
			<legend>Роли</legend>
			{Object.entries(roleNames).map(([role, label]) => (
				<label className="admin-checkbox" key={role}>
					<input
						type="checkbox"
						checked={value.includes(role)}
						disabled={disabled || (role === "ADMIN" && lastAdmin)}
						onChange={(e) =>
							setValue(
								e.target.checked
									? [...value, role]
									: value.filter((item) => item !== role),
							)
						}
					/>
					<span>{label}</span>
				</label>
			))}
			{lastAdmin && (
				<p className="muted">
					В системе должен остаться хотя бы один активный администратор
				</p>
			)}
		</fieldset>
	);
}
function Pagination({
	page,
	total,
	size,
	go,
	busy,
}: {
	page: number;
	total: number;
	size: number;
	go: (page: number) => void;
	busy: boolean;
}) {
	return (
		<nav className="admin-pagination" aria-label="Страницы пользователей">
			<button
				className="button secondary"
				type="button"
				disabled={busy || page === 0}
				onClick={() => go(page - 1)}
			>
				<ChevronLeft size={20} aria-hidden="true" />
				Назад
			</button>
			<span>Страница {page + 1}</span>
			<button
				className="button secondary"
				type="button"
				disabled={busy || (page + 1) * size >= total}
				onClick={() => go(page + 1)}
			>
				Вперёд
				<ChevronRight size={20} aria-hidden="true" />
			</button>
		</nav>
	);
}
export default function UserAdmin({
	profile,
	logout,
	profileChanged,
}: {
	profile: Profile;
	logout: () => Promise<void>;
	profileChanged: () => Promise<void>;
}) {
	const router = useRouter(),
		search = useSearchParams();
	const id = search.get("user"),
		query = search.get("query") || "",
		role = search.get("role") || "",
		status = search.get("status") || "";
	const page = Math.max(0, Number.parseInt(search.get("page") || "0", 10) || 0);
	const allowed =
		profile.roles.includes("ADMIN") &&
		!["BLOCKED", "DELETED", "SUSPENDED", "PENDING"].includes(
			profile.status || "ACTIVE",
		);
	const [list, setList] = useState<AdminPage<AdminUser> | null>(null),
		[loading, setLoading] = useState(true),
		[error, setError] = useState("");
	const [user, setUser] = useState<AdminUser | null>(null),
		[cardLoading, setCardLoading] = useState(false),
		[cardError, setCardError] = useState("");
	const [creating, setCreating] = useState(false),
		[editing, setEditing] = useState(false),
		[action, setAction] = useState<string | null>(null);
	const [revision, setRevision] = useState(0),
		[notice, setNotice] = useState("");
	const [profileOpen, setProfileOpen] = useState(false),
		[leaving, setLeaving] = useState(false),
		[logoutError, setLogoutError] = useState("");
	const profileButton = useRef<HTMLButtonElement>(null);
	const cardGeneration = useRef(0);
	const searchInput = useRef<HTMLInputElement>(null);
	useEffect(() => {
		// Keep native typing immediate while URL navigation commits asynchronously.
		if (searchInput.current && document.activeElement !== searchInput.current)
			searchInput.current.value = query;
	}, [query]);
	useEffect(() => {
		const restoreSearch = () => {
			if (searchInput.current)
				searchInput.current.value =
					new URLSearchParams(window.location.search).get("query") || "";
		};
		window.addEventListener("popstate", restoreSearch);
		return () => window.removeEventListener("popstate", restoreSearch);
	}, []);
	useEffect(() => {
		if (profileOpen)
			document
				.querySelector<HTMLElement>('#admin-profile-menu [role="menuitem"]')
				?.focus();
	}, [profileOpen]);
	const tab = search.get("userTab") === "history" ? "history" : "access";
	const navigate = (
		changes: Record<string, string | null>,
		replace = false,
	) => {
		const params = new URLSearchParams(search.toString());
		params.set("view", "users");
		for (const [key, value] of Object.entries(changes)) {
			if (value === null || value === "") params.delete(key);
			else params.set(key, value);
		}
		const path = `/?${params}`;
		if (replace) router.replace(path, { scroll: false });
		else router.push(path, { scroll: true });
	};
	// biome-ignore lint/correctness/useExhaustiveDependencies: revision invalidates the list after mutations and manual retries.
	useEffect(() => {
		if (!allowed) return;
		const controller = new AbortController();
		setLoading(true);
		setError("");
		const timer = setTimeout(() => {
			const params = new URLSearchParams({
				query,
				role,
				status,
				page: String(page),
				size: "20",
			});
			adminRequest<AdminPage<AdminUser>>(
				`?${params}`,
				"GET",
				undefined,
				controller.signal,
			)
				.then((result) => {
					if (!controller.signal.aborted) setList(result);
				})
				.catch((e) => {
					if (!controller.signal.aborted) setError(e.message);
				})
				.finally(() => {
					if (!controller.signal.aborted) setLoading(false);
				});
		}, 250);
		return () => {
			clearTimeout(timer);
			controller.abort();
		};
	}, [allowed, query, role, status, page, revision]);
	useEffect(() => {
		const generation = ++cardGeneration.current;
		setEditing(false);
		setAction(null);
		setNotice("");
		if (!id || !allowed) {
			setUser(null);
			return;
		}
		const controller = new AbortController();
		setCardLoading(true);
		setCardError("");
		setUser(null);
		adminRequest<AdminUser>(`/${id}`, "GET", undefined, controller.signal)
			.then((value) => {
				if (!controller.signal.aborted && generation === cardGeneration.current)
					setUser(value);
			})
			.catch((e) => {
				if (!controller.signal.aborted && generation === cardGeneration.current)
					setCardError(e.message);
			})
			.finally(() => {
				if (!controller.signal.aborted && generation === cardGeneration.current)
					setCardLoading(false);
			});
		return () => controller.abort();
	}, [id, allowed]);
	const reloadCard = async () => {
		if (!id) return;
		const generation = ++cardGeneration.current;
		setCardError("");
		setCardLoading(true);
		try {
			const value = await adminRequest<AdminUser>(`/${id}`);
			if (generation === cardGeneration.current) setUser(value);
		} catch (e) {
			if (generation === cardGeneration.current)
				setCardError(
					e instanceof Error ? e.message : "Не удалось загрузить пользователя",
				);
		} finally {
			if (generation === cardGeneration.current) setCardLoading(false);
		}
	};
	async function changed(value: AdminUser, message: string) {
		setUser(value);
		setRevision((previous) => previous + 1);
		setNotice(message);
		if (value.id === profile.id) await profileChanged();
	}
	const closeProfile = () => {
		setProfileOpen(false);
		profileButton.current?.focus();
	};
	async function leave() {
		if (leaving) return;
		setLeaving(true);
		setLogoutError("");
		try {
			await logout();
		} catch (e) {
			setLogoutError(e instanceof Error ? e.message : "Не удалось выйти");
		} finally {
			setLeaving(false);
		}
	}
	const cardUser = user;
	return (
		<div className="management-shell user-admin-shell">
			<aside className="sidebar">
				<Link className="brand" href="/?view=manage">
					Advent
					<br />
					Academy
				</Link>
				<nav>
					<Link className="nav-link" href="/?view=manage">
						<BookOpen size={24} aria-hidden="true" />
						Курсы
					</Link>
					{allowed && (
						<Link
							className="nav-link active"
							aria-current="page"
							href="/?view=users"
						>
							<Users size={24} aria-hidden="true" />
							Пользователи
						</Link>
					)}
				</nav>
			</aside>
			<div className="workspace">
				<header className="topbar">
					<Link href="/?view=users" className="management-brand">
						Advent Academy
					</Link>
					<div className="header-actions">
						<ThemeToggle />
						<div className="profile">
							<button
								ref={profileButton}
								type="button"
								className="profile-button"
								aria-label={`Меню пользователя ${profile.firstName}`}
								aria-expanded={profileOpen}
								aria-haspopup="menu"
								aria-controls="admin-profile-menu"
								onClick={() => setProfileOpen((value) => !value)}
								onKeyDown={(e) => {
									if (e.key === "Escape") closeProfile();
								}}
							>
								<span className="avatar">{profile.firstName.slice(0, 1)}</span>
								<span className="management-profile">
									<strong>
										{profile.firstName} {profile.lastName}
									</strong>
									<small>{allowed ? "Администратор" : "Пользователь"}</small>
								</span>
							</button>
							{profileOpen && (
								<>
									<button
										className="menu-backdrop"
										type="button"
										aria-label="Закрыть меню профиля"
										onClick={closeProfile}
									/>
									<div
										className="profile-menu"
										role="menu"
										aria-label="Меню пользователя"
										id="admin-profile-menu"
										onKeyDown={(e) => {
											if (e.key === "Escape") closeProfile();
										}}
									>
										<strong>
											{profile.firstName} {profile.lastName}
										</strong>
										<button
											className="text-link"
											role="menuitem"
											type="button"
											disabled={leaving}
											onClick={() => void leave()}
										>
											{leaving ? "Выходим…" : "Выйти"}
										</button>
										{logoutError && <Feedback>{logoutError}</Feedback>}
									</div>
								</>
							)}
						</div>
					</div>
				</header>
				<main className="main-content admin-main">
					{!allowed ? (
						<section className="card empty-state">
							<h1>Нет доступа к управлению пользователями</h1>
							<p>Этот раздел доступен только администратору.</p>
							<Link className="button secondary" href="/">
								К кабинету
							</Link>
						</section>
					) : (
						<>
							<nav
								className="admin-mobile-nav"
								aria-label="Разделы администратора"
							>
								<Link href="/?view=manage">Курсы</Link>
								<Link href="/?view=users" aria-current="page">
									Пользователи
								</Link>
							</nav>
							{id ? (
								<>
									<div className="admin-card-toolbar">
										<button
											type="button"
											className="text-link admin-back"
											onClick={() => navigate({ user: null, userTab: null })}
										>
											<ArrowLeft size={20} aria-hidden="true" />К пользователям
										</button>
										<button
											type="button"
											className="text-link"
											onClick={() => void reloadCard()}
										>
											Обновить данные
										</button>
									</div>
									{cardLoading ? (
										<p role="status">Загружаем пользователя…</p>
									) : cardError ? (
										<>
											<Feedback>
												<strong>Не удалось загрузить пользователя</strong>
												<p>{cardError}</p>
											</Feedback>
											<button
												className="button secondary"
												type="button"
												onClick={() => void reloadCard()}
											>
												Повторить
											</button>
										</>
									) : (
										cardUser && (
											<>
												<div className="admin-user-heading">
													<h1>
														{cardUser.firstName} {cardUser.lastName}
													</h1>
													<Status user={cardUser} />
												</div>
												<p className="admin-user-email">{cardUser.email}</p>
												<p className="muted">
													Дата регистрации: {moscowDate(cardUser.createdAt)} МСК
												</p>
												<div
													className="admin-tabs"
													role="tablist"
													aria-label="Карточка пользователя"
												>
													{[
														{
															id: "access",
															label: "Доступ и роли",
															icon: UserRound,
														},
														{
															id: "history",
															label: "История действий",
															icon: History,
														},
													].map((item, index) => (
														<button
															key={item.id}
															id={`admin-tab-${item.id}`}
															type="button"
															role="tab"
															aria-selected={tab === item.id}
															aria-controls="admin-user-panel"
															tabIndex={tab === item.id ? 0 : -1}
															className={tab === item.id ? "selected" : ""}
															onClick={() =>
																navigate(
																	{
																		userTab:
																			item.id === "history" ? "history" : null,
																	},
																	true,
																)
															}
															onKeyDown={(e) => {
																if (
																	[
																		"ArrowLeft",
																		"ArrowRight",
																		"Home",
																		"End",
																	].includes(e.key)
																) {
																	e.preventDefault();
																	const next =
																		e.key === "Home"
																			? "access"
																			: e.key === "End"
																				? "history"
																				: index === 0
																					? "history"
																					: "access";
																	navigate(
																		{
																			userTab:
																				next === "history" ? "history" : null,
																		},
																		true,
																	);
																	document
																		.getElementById(`admin-tab-${next}`)
																		?.focus();
																}
															}}
														>
															<item.icon size={20} aria-hidden="true" />
															{item.label}
														</button>
													))}
												</div>
												{notice && <Feedback success>{notice}</Feedback>}
												<section
													role="tabpanel"
													id="admin-user-panel"
													aria-labelledby={`admin-tab-${tab}`}
												>
													{tab === "history" ? (
														<UserHistory id={cardUser.id} revision={revision} />
													) : (
														<div className="admin-card-grid">
															<section className="card admin-detail-card">
																<h2>Роли</h2>
																<p className="muted">
																	Роли пользователя в системе
																</p>
																<Roles roles={cardUser.roles} />
																{cardUser.status !== "DELETED" && (
																	<button
																		className="button secondary"
																		type="button"
																		onClick={() => setEditing(true)}
																	>
																		Изменить роли
																	</button>
																)}
															</section>
															<section className="card admin-detail-card">
																<h2>Активация и email</h2>
																<p>
																	{cardUser.activated ? (
																		<Check size={18} aria-hidden="true" />
																	) : (
																		<Clock size={18} aria-hidden="true" />
																	)}
																	{cardUser.activated
																		? "Учётная запись активирована"
																		: "Учётная запись не активирована"}
																</p>
																<p>
																	{cardUser.emailVerified
																		? "Email подтверждён"
																		: "Email не подтверждён"}
																</p>
															</section>
															<InvitationCard
																user={cardUser}
																changed={changed}
															/>
															<section className="card admin-detail-card">
																<h2>Блокировка</h2>
																<p>
																	{cardUser.status === "BLOCKED"
																		? "Заблокирован администратором"
																		: cardUser.temporarilyLocked
																			? "После неудачных попыток входа"
																			: "Доступ не заблокирован"}
																</p>
																{cardUser.temporarilyLocked && (
																	<p>
																		Вход временно заблокирован
																		{cardUser.lockedUntil
																			? ` до ${moscowDate(cardUser.lockedUntil)} МСК`
																			: ""}
																		.
																	</p>
																)}
																{!cardUser.canRemoveAdmin && (
																	<p className="muted">
																		В системе должен остаться хотя бы один
																		активный администратор
																	</p>
																)}
																{(
																	[
																		[
																			"block",
																			"Заблокировать пользователя",
																			cardUser.canBlock ||
																				!cardUser.canRemoveAdmin,
																		],
																		[
																			"unblock",
																			"Разблокировать пользователя",
																			cardUser.canUnblock,
																		],
																		[
																			"unlock",
																			"Разблокировать вход",
																			cardUser.canUnlock,
																		],
																	] as const
																)
																	.filter(([, , show]) => show)
																	.map(([command, label]) => (
																		<button
																			key={command}
																			type="button"
																			className={`button secondary ${command === "block" ? "admin-danger" : ""}`}
																			disabled={
																				command === "block" &&
																				!cardUser.canBlock
																			}
																			onClick={() => setAction(command)}
																		>
																			{label}
																		</button>
																	))}
															</section>
														</div>
													)}
												</section>
											</>
										)
									)}
								</>
							) : (
								<>
									<div className="section-heading">
										<h1>Пользователи</h1>
										<button
											className="button primary"
											type="button"
											onClick={() => setCreating(true)}
										>
											<Plus size={20} aria-hidden="true" />
											Создать пользователя
										</button>
									</div>
									<section
										className="card admin-filters"
										aria-label="Поиск пользователей"
									>
										<div>
											<label htmlFor="admin-search">Имя или email</label>
											<input
												id="admin-search"
												ref={searchInput}
												defaultValue={query}
												maxLength={255}
												onChange={(e) =>
													navigate({ query: e.target.value, page: null }, true)
												}
												placeholder="Начните вводить имя или email"
											/>
										</div>
										<div>
											<label htmlFor="admin-role-filter">Роль</label>
											<select
												id="admin-role-filter"
												value={role}
												onChange={(e) =>
													navigate({ role: e.target.value, page: null }, true)
												}
											>
												<option value="">Все роли</option>
												{Object.entries(roleNames).map(([key, label]) => (
													<option value={key} key={key}>
														{label}
													</option>
												))}
											</select>
										</div>
										<div>
											<label htmlFor="admin-status-filter">Статус</label>
											<select
												id="admin-status-filter"
												value={status}
												onChange={(e) =>
													navigate({ status: e.target.value, page: null }, true)
												}
											>
												<option value="">Все статусы</option>
												{["ACTIVE", "PENDING", "LOCKED", "BLOCKED"].map(
													(key) => (
														<option value={key} key={key}>
															{statusNames[key]}
														</option>
													),
												)}
											</select>
										</div>
										<button
											className="text-link"
											type="button"
											onClick={() =>
												navigate(
													{ query: null, role: null, status: null, page: null },
													true,
												)
											}
										>
											Сбросить фильтры
										</button>
									</section>
									<section aria-busy={loading} className="admin-list-section">
										{loading ? (
											<>
												<p role="status">Загружаем пользователей…</p>
												<div className="card admin-skeleton" aria-hidden="true">
													{[1, 2, 3, 4, 5].map((n) => (
														<div key={n} />
													))}
												</div>
											</>
										) : error ? (
											<>
												<Feedback>
													<strong>Не удалось загрузить пользователей</strong>
													<p>{error}</p>
												</Feedback>
												<button
													className="button secondary"
													type="button"
													onClick={() => setRevision((n) => n + 1)}
												>
													Повторить
												</button>
											</>
										) : !list?.items.length ? (
											<section className="card empty-state">
												<h2>
													{query || role || status || page
														? "По вашему запросу ничего не найдено"
														: "Пользователей пока нет"}
												</h2>
												<button
													className="button secondary"
													type="button"
													onClick={() =>
														query || role || status || page
															? navigate(
																	{
																		query: null,
																		role: null,
																		status: null,
																		page: null,
																	},
																	true,
																)
															: setCreating(true)
													}
												>
													{query || role || status || page
														? "Сбросить фильтры"
														: "Создать пользователя"}
												</button>
											</section>
										) : (
											<div className="card admin-users">
												<table>
													<thead>
														<tr>
															<th>Пользователь</th>
															<th>Роли</th>
															<th>Статус</th>
															<th>
																Дата регистрации
																<br />
																<small>Время МСК</small>
															</th>
															<th>Действие</th>
														</tr>
													</thead>
													<tbody>
														{list.items.map((person) => (
															<tr key={person.id}>
																<td className="admin-person">
																	<strong>
																		{person.firstName} {person.lastName}
																	</strong>
																	<span>{person.email}</span>
																</td>
																<td>
																	<Roles roles={person.roles} />
																</td>
																<td>
																	<Status user={person} />
																</td>
																<td>
																	<span className="admin-mobile-label">
																		Дата регистрации:{" "}
																	</span>
																	{moscowDate(person.createdAt)}
																</td>
																<td>
																	<Link
																		className="button secondary"
																		href={`/?${new URLSearchParams({ ...Object.fromEntries(search), view: "users", user: person.id })}`}
																	>
																		Открыть
																		<span className="sr-only">
																			{" "}
																			пользователя {person.firstName}{" "}
																			{person.lastName}
																		</span>
																	</Link>
																</td>
															</tr>
														))}
													</tbody>
												</table>
											</div>
										)}
									</section>
									<Pagination
										page={page}
										total={list?.total || 0}
										size={20}
										go={(next) => navigate({ page: String(next) })}
										busy={loading}
									/>
								</>
							)}
						</>
					)}
				</main>
			</div>
			{allowed && creating && (
				<CreateUser
					close={() => setCreating(false)}
					created={(value) => {
						setCreating(false);
						setRevision((n) => n + 1);
						navigate({ user: value.id });
					}}
					openExisting={(existing) => {
						setCreating(false);
						navigate({ user: existing });
					}}
				/>
			)}
			{allowed && editing && cardUser && (
				<EditRoles
					user={cardUser}
					close={() => setEditing(false)}
					changed={async (value) => {
						setEditing(false);
						await changed(value, "Роли обновлены");
					}}
				/>
			)}
			{allowed && action && cardUser && (
				<AccessAction
					user={cardUser}
					action={action}
					close={() => setAction(null)}
					changed={async (value) => {
						setAction(null);
						await changed(value, "Доступ обновлён");
					}}
				/>
			)}
		</div>
	);
}

function CreateUser({
	close,
	created,
	openExisting,
}: {
	close: () => void;
	created: (user: AdminUser) => void;
	openExisting: (id: string) => void;
}) {
	const [firstName, setFirst] = useState(""),
		[lastName, setLast] = useState(""),
		[email, setEmail] = useState(""),
		[roles, setRoles] = useState(["STUDENT"]);
	const [busy, setBusy] = useState(false),
		[error, setError] = useState(""),
		[fields, setFields] = useState<Record<string, string>>({}),
		[existing, setExisting] = useState<string | null>(null),
		[result, setResult] = useState<AdminUser | null>(null),
		[unknown, setUnknown] = useState(false);
	const request = useRef(crypto.randomUUID());
	async function submit(event: FormEvent) {
		event.preventDefault();
		if (busy) return;
		const invalid: Record<string, string> = {};
		if (!firstName.trim()) invalid.firstName = "Введите имя";
		else if (firstName.length > 50)
			invalid.firstName = "Имя должно быть не длиннее 50 символов";
		if (!lastName.trim()) invalid.lastName = "Введите фамилию";
		else if (lastName.length > 50)
			invalid.lastName = "Фамилия должна быть не длиннее 50 символов";
		if (
			!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) ||
			email.trim().length > 255
		)
			invalid.email = "Введите корректный email";
		setFields(invalid);
		setError("");
		setExisting(null);
		if (Object.keys(invalid).length) {
			document
				.getElementById(`admin-create-${Object.keys(invalid)[0]}`)
				?.focus();
			return;
		}
		setBusy(true);
		try {
			setResult(
				await adminRequest<AdminUser>("", "POST", {
					requestId: request.current,
					firstName: firstName.trim(),
					lastName: lastName.trim(),
					email: email.trim(),
					roles,
				}),
			);
			setUnknown(false);
		} catch (e) {
			if (e instanceof AdminError) {
				setError(e.message);
				setExisting(e.existingUserId || null);
				setUnknown(e.status === 0 || e.status >= 500);
			} else setError("Не удалось создать пользователя");
		} finally {
			setBusy(false);
		}
	}
	return (
		<AdminDialog title="Создать пользователя" busy={busy} close={close}>
			{result ? (
				<>
					<Feedback success>Пользователь создан</Feedback>
					<p>
						<strong>
							{result.firstName} {result.lastName}
						</strong>
						<br />
						{result.email}
					</p>
					<InvitationCard
						user={result}
						changed={async (value) => setResult(value)}
					/>
					<button
						className="button primary"
						type="button"
						onClick={() => created(result)}
					>
						Открыть пользователя
					</button>
				</>
			) : (
				<form
					className="management-form admin-create-form"
					onSubmit={submit}
					noValidate
					aria-busy={busy}
				>
					{(
						[
							{
								id: "firstName",
								label: "Имя",
								value: firstName,
								set: setFirst,
								max: 50,
							},
							{
								id: "lastName",
								label: "Фамилия",
								value: lastName,
								set: setLast,
								max: 50,
							},
							{
								id: "email",
								label: "Email",
								value: email,
								set: setEmail,
								max: 255,
							},
						] as const
					).map((field) => (
						<div key={field.id}>
							<label htmlFor={`admin-create-${field.id}`}>{field.label}</label>
							<input
								id={`admin-create-${field.id}`}
								type={field.id === "email" ? "email" : "text"}
								required
								maxLength={field.max}
								value={field.value}
								disabled={busy || unknown}
								autoComplete="off"
								aria-invalid={!!fields[field.id]}
								aria-describedby={
									fields[field.id]
										? `admin-create-${field.id}-error`
										: undefined
								}
								onChange={(e) => {
									field.set(e.target.value);
									setFields((previous) => ({ ...previous, [field.id]: "" }));
								}}
							/>
							{fields[field.id] && (
								<p
									className="field-error"
									id={`admin-create-${field.id}-error`}
									role="alert"
								>
									{fields[field.id]}
								</p>
							)}
						</div>
					))}
					<RoleChoices
						value={roles}
						setValue={setRoles}
						disabled={busy || unknown}
					/>
					<p className="muted">
						Пользователь получит приглашение и самостоятельно установит пароль
					</p>
					{error && (
						<Feedback>
							{error}
							{existing && (
								<button
									className="text-link"
									type="button"
									onClick={() => openExisting(existing)}
								>
									Открыть пользователя
								</button>
							)}
						</Feedback>
					)}
					{unknown && (
						<p role="status">
							Результат создания пока не подтверждён. Повтор проверит тот же
							запрос и не создаст вторую учётную запись.
						</p>
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
							{busy ? (
								<>
									<LoaderCircle
										size={18}
										className="admin-spinner"
										aria-hidden="true"
									/>
									Создаём…
								</>
							) : unknown ? (
								"Проверить результат и повторить"
							) : (
								"Создать и пригласить"
							)}
						</button>
					</div>
				</form>
			)}
		</AdminDialog>
	);
}
function InvitationCard({
	user,
	changed,
}: {
	user: AdminUser;
	changed: (user: AdminUser, message: string) => Promise<void>;
}) {
	const [busy, setBusy] = useState(false),
		[error, setError] = useState("");
	const request = useRef<string | null>(null);
	const invitation = user.invitation;
	const labels: Record<string, string> = {
		SENT: "Приглашение отправлено",
		FAILED: "Не удалось отправить приглашение",
		ACCEPTED: "Приглашение принято",
		EXPIRED: "Срок действия приглашения истёк",
		SENDING: "Приглашение отправляется",
		UNKNOWN: "Отправка приглашения не подтверждена",
	};
	async function resend() {
		if (busy) return;
		setBusy(true);
		setError("");
		request.current ||= crypto.randomUUID();
		try {
			const result = await adminRequest<AdminUser>(
				`/${user.id}/invitation`,
				"POST",
				{ requestId: request.current },
			);
			request.current = null;
			await changed(result, "Состояние приглашения обновлено");
		} catch (e) {
			setError(
				e instanceof Error ? e.message : "Не удалось отправить приглашение",
			);
		} finally {
			setBusy(false);
		}
	}
	return (
		<section
			className="card admin-detail-card admin-invitation"
			aria-busy={busy}
		>
			<h2>Приглашение</h2>
			<p>
				<Mail size={18} aria-hidden="true" />
				{invitation
					? labels[invitation.status] || "Отправка не подтверждена"
					: "Приглашение не создавалось"}
			</p>
			{invitation?.status === "FAILED" && (
				<Feedback>
					Учётная запись создана, но письмо не отправлено. Можно повторить
					отправку приглашения.
				</Feedback>
			)}
			{invitation?.sentAt && (
				<p className="muted">
					Отправлено: {moscowDate(invitation.sentAt)} МСК. Отправка не
					подтверждает доставку.
				</p>
			)}
			{invitation && !user.activated && (
				<p className="muted">
					Срок действия: до {moscowDate(invitation.expiresAt)} МСК
				</p>
			)}
			{invitation && user.status === "PENDING" && !user.emailVerified && (
				<button
					className="button secondary"
					type="button"
					disabled={busy || invitation.status === "SENDING"}
					onClick={() => void resend()}
				>
					{busy
						? "Отправляем…"
						: invitation.status === "FAILED"
							? "Повторить отправку"
							: "Отправить приглашение повторно"}
				</button>
			)}
			{error && <Feedback>{error}</Feedback>}
		</section>
	);
}
function EditRoles({
	user,
	close,
	changed,
}: {
	user: AdminUser;
	close: () => void;
	changed: (user: AdminUser) => Promise<void>;
}) {
	const [current, setCurrent] = useState(user),
		[roles, setRoles] = useState(
			user.roles.filter((role) => role in roleNames),
		);
	const [busy, setBusy] = useState(false),
		[error, setError] = useState(""),
		[conflict, setConflict] = useState(false),
		[confirm, setConfirm] = useState(false),
		[notice, setNotice] = useState("");
	async function save() {
		if (busy || conflict) return;
		if (
			current.roles.includes("ADMIN") &&
			!roles.includes("ADMIN") &&
			!confirm
		) {
			setConfirm(true);
			return;
		}
		setBusy(true);
		setError("");
		try {
			await changed(
				await adminRequest<AdminUser>(`/${user.id}/roles`, "PUT", {
					version: current.version,
					roles,
				}),
			);
		} catch (e) {
			setError(e instanceof Error ? e.message : "Не удалось сохранить роли");
			if (
				e instanceof AdminError &&
				(e.code === "VERSION_CONFLICT" || e.status === 0 || e.status >= 500)
			)
				setConflict(true);
		} finally {
			setBusy(false);
		}
	}
	async function refresh() {
		setBusy(true);
		setError("");
		try {
			const fresh = await adminRequest<AdminUser>(`/${user.id}`);
			setCurrent(fresh);
			setRoles(fresh.roles.filter((role) => role in roleNames));
			setConflict(false);
			setConfirm(false);
			setNotice("Данные обновлены. Проверьте выбранные роли.");
		} catch (e) {
			setError(e instanceof Error ? e.message : "Не удалось обновить данные");
		} finally {
			setBusy(false);
		}
	}
	return (
		<AdminDialog
			title={confirm ? "Снять роль Администратор?" : "Изменить роли"}
			busy={busy}
			close={close}
		>
			<p>
				<strong>
					{user.firstName} {user.lastName}
				</strong>
				<br />
				{user.email}
			</p>
			{confirm ? (
				<p>
					Будет снята роль «Администратор». Изменения вступят в силу после
					сохранения.
				</p>
			) : (
				<>
					<p className="muted">
						Текущие роли: {readableValue(current.roles.join(","))}
					</p>
					<RoleChoices
						value={roles}
						setValue={setRoles}
						disabled={busy}
						lastAdmin={!current.canRemoveAdmin}
					/>
				</>
			)}
			{notice && <p role="status">{notice}</p>}
			{error && (
				<Feedback>
					{error}
					<p>
						{conflict
							? "Ваш выбор оставлен в форме. Перед повторным изменением обновите данные и проверьте результат предыдущего запроса."
							: "Ваш выбор оставлен в форме. Изменения не сохранены."}
					</p>
				</Feedback>
			)}
			<div className="management-actions">
				<button
					className="button secondary"
					type="button"
					disabled={busy}
					onClick={() => (confirm && !conflict ? setConfirm(false) : close())}
				>
					Отмена
				</button>
				{conflict ? (
					<button
						className="button primary"
						type="button"
						disabled={busy}
						onClick={() => void refresh()}
					>
						Обновить данные
					</button>
				) : (
					<button
						className="button primary"
						type="button"
						disabled={busy}
						onClick={() => void save()}
					>
						{busy
							? "Сохраняем…"
							: confirm
								? "Снять роль и сохранить"
								: "Сохранить изменения"}
					</button>
				)}
			</div>
		</AdminDialog>
	);
}
function AccessAction({
	user,
	action,
	close,
	changed,
}: {
	user: AdminUser;
	action: string;
	close: () => void;
	changed: (user: AdminUser) => Promise<void>;
}) {
	const [busy, setBusy] = useState(false),
		[error, setError] = useState(""),
		[conflict, setConflict] = useState(false);
	const labels: Record<string, string> = {
		block: "Заблокировать пользователя",
		unblock: "Разблокировать пользователя",
		unlock: "Разблокировать вход",
	};
	async function save() {
		if (busy) return;
		setBusy(true);
		setError("");
		try {
			await changed(
				await adminRequest<AdminUser>(`/${user.id}/${action}`, "POST", {
					version: user.version,
				}),
			);
		} catch (e) {
			setError(e instanceof Error ? e.message : "Не удалось изменить доступ");
			if (e instanceof AdminError && e.status === 409) setConflict(true);
		} finally {
			setBusy(false);
		}
	}
	return (
		<AdminDialog title={labels[action]} busy={busy} close={close}>
			<p>
				<strong>
					{user.firstName} {user.lastName}
				</strong>
				<br />
				{user.email}
			</p>
			<p>
				{action === "unlock"
					? "Снять временную блокировку после неудачных попыток входа? Административная блокировка сохранится, если она установлена."
					: action === "unblock"
						? "Снять административную блокировку пользователя? Временная блокировка входа снимается отдельно."
						: "Подтвердите административную блокировку пользователя."}
			</p>
			{error && <Feedback>Не удалось изменить доступ. {error}</Feedback>}
			<div className="management-actions">
				<button
					className="button secondary"
					type="button"
					disabled={busy}
					onClick={close}
				>
					Отмена
				</button>
				{!conflict && (
					<button
						className="button primary"
						type="button"
						disabled={busy}
						onClick={() => void save()}
					>
						{busy ? "Сохраняем…" : labels[action]}
					</button>
				)}
				{conflict && <p>Закройте окно и обновите карточку перед повтором.</p>}
			</div>
		</AdminDialog>
	);
}
function UserHistory({ id, revision }: { id: string; revision: number }) {
	const [data, setData] = useState<AdminPage<AdminEvent> | null>(null),
		[page, setPage] = useState(0),
		[loading, setLoading] = useState(true),
		[error, setError] = useState(""),
		[retry, setRetry] = useState(0);
	// biome-ignore lint/correctness/useExhaustiveDependencies: revision and retry invalidate history after mutations and failed requests.
	useEffect(() => {
		const controller = new AbortController();
		setLoading(true);
		setError("");
		adminRequest<AdminPage<AdminEvent>>(
			`/${id}/history?page=${page}&size=20`,
			"GET",
			undefined,
			controller.signal,
		)
			.then((value) => {
				if (!controller.signal.aborted) setData(value);
			})
			.catch((e) => {
				if (!controller.signal.aborted) setError(e.message);
			})
			.finally(() => {
				if (!controller.signal.aborted) setLoading(false);
			});
		return () => controller.abort();
	}, [id, page, revision, retry]);
	const labels: Record<string, string> = {
		USER_CREATED: "Создан пользователь",
		ROLES_CHANGED: "Изменены роли",
		BLOCK: "Пользователь заблокирован",
		UNBLOCK: "Пользователь разблокирован",
		UNLOCK: "Снята временная блокировка входа",
		INVITATION_SENT: "Приглашение отправлено",
		INVITATION_FAILED: "Ошибка отправки приглашения",
		INVITATION_ACCEPTED: "Приглашение принято",
	};
	return (
		<section aria-busy={loading}>
			{loading ? (
				<p role="status">Загружаем историю…</p>
			) : error ? (
				<>
					<Feedback>{error}</Feedback>
					<button
						className="button secondary"
						type="button"
						onClick={() => setRetry((n) => n + 1)}
					>
						Повторить
					</button>
				</>
			) : !data?.items.length ? (
				<section className="card empty-state">Действий пока нет</section>
			) : (
				<ol className="admin-history">
					{data.items.map((event) => (
						<li className="card" key={event.id}>
							<p className="muted">{moscowDate(event.createdAt)} МСК</p>
							<strong>{event.actorName}</strong>
							<p>{labels[event.action] || "Данные пользователя изменены"}</p>
							{(event.beforeValue || event.afterValue) &&
								event.beforeValue !== event.afterValue && (
									<p className="muted">
										{event.beforeValue
											? `${readableValue(event.beforeValue)} → `
											: "Назначены роли: "}
										{readableValue(event.afterValue)}
									</p>
								)}
						</li>
					))}
				</ol>
			)}
			<Pagination
				page={page}
				total={data?.total || 0}
				size={20}
				go={setPage}
				busy={loading}
			/>
		</section>
	);
}
