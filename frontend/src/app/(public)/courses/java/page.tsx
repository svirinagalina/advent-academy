import type { Metadata } from "next";
import { ApplyButton } from "@/components/public/application";
import {
	Breadcrumbs,
	Closing,
	CourseCards,
	Section,
} from "@/components/public/ui";
export const metadata: Metadata = {
	title: "Направление Java",
	description:
		"Сравни Java с нуля и Advanced: подготовку, программу, практику и результаты собственной работы.",
};
export default function Page() {
	return (
		<>
			<Breadcrumbs
				items={[{ label: "Курсы", href: "/courses" }, { label: "Java" }]}
			/>
			<div className="public-page-heading">
				<p className="public-kicker">Направление Java</p>
				<h1>Два маршрута. Одна цель — понимать свою работу.</h1>
				<p className="public-lead">
					Выбери старт по своей подготовке. Advanced не требует повторять весь
					путь с нуля.
				</p>
			</div>
			<CourseCards />
			<Section title="С чего начать?">
				<div className="public-comparison">
					<article>
						<h3>Java с нуля</h3>
						<dl>
							<dt>Начало</dt>
							<dd>Без опыта программирования</dd>
							<dt>Основная работа</dt>
							<dd>Упражнения и первый backend</dd>
							<dt>Темы</dt>
							<dd>Основы языка, данные и backend</dd>
							<dt>Результат</dt>
							<dd>Собственный проект с понятным запуском</dd>
						</dl>
					</article>
					<article>
						<h3>Java Advanced</h3>
						<dl>
							<dt>Начало</dt>
							<dd>ООП, коллекции, SQL, основы HTTP и Git</dd>
							<dt>Основная работа</dt>
							<dd>Изменение существующего проекта и сложные сценарии</dd>
							<dt>Темы</dt>
							<dd>Конкурентность, события, диагностика и архитектура</dd>
							<dt>Результат</dt>
							<dd>Проверенные изменения, тесты и обоснования</dd>
						</dl>
					</article>
				</div>
			</Section>
			<section className="public-help-strip">
				<div>
					<h2>Не уверен, какой маршрут выбрать?</h2>
					<p>Обсудим подготовку. Это не автоматический тест уровня.</p>
				</div>
				<ApplyButton route="unsure" className="secondary">
					Обсудить подготовку
				</ApplyButton>
			</section>
			<Closing />
		</>
	);
}
