import Link from "next/link";
import { ArrowRight, BookOpen, Code2, CircleHelp } from "lucide-react";
import {
	Closing,
	CourseCards,
	GoCard,
	PartnerMention,
	PracticeCard,
	Section,
	TextLink,
	Workflow,
} from "./ui";
export function PublicHome() {
	return (
		<>
			<section className="public-hero public-home-hero">
				<div className="public-hero-copy" data-public-reveal>
					<p className="public-kicker">Практическая разработка</p>
					<h1>Java: с нуля — к практике. С опытом — к глубине.</h1>
					<p className="public-lead">
						Освой основы или укрепи знания после других курсов. Пиши код,
						разбирай ошибки и учись объяснять свои решения.
					</p>
					<div className="public-actions">
						<Link href="/courses/java" className="public-button primary">
							Выбрать маршрут Java <ArrowRight size={18} aria-hidden="true" />
						</Link>
						<Link href="/practice" className="public-button secondary">
							Посмотреть практику
						</Link>
					</div>
				</div>
				<div className="public-hero-art" data-public-reveal>
					<Workflow />
				</div>
			</section>
			<Section
				title="Твой следующий шаг в Java"
				intro="Начни с основ или выбери программу для тех, у кого уже есть база"
			>
				<CourseCards />
			</Section>
			<Section
				title="Знакомо после обучения?"
				intro="Выбирай программу по задачам, которые пока вызывают трудности"
			>
				<div className="public-grid three public-situations">
					{[
						[BookOpen, "По уроку получается. Самостоятельно — сложнее"],
						[Code2, "Проект запускается. Причина ошибки непонятна"],
						[CircleHelp, "Термины знакомы. Решение объяснить трудно"],
					].map(([Icon, title]) => {
						const FeatureIcon = Icon as typeof BookOpen;
						return (
							<article key={String(title)}>
								<FeatureIcon size={24} aria-hidden="true" />
								<h3>{String(title)}</h3>
							</article>
						);
					})}
				</div>
				<p className="public-section-note">
					Начнём с твоей подготовки и постепенно перейдём к более
					самостоятельной работе.
				</p>
				<TextLink href="/courses/java/advanced#requirements">
					Посмотреть требования Advanced
				</TextLink>
			</Section>
			<Section title="Наши направления">
				<div className="public-grid three">
					<article className="public-direction-card">
						<Code2 size={40} aria-hidden="true" />
						<div>
							<h3>Java</h3>
							<p>Два маршрута: с нуля и Advanced.</p>
							<TextLink href="/courses/java">Подробнее</TextLink>
						</div>
					</article>
					<article className="public-direction-card">
						<PartnerMention />
					</article>
					<GoCard />
				</div>
			</Section>
			<Section title="Практика с понятным результатом">
				<PracticeCard />
			</Section>
			<section className="public-author-strip" data-public-reveal>
				<div>
					<h2>Академия новая. Опыт — уже есть.</h2>
					<p>Обучение строится на опыте backend-разработки и менторства.</p>
				</div>
				<div>
					<strong>Иван Пшыченко</strong>
					<span>Java-разработчик и ментор</span>
					<TextLink href="/about">Об академии</TextLink>
				</div>
			</section>
			<Closing />
			<noscript>
				<p className="public-notice">
					Основное содержание доступно без JavaScript. Для переключения темы,
					фильтров, открытия меню и окна заявки включите JavaScript. Все
					программы доступны в <Link href="/courses">каталоге курсов</Link>.
				</p>
			</noscript>
		</>
	);
}
