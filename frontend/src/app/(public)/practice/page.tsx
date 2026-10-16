import type { Metadata } from "next";
import {
	Breadcrumbs,
	Closing,
	PracticeCard,
	Section,
	Workflow,
} from "@/components/public/ui";
export const metadata: Metadata = {
	title: "Проекты и практика",
	description:
		"Учебные задачи Java: требования, ограничения, проверка и объяснение решения.",
};
export default function Page() {
	return (
		<>
			<Breadcrumbs items={[{ label: "Проекты и практика" }]} />
			<div className="public-page-heading">
				<h1>Практика, в которой нужно разобраться</h1>
				<p className="public-lead">
					Задача, ограничения, проверка и объяснение решения.
				</p>
			</div>
			<Workflow compact />
			<Section title="От повторного события к проверенному изменению">
				<PracticeCard />
			</Section>
			<Section title="Как выглядит работа над задачей">
				<ol className="public-editorial-list">
					<li>
						<h3>Воспроизвести ошибку</h3>
						<p>Собрать признаки и найти минимальный сценарий.</p>
					</li>
					<li>
						<h3>Проверить гипотезу</h3>
						<p>Проследить путь события и условия сбоя.</p>
					</li>
					<li>
						<h3>Изменить код</h3>
						<p>Сохранить соседнее поведение и проверить результат тестом.</p>
					</li>
					<li>
						<h3>Объяснить решение</h3>
						<p>Описать выбор, альтернативы и ограничения.</p>
					</li>
				</ol>
			</Section>
			<Closing />
		</>
	);
}
