import type { Metadata } from "next";
import Link from "next/link";
import { ApplyButton } from "@/components/public/application";
import { Breadcrumbs, PartnerMention } from "@/components/public/ui";
export const metadata: Metadata = {
	title: "Контакты",
	description:
		"Обсуждение подготовки и задач для обучения Java. Информация о доступности приёма заявок.",
};
export default function Page() {
	return (
		<>
			<Breadcrumbs items={[{ label: "Контакты" }]} />
			<div className="public-page-heading">
				<h1>Давай обсудим обучение</h1>
				<p className="public-lead">
					Расскажи о своей подготовке и задачах, которые хочешь научиться
					решать.
				</p>
			</div>
			<div className="public-contact-layout">
				<section className="public-contact-card">
					<h2>Маршруты Java</h2>
					<p>Программу, формат и условия обучения обсудим до начала занятий.</p>
					<p className="public-notice">
						Приём заявок через сайт пока недоступен. Контактный канал будет
						опубликован здесь.
					</p>
					<ApplyButton />
					<p className="public-fineprint">
						Уже учишься в академии? <Link href="/login">Войти в кабинет</Link>
					</p>
				</section>
				<section className="public-contact-card">
					<h2>Английский у партнёра</h2>
					<PartnerMention />
				</section>
			</div>
		</>
	);
}
