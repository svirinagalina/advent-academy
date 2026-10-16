import Link from "next/link";
import { PublicShell } from "@/components/public/shell";
export default function NotFound() {
	return (
		<PublicShell>
			<section className="public-page-heading public-document">
				<h1>Страница не найдена</h1>
				<p className="public-lead">
					Возможно, адрес изменился. Выбери направление или вернись на главную.
				</p>
				<div className="public-actions">
					<Link className="public-button primary" href="/courses">
						В каталог курсов
					</Link>
					<Link className="public-button secondary" href="/">
						На главную
					</Link>
				</div>
			</section>
		</PublicShell>
	);
}
