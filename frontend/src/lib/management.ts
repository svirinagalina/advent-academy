import { api, ClientError } from "@/lib/learning";
export type Status = "DRAFT" | "REVIEW" | "PUBLISHED" | "ARCHIVED";
export type ManagedCourse = {
	id: string;
	title: string;
	description: string;
	status: Status;
};
export type LessonNode = {
	id: string;
	title: string;
	position: number;
	contentPresent: boolean;
	contentStatus: Status | null;
};
export type ModuleNode = {
	componentId: string;
	ownerId: string | null;
	title: string;
	status: Status;
	position: number;
	lessons: LessonNode[];
};
export type CourseTree = ManagedCourse & { components: ModuleNode[] };
export type Component = {
	id: string;
	ownerId: string | null;
	title: string;
	status: Status;
	lessons: { id: string; title: string }[];
};
export type Material = {
	id: string;
	lessonId: string;
	html: string;
	markdown: string;
	status: Status;
};
export type Student = {
	id: string;
	firstName: string;
	lastName: string;
	email: string;
};
export const statusLabel: Record<Status, string> = {
	DRAFT: "Черновик",
	REVIEW: "На проверке",
	PUBLISHED: "Опубликован",
	ARCHIVED: "Архив",
};
export function request<T>(path: string, method = "GET", body?: unknown) {
	return api<T>(`/api/backend/${path}`, {
		method,
		...(body === undefined
			? {}
			: {
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify(body),
				}),
	});
}
export function managementError(error: unknown) {
	if (error instanceof ClientError) {
		if (error.message.includes("invalid or foreign assets"))
            return "В тексте есть некорректные ссылки на вложения или вложения другого урока. Проверьте ссылки asset: и /api/assets/.";
        if (error.message.includes("archive must contain a Markdown"))
			return "В архиве не найден Markdown-файл в корне.";
		if (error.message.includes("cannot read zip"))
			return "Не удалось прочитать архив. Проверьте ZIP-файл.";
		if (error.message.includes("UTF-8"))
			return "Сохраните Markdown в кодировке UTF-8.";
		if (error.message.includes("more than one Markdown"))
			return "В корне ZIP должен быть ровно один Markdown-файл.";
		if (error.message.includes("exceeds limit"))
            return "Превышен допустимый размер файла или Markdown. Текст: до 5 MiB, изображения: до 10 MiB.";
        if (error.message.includes("file must not be empty"))
			return "Выберите непустой файл материала.";

		if (error.status === 401) return "Сессия истекла. Войдите в кабинет снова.";
		if (error.status === 403)
			return "Нет доступа к этому действию. Проверьте права и статус курса.";
		if (error.status === 404)
			return "Объект не найден. Обновите программу курса.";
		if (error.status === 422)
			return "Публикация невозможна или выбранный пользователь неактивен. Обновите данные и проверьте условия действия.";
		if (error.status === 409)
			return "Данные уже изменились или действие недоступно в текущем статусе. Обновите курс.";
		if (error.status === 413) return "Файл слишком большой. Максимум 5 MiB.";
		if (error.status === 0 || error.status >= 500)
			return "Не удалось подтвердить результат. Проверьте соединение и обновите данные перед повтором.";
	}
	return error instanceof Error ? error.message : "Не удалось выполнить запрос";
}
