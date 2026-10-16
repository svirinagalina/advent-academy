import Link from "next/link";
import type { ReactNode } from "react";
import { ApplicationProvider } from "./application";
import { PublicNavigation } from "./navigation";
import { PublicMotion } from "./motion";
export function PublicShell({ children }: { children: ReactNode }) {
	return (
		<div className="public-site">
			<ApplicationProvider>
				<a href="#public-content" className="skip-link">
					К содержимому
				</a>
				<PublicNavigation />
				<main id="public-content" tabIndex={-1} className="public-container">
					{children}
				</main>
				<footer className="public-footer public-container">
					<div>
						<Link href="/" className="public-logo">
							<span>Advent</span> Academy
						</Link>
						<nav aria-label="Навигация в подвале">
							<Link href="/courses">Курсы</Link>
							<Link href="/practice">Проекты и практика</Link>
							<Link href="/about">Об академии</Link>
							<Link href="/contacts">Контакты</Link>
							<Link href="/privacy">Документы</Link>
						</nav>
						<span className="public-footer-partner">
							Английский с Галиной Свириной
							<span>Партнёрское направление · адрес уточняется</span>
						</span>
					</div>
					<p>
						© Advent Academy, 2026 <span>Обучение. Практика. Развитие.</span>
					</p>
				</footer>
				<PublicMotion />
			</ApplicationProvider>
		</div>
	);
}
