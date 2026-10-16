import Link from "next/link";
import {
	ArrowRight,
	BookOpen,
	CheckCheck,
	Code2,
	Coffee,
	FileText,
	GitBranch,
	Landmark,
	LockKeyhole,
	MessagesSquare,
	ShieldCheck,
	type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import { publicCourses, type JavaRoute } from "@/lib/public-courses";
import { ApplyButton } from "./application";

export function TextLink({
	href,
	children,
}: {
	href: string;
	children: ReactNode;
}) {
	return (
		<Link className="public-text-link" href={href}>
			{children}
			<ArrowRight size={18} aria-hidden="true" />
		</Link>
	);
}
export function Section({
	title,
	intro,
	children,
	id,
}: {
	title: string;
	intro?: string;
	children: ReactNode;
	id?: string;
}) {
	return (
		<section id={id} className="public-section" data-public-reveal>
			<div className="public-section-heading">
				<h2>{title}</h2>
				{intro && <p>{intro}</p>}
			</div>
			{children}
		</section>
	);
}
export function Breadcrumbs({
	items,
}: {
	items: { label: string; href?: string }[];
}) {
	return (
		<nav className="public-breadcrumbs" aria-label="Хлебные крошки">
			<ol>
				<li>
					<Link href="/">Главная</Link>
				</li>
				{items.map((item) => (
					<li key={item.label}>
						{item.href ? (
							<Link href={item.href}>{item.label}</Link>
						) : (
							<span aria-current="page">{item.label}</span>
						)}
					</li>
				))}
			</ol>
		</nav>
	);
}
export function PartnerMention() {
	return (
		<div className="public-partner">
			<BookOpen size={28} aria-hidden="true" />
			<div>
				<strong>Английский с Галиной Свириной</strong>
				<span className="public-badge">Партнёр</span>
				<p>Программа и условия — на сайте партнёра.</p>
				<p className="public-fineprint">Адрес сайта партнёра уточняется.</p>
			</div>
		</div>
	);
}
export function CourseCards({
	applications = false,
	query = "",
}: {
	applications?: boolean;
	query?: string;
}) {
	return (
		<div className="public-grid two">
			{publicCourses.map((course) => (
				<article className="public-course-card" key={course.id}>
					<Coffee
						className="public-course-symbol"
						size={56}
						strokeWidth={1.4}
						aria-hidden="true"
					/>
					<span className="public-badge">{course.level}</span>
					<h3>{course.title}</h3>
					<p>{course.description}</p>
					{applications && (
						<p className="public-fineprint">{course.requirements}</p>
					)}
					<div className="public-actions">
						<Link className="public-button primary" href={course.href + query}>
							{applications
								? "Подробнее"
								: course.id === "beginner"
									? "Программа с нуля"
									: "Программа Advanced"}
							<ArrowRight size={18} aria-hidden="true" />
						</Link>
						{applications && <ApplyButton route={course.id} className="text" />}
					</div>
				</article>
			))}
		</div>
	);
}
export function GoCard() {
	return (
		<article className="public-direction-card public-go">
			<div className="public-direction-icon">
				<Code2 size={44} strokeWidth={1.4} aria-hidden="true" />
				<LockKeyhole size={18} aria-hidden="true" />
			</div>
			<div>
				<h3>Go-разработка</h3>
				<span className="public-badge neutral">Скоро</span>
				<p>Готовим программу. Запись пока закрыта.</p>
			</div>
		</article>
	);
}
export function Workflow({ compact = false }: { compact?: boolean }) {
	const steps: [string, string, LucideIcon][] = [
		["Задача", "Понимаем, что нужно сделать", FileText],
		["Код", "Пишем решение", Code2],
		["Проверка", "Запускаем и ищем ошибки", ShieldCheck],
		["Разбор", "Объясняем, почему так", MessagesSquare],
	];
	return (
		<ol
			className={`public-workflow ${compact ? "compact" : ""}`}
			aria-label="От задачи к разбору решения"
		>
			{steps.map(([title, description, Icon]) => (
				<li key={title}>
					<Icon size={28} strokeWidth={1.6} aria-hidden="true" />
					<div>
						<strong>{title}</strong>
						{!compact && <span>{description}</span>}
					</div>
				</li>
			))}
		</ol>
	);
}
export function SystemDiagram() {
	return (
		<figure
			className="public-system-diagram"
			aria-label="Учебная схема: REST API, Java-сервис и Kafka"
		>
			<p className="public-fineprint">Учебный сценарий</p>
			<ol>
				<li>
					<Code2 size={24} aria-hidden="true" />
					<strong>API</strong>
					<span>REST</span>
				</li>
				<li>
					<GitBranch size={24} aria-hidden="true" />
					<strong>Service</strong>
					<span>Java</span>
				</li>
				<li>
					<MessagesSquare size={24} aria-hidden="true" />
					<strong>Kafka</strong>
					<span>События</span>
				</li>
			</ol>
			<p className="public-fineprint">Тесты · Логи · Контракты</p>
		</figure>
	);
}
export function PracticeCard() {
	return (
		<article className="public-practice-card">
			<figure
				className="public-bank-diagram"
				aria-label="Учебная схема повторного события"
			>
				<Landmark size={60} strokeWidth={1.2} aria-hidden="true" />
				<div className="public-event">
					<span>Событие</span>
					<code>evt-001</code>
				</div>
				<ArrowRight size={24} aria-hidden="true" />
				<div className="public-event duplicate">
					<span>Повтор</span>
					<code>evt-001</code>
				</div>
				<p>Как не повторить операцию?</p>
			</figure>
			<div>
				<span className="public-badge">Сценарий Advanced</span>
				<h3>Учебная банковская система</h3>
				<p>Исследуем поведение сервисов при повторных событиях и ошибках.</p>
				<ul className="public-check-list">
					<li>
						<CheckCheck size={18} aria-hidden="true" />
						Повторное событие без повторной операции
					</li>
					<li>
						<CheckCheck size={18} aria-hidden="true" />
						Тест, который воспроизводит ошибку
					</li>
					<li>
						<CheckCheck size={18} aria-hidden="true" />
						Разбор выбранного решения
					</li>
				</ul>
				<TextLink href="/practice/event-redelivery">Разобрать пример</TextLink>
			</div>
		</article>
	);
}
export function Closing({
	title = "Выбери маршрут под свою подготовку",
	description = "Обсудим программу, формат и условия до начала обучения",
	route = "unsure",
}: {
	title?: string;
	description?: string;
	route?: JavaRoute;
}) {
	return (
		<section className="public-closing" data-public-reveal>
			<div>
				<h2>{title}</h2>
				<p>{description}</p>
			</div>
			<ApplyButton route={route} />
		</section>
	);
}
export function Accordion({
	items,
	initial = 0,
	program = false,
}: {
	items: string[][];
	initial?: number;
	program?: boolean;
}) {
	return (
		<div className={`public-accordion ${program ? "program" : ""}`}>
			{items.map(([title, body], i) => (
				<details
					key={title}
					id={program ? `module-${i + 1}` : undefined}
					open={i === initial}
				>
					<summary>
						{program && (
							<span className="public-module-number">
								{String(i + 1).padStart(2, "0")}
							</span>
						)}
						<span>{title}</span>
						<span className="public-disclosure" aria-hidden="true">
							+
						</span>
					</summary>
					<div>
						<p>{body}</p>
					</div>
				</details>
			))}
		</div>
	);
}
