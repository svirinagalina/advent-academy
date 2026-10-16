import type { Metadata } from "next";
import { Breadcrumbs, TextLink } from "@/components/public/ui";
export const metadata: Metadata = {
	title: "Документы",
	description: "Информация о публикации документов и доступности формы заявки.",
	robots: { index: false, follow: true },
};
export default function Page() {
	return (
		<>
			<Breadcrumbs items={[{ label: "Документы" }]} />
			<div className="public-page-heading">
				<h1>Документы</h1>
				<p className="public-lead">
					Политика обработки персональных данных для заявок ещё не опубликована.
				</p>
			</div>
			<section className="public-reading public-document">
				<h2>Приём заявок через сайт пока недоступен</h2>
				<p>
					До публикации утверждённых условий и политики форма не собирает и не
					отправляет контактные данные. Этот экран не заменяет юридический
					документ.
				</p>
				<TextLink href="/courses">В каталог курсов</TextLink>
			</section>
		</>
	);
}
