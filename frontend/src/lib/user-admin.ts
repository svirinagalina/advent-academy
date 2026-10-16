export const roleNames: Record<string, string> = {
	ADMIN: "Администратор",
	CONTENT_EDITOR: "Редактор контента",
	INSTRUCTOR: "Преподаватель",
	STUDENT: "Ученик",
};
export const statusNames: Record<string, string> = {
	ACTIVE: "Активен",
	PENDING: "Ожидает активации",
	LOCKED: "Вход временно заблокирован",
	BLOCKED: "Заблокирован администратором",
	SUSPENDED: "Доступ приостановлен",
	DELETED: "Удалён",
};
export type AdminUser = {
	id: string;
	firstName: string;
	lastName: string;
	email: string;
	roles: string[];
	status: string;
	activated: boolean;
	emailVerified: boolean;
	temporarilyLocked: boolean;
	lockedUntil: string | null;
	createdAt: string;
	version: number;
	invitation: {
		status: string;
		expiresAt: string;
		sentAt: string | null;
		canResendAt: string;
	} | null;
	canRemoveAdmin: boolean;
	canBlock: boolean;
	canUnblock: boolean;
	canUnlock: boolean;
};
export type AdminPage<T> = {
	items: T[];
	total: number;
	page: number;
	size: number;
};
export type AdminEvent = {
	id: string;
	userId: string;
	actorName: string;
	action: string;
	beforeValue: string;
	afterValue: string;
	createdAt: string;
};
const messages: Record<string, string> = {
	NETWORK: "Не удалось получить ответ сервера. Проверьте соединение.",
	ADMIN_REQUIRED:
		"Доступ к управлению пользователями недоступен. Войдите с действующей учётной записью администратора.",
	USER_NOT_FOUND: "Пользователь не найден.",
	USER_DISABLED: "Учётная запись удалена. Изменения недоступны.",
	EMAIL_EXISTS: "Пользователь с таким email уже существует",
	REQUEST_REUSED:
		"Запрос уже использован с другими данными. Проверьте пользователя перед повтором.",
	LAST_ADMIN: "В системе должен остаться хотя бы один активный администратор",
	VERSION_CONFLICT:
		"Другой администратор уже изменил данные пользователя. Обновите данные перед повтором.",
	INVALID_ROLES: "Проверьте выбранные роли.",
	INVALID_FILTER: "Проверьте параметры поиска.",
	ALREADY_ACTIVATED:
		"Учётная запись уже активирована. Обновите данные пользователя.",
	NOT_INVITED: "Для этой учётной записи приглашение не создавалось.",
	INVITATION_COOLDOWN:
		"Повторная отправка доступна не чаще одного раза в минуту. Повторите позже.",
	INVALID_TRANSITION: "Состояние пользователя уже изменилось. Обновите данные.",
	INVALID_INVITATION:
		"Приглашение недействительно, уже использовано или срок его действия истёк. Запросите новое приглашение у администратора.",
	PASSWORD_MISMATCH: "Пароли не совпадают.",
	PASSWORD_TOO_LONG:
		"Пароль слишком длинный. Используйте более короткий пароль.",
};
export class AdminError extends Error {
	constructor(
		public status: number,
		public code: string,
		public existingUserId?: string,
	) {
		super(
			messages[code] ||
				(status >= 500
					? "Сервис временно недоступен. Повторите запрос."
					: status === 401
						? "Сессия истекла. Войдите снова."
						: "Не удалось выполнить запрос. Проверьте данные и повторите."),
		);
	}
}
export async function adminRequest<T>(
	path: string,
	method = "GET",
	body?: unknown,
	signal?: AbortSignal,
): Promise<T> {
	let response: Response;
	try {
		response = await fetch(`/api/backend/users/admin${path}`, {
			method,
			cache: "no-store",
			signal,
			...(body === undefined
				? {}
				: {
						headers: { "Content-Type": "application/json" },
						body: JSON.stringify(body),
					}),
		});
	} catch (error) {
		if (error instanceof DOMException && error.name === "AbortError")
			throw error;
		throw new AdminError(0, "NETWORK");
	}
	const data = await response.json().catch(() => {
		if (response.ok) throw new AdminError(0, "NETWORK");
		return {};
	});
	if (!response.ok)
		throw new AdminError(
			response.status,
			data.code || data.message,
			data.existingUserId,
		);
	return data;
}
export function moscowDate(value: string) {
	return new Intl.DateTimeFormat("ru-RU", {
		dateStyle: "short",
		timeStyle: "short",
		timeZone: "Europe/Moscow",
	}).format(new Date(value));
}
export function readableValue(value: string) {
	return (
		value
			.split(",")
			.filter(Boolean)
			.map((item) => roleNames[item] || statusNames[item] || item)
			.join(", ") || "Без ролей"
	);
}
