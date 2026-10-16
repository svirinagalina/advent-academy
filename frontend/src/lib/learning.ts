import type { ReactNode } from "react";
import type { Course, DemoState } from "@/lib/demo";
export type Profile = {
	id: string;
	firstName: string;
	lastName: string;
	email: string;
	roles: string[];
};
export type CourseView = {
	id: string;
	title: string;
	description: string;
	status: "new" | "active" | "complete";
	total: number;
	completed: number;
	currentLessonId: string | null;
	modules: {
		id: string;
		title: string;
		lessons: {
			id: string;
			title: string;
			assignment: boolean;
			completed: boolean;
			available: boolean;
		}[];
	}[];
};
export type LessonView = {
	id: string;
	title: string;
	html: string;
	assignment: boolean;
	assets: {
		id: string;
		filename: string;
		contentType: string;
		fileSize: number;
		url: string;
	}[];
};
export type Attempt = {
	id: string;
	userId: string;
	courseId: string;
	lessonId: string;
	attempt: number;
	url: string;
	comment: string;
	status: "review" | "changes" | "accepted";
	feedback: string | null;
	submittedAt: string;
	reviewedAt: string | null;
	version: number;
};
export type AssignmentView = {
	title: string;
	description: string;
	attempts: Attempt[];
};
export type AssignmentSummary = {
	courseId: string;
	courseTitle: string;
	lessonId: string;
	lessonTitle: string;
	title: string;
	status: "draft" | "review" | "changes" | "accepted";
	available: boolean;
	configured: boolean;
	attempts: number;
};
export type ReviewItem = {
	submission: Attempt;
	courseTitle: string;
	lessonTitle: string;
	title: string;
	description: string;
};
export type ReviewPage = {
	items: ReviewItem[];
	total: number;
	page: number;
	size: number;
};
export type LiveData = {
	meetingList: ReactNode;
	nearestMeeting: ReactNode;
	assignmentPanel: ReactNode;
	assignmentList: ReactNode;
	attention: ReactNode;
	reviewPanel: ReactNode;
	canReview: boolean;
	profile: Profile;
	courses: Course[];
	state: DemoState;
	lesson: LessonView | null;
	lessonError: string;
	lessonLoading: boolean;
	complete: (courseId: string, lessonId: string) => Promise<void>;
	logout: () => Promise<void>;
};
export class ClientError extends Error {
	constructor(
		public status: number,
		message: string,
		public fields: Record<string, string> = {},
	) {
		super(message);
	}
}
const messages: Record<string, string> = {
	"Internal Server Error":
		"Сервис временно недоступен. Попробуйте обновить страницу позже.",
	"Meeting version changed; reload it":
		"Занятие уже изменилось. Обновите расписание и повторите действие.",
	"Meeting is not open for joining":
		"Вход ещё не открыт или занятие уже завершено.",
	"Only a planned meeting can be rescheduled":
		"Перенести можно только ещё не начавшееся занятие.",
	"This meeting transition is not available":
		"Действие больше недоступно. Обновите расписание.",
	"Use a valid future meeting window of at most eight hours":
		"Проверьте время: окончание позже начала, длительность до 8 часов.",
	"Select distinct enrolled students other than the meeting host":
		"Выберите учеников, назначенных этому курсу.",
	"Course access cannot be verified right now":
		"Не удалось проверить доступ к курсу. Попробуйте позже.",
	"Creation request was already used with different content":
		"Запрос уже сохранён с другими данными. Обновите расписание.",
	"Assignment instructions have not been added":
		"Условие задания пока не добавлено.",
	"Use an absolute HTTPS link without credentials":
		"Укажите HTTPS-ссылку без логина и пароля.",
	"Wait for review or requested changes before resubmitting":
		"Дождитесь проверки или запроса исправлений. Можно обновить статус задания.",
	"RequestId was already used with different content":
		"Решение уже отправлено с другими данными. Обновите статус задания.",
	"Submission was already reviewed; reload it":
		"Работа уже проверена. Обновите очередь.",
	"Submission version changed; reload it":
		"Работа изменилась. Обновите очередь.",
	"Explain the requested changes": "Опишите, что нужно исправить.",
	"Complete the previous lesson first": "Сначала завершите предыдущий урок.",
	"Lesson is already completed": "Урок уже завершён.",
	"Invalid request": "Проверьте введённые данные.",
	"Access denied": "Недостаточно прав для этого действия.",
};
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
	let response: Response;
	try {
		response = await fetch(path, { ...init, cache: "no-store" });
	} catch {
		throw new ClientError(0, "Проверьте соединение и попробуйте ещё раз.");
	}
	if (!response.ok) {
		const data = await response.json().catch(() => ({}));
		const message = data.message || data.error || "Не удалось выполнить запрос";
		const fields: Record<string, string> = {};
		if (data.fieldErrors && typeof data.fieldErrors === "object") {
			for (const [field, value] of Object.entries(data.fieldErrors)) {
				if (typeof value === "string") fields[field] = value;
			}
		}
		throw new ClientError(
			response.status,
			messages[message] || message,
			fields,
		);
	}
	const text = await response.text();
	return text ? JSON.parse(text) : (undefined as T);
}
export function toDemo(courses: CourseView[]): {
	courses: Course[];
	state: DemoState;
} {
	return {
		courses: courses.map((course) => ({
			...course,
			initialCompleted: 0,
			modules: course.modules.map((module) => ({
				title: module.title,
				lessons: module.lessons,
			})),
		})),
		state: {
			version: 1,
			completed: courses.flatMap((course) =>
				course.modules.flatMap((module) =>
					module.lessons
						.filter((lesson) => lesson.completed)
						.map((lesson) => `${course.id}/${lesson.id}`),
				),
			),
			started: courses
				.filter((course) => course.status !== "new")
				.map((course) => course.id),
			submission: { url: "", comment: "", status: "draft" },
		},
	};
}
