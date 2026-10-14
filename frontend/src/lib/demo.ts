export type SubmissionStatus = "draft" | "review" | "changes" | "accepted";
export type Submission = {
	url: string;
	comment: string;
	status: SubmissionStatus;
};
export type DemoState = {
	version: 1;
	completed: string[];
	submission: Submission;
	started: string[];
};
export type Lesson = { id: string; title: string; assignment?: boolean };
export type Course = {
	id: string;
	title: string;
	description: string;
	total: number;
	initialCompleted: number;
	modules: { title: string; lessons: Lesson[] }[];
};

const javaLessons: Lesson[] = [
	{ id: "variables", title: "Переменные и типы данных" },
	{ id: "arithmetic", title: "Арифметические операторы" },
	{ id: "comparison", title: "Сравнение значений" },
	{ id: "logic", title: "Логические операторы" },
	{ id: "conditions", title: "Условные операторы", assignment: true },
	{ id: "loops", title: "Циклы" },
	{ id: "arrays", title: "Массивы" },
	{ id: "methods", title: "Методы" },
];

export const courses: Course[] = [
	{
		id: "java",
		title: "Java: основы программирования",
		description: "Базовый синтаксис, условия, циклы и первые программы.",
		total: 24,
		initialCompleted: 8,
		modules: [
			{
				title: "Первые шаги",
				lessons: [
					"Знакомство с Java",
					"Установка JDK",
					"Первая программа",
					"Как устроен код",
				].map((title, i) => ({ id: `intro-${i}`, title })),
			},
			{ title: "Основы Java", lessons: javaLessons },
			{
				title: "Практика программирования",
				lessons: [
					"Строки",
					"Работа с вводом",
					"Числа и вычисления",
					"Обработка ошибок",
					"Коллекции",
					"Списки",
					"Множества",
					"Словари",
					"Работа с файлами",
					"Отладка",
					"Тестирование",
					"Итоговая практика",
				].map((title, i) => ({ id: `practice-${i}`, title })),
			},
		],
	},
	{
		id: "git",
		title: "Git и командная работа",
		description: "Ветки, коммиты и совместная работа.",
		total: 12,
		initialCompleted: 3,
		modules: [
			{
				title: "Работа с Git",
				lessons: [
					"Знакомство с Git",
					"Первый репозиторий",
					"Коммиты",
					"Ветки",
					"Слияние веток",
					"Конфликты",
					"Удалённый репозиторий",
					"Pull request",
					"История изменений",
					"Отмена изменений",
					"Работа в команде",
					"Практика",
				].map((title, i) => ({ id: `git-${i}`, title })),
			},
		],
	},
	{
		id: "sql",
		title: "SQL: основы работы с данными",
		description: "Запросы и работа с таблицами.",
		total: 10,
		initialCompleted: 0,
		modules: [
			{
				title: "Основы SQL",
				lessons: [
					"Знакомство с базами данных",
					"Первый SELECT",
					"Фильтрация данных",
					"Сортировка",
					"Агрегатные функции",
					"Группировка",
					"Связи таблиц",
					"JOIN",
					"Изменение данных",
					"Практика",
				].map((title, i) => ({ id: `sql-${i}`, title })),
			},
		],
	},
];

export function lessons(course: Course) {
	return course.modules.flatMap((module) => module.lessons);
}
export function lessonKey(courseId: string, lessonId: string) {
	return `${courseId}/${lessonId}`;
}
export const initialState: DemoState = {
	version: 1,
	completed: courses.flatMap((course) =>
		lessons(course)
			.slice(0, course.initialCompleted)
			.map((lesson) => lessonKey(course.id, lesson.id)),
	),
	submission: { url: "", comment: "", status: "draft" },
	started: ["java", "git"],
};
export function completedCount(course: Course, state: DemoState) {
	return lessons(course).filter((lesson) =>
		state.completed.includes(lessonKey(course.id, lesson.id)),
	).length;
}
export function currentLesson(course: Course, state: DemoState) {
	return (
		lessons(course).find(
			(lesson) => !state.completed.includes(lessonKey(course.id, lesson.id)),
		) ?? lessons(course)[0]
	);
}
export function isAvailable(course: Course, lesson: Lesson, state: DemoState) {
	const list = lessons(course);
	const index = list.findIndex((item) => item.id === lesson.id);
	return (
		index === 0 ||
		state.completed.includes(lessonKey(course.id, list[index - 1].id))
	);
}
export function courseHref(course: Course) {
	return `/?view=course&course=${course.id}`;
}
export function lessonHref(course: Course, lesson: Lesson, assignment = false) {
	return `/?view=lesson&course=${course.id}&lesson=${lesson.id}${assignment ? "&assignment=1" : ""}`;
}
export const storageKey = "advent-academy-demo-v1";
export function parseState(raw: string | null): DemoState {
	if (!raw) return initialState;
	try {
		const parsed: unknown = JSON.parse(raw);
		if (!parsed || typeof parsed !== "object") return initialState;
		const candidate = parsed as Partial<DemoState>;
		if (
			candidate.version !== 1 ||
			!Array.isArray(candidate.completed) ||
			!Array.isArray(candidate.started)
		)
			return initialState;
		const submission = candidate.submission;
		if (
			!submission ||
			typeof submission.url !== "string" ||
			typeof submission.comment !== "string" ||
			!["draft", "review", "changes", "accepted"].includes(submission.status)
		)
			return initialState;
		const known = new Set(
			courses.flatMap((course) =>
				lessons(course).map((lesson) => lessonKey(course.id, lesson.id)),
			),
		);
		const completed = candidate.completed.filter(
			(key): key is string => typeof key === "string" && known.has(key),
		);
		// Only a contiguous sequence is valid; local demo data must not unlock gaps.
		const normalized = courses.flatMap((course) => {
			const result: string[] = [];
			for (const lesson of lessons(course)) {
				const key = lessonKey(course.id, lesson.id);
				if (!completed.includes(key)) break;
				result.push(key);
			}
			return result;
		});
		const accepted = normalized.includes("java/conditions");
		return {
			version: 1,
			completed: normalized,
			started: candidate.started.filter(
				(id): id is string =>
					typeof id === "string" && courses.some((course) => course.id === id),
			),
			submission: {
				...submission,
				status: accepted
					? "accepted"
					: submission.status === "accepted"
						? "draft"
						: submission.status,
			},
		};
	} catch {
		return initialState;
	}
}
