import type { Metadata } from "next";
import {
	Breadcrumbs,
	Closing,
	Section,
	TextLink,
} from "@/components/public/ui";
export const metadata: Metadata = {
	title: "Об академии",
	description:
		"Advent Academy: опыт backend-разработки и преподавания, самостоятельная практика и объяснение решений.",
};
export default function Page() {
	return (
		<>
			<Breadcrumbs items={[{ label: "Об академии" }]} />
			<div className="public-page-heading">
				<h1>Академия новая. Опыт — уже есть.</h1>
				<p className="public-lead">
					Advent Academy создаётся на основе опыта backend-разработки и
					преподавания. Здесь важно не только получить работающий ответ, но и
					понять, почему решение работает и где у него ограничения.
				</p>
			</div>
			<section className="public-author-profile">
				<div className="public-author-initials" aria-hidden="true">
					ИП
				</div>
				<div>
					<h2>Иван Пшыченко</h2>
					<p>Java-разработчик и ментор</p>
					<p>
						Обучение строится вокруг понятных задач, самостоятельной работы и
						проверки результата.
					</p>
				</div>
			</section>
			<Section title="Какой подход лежит в основе">
				<ol className="public-editorial-list">
					<li>
						<h3>Понятная последовательность</h3>
						<p>
							От темы и небольшого упражнения — к более самостоятельной работе.
						</p>
					</li>
					<li>
						<h3>Самостоятельная практика</h3>
						<p>
							Нужно не повторить готовые шаги, а разобраться в условиях задачи.
						</p>
					</li>
					<li>
						<h3>Проверка результата</h3>
						<p>
							Работающий успешный сценарий — начало проверки, а не её
							завершение.
						</p>
					</li>
					<li>
						<h3>Обсуждение решений</h3>
						<p>Понимание компромиссов важно так же, как и написанный код.</p>
					</li>
				</ol>
			</Section>
			<TextLink href="/courses/java">Посмотреть маршруты Java</TextLink>
			<Closing />
		</>
	);
}
