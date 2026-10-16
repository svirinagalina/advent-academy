import type { Metadata } from "next";
import { CheckCheck, GitBranch, SearchCheck } from "lucide-react";
import { ApplyButton } from "@/components/public/application";
import {
	Breadcrumbs,
	Closing,
	Section,
	SystemDiagram,
	TextLink,
} from "@/components/public/ui";
export const metadata: Metadata = {
	title: "Повторная обработка события",
	description:
		"Учебный сценарий Java Advanced: идемпотентность, транзакции и проверка повторной доставки события.",
};
export default function Page() {
	return (
		<>
			<Breadcrumbs
				items={[
					{ label: "Практика", href: "/practice" },
					{ label: "Повторная обработка события" },
				]}
			/>
			<section className="public-hero">
				<div>
					<p className="public-kicker">Учебный сценарий Java Advanced</p>
					<h1>Одно событие. Две обработки. Как не повторить операцию?</h1>
					<p className="public-lead">
						Разбираем не только успешный запрос, но и поведение системы при
						повторной доставке сообщения.
					</p>
					<div className="public-actions">
						<TextLink href="/courses/java/advanced">
							Посмотреть программу Advanced
						</TextLink>
						<ApplyButton route="advanced" />
					</div>
				</div>
				<SystemDiagram />
			</section>
			<Section title="Задача">
				<p className="public-reading">
					Событие о переводе пришло повторно. Нужно сохранить корректное
					состояние и проверить результат.
				</p>
				<div className="public-event-flow">
					<div>
						<code>evt-001</code>
						<strong>Первое событие</strong>
					</div>
					<div>
						<code>evt-001</code>
						<strong>Повторная доставка</strong>
					</div>
					<div>
						<CheckCheck size={28} aria-hidden="true" />
						<strong>Операция выполнена один раз</strong>
					</div>
				</div>
				<p className="public-fineprint">
					Иллюстративная учебная схема. Реальные банковские операции и данные
					клиентов здесь не используются.
				</p>
			</Section>
			<Section title="Что исследуем">
				<ul className="public-editorial-list">
					<li>
						<h3>Границы транзакции</h3>
						<p>Какие изменения должны сохраняться вместе?</p>
					</li>
					<li>
						<h3>Идемпотентность</h3>
						<p>Как различить повтор и новое событие?</p>
					</li>
					<li>
						<h3>Поведение при сбое</h3>
						<p>Что произойдёт после частичного выполнения?</p>
					</li>
					<li>
						<h3>Тест повторной обработки</h3>
						<p>
							Какая проверка воспроизводит проблему и подтверждает исправление?
						</p>
					</li>
				</ul>
			</Section>
			<Section title="Работа над решением">
				<p className="public-reading">
					Воспроизвести ошибку → проверить гипотезу → изменить код → объяснить
					решение.
				</p>
				<div className="public-grid three public-requirements">
					<article>
						<GitBranch size={26} aria-hidden="true" />
						<h3>Изменение в репозитории</h3>
						<p>Небольшой diff в границах задачи.</p>
					</article>
					<article>
						<SearchCheck size={26} aria-hidden="true" />
						<h3>Тест проблемного сценария</h3>
						<p>Проверка повторной доставки и сбоя.</p>
					</article>
					<article>
						<CheckCheck size={26} aria-hidden="true" />
						<h3>Объяснение решения</h3>
						<p>Выбранный подход, ограничения и альтернативы.</p>
					</article>
				</div>
				<p className="public-section-note">
					Универсального решения для любой системы нет. В учебной задаче
					отдельно определяются границы транзакции, уникальность события и
					гарантии обработки.
				</p>
			</Section>
			<Closing
				title="Разберём твою подготовку и подберём задачи"
				route="advanced"
			/>
		</>
	);
}
