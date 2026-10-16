"use client";
import Link from "next/link";
import { ArrowRight, Info } from "lucide-react";
import { createContext, useContext, useState, type ReactNode } from "react";
import type { JavaRoute } from "@/lib/public-courses";
import { PublicDialog } from "./dialog";
const ApplicationContext = createContext<(route: JavaRoute) => void>(() => {});

export function ApplicationProvider({ children }: { children: ReactNode }) {
	const [route, setRoute] = useState<JavaRoute | null>(null);
	return (
		<ApplicationContext.Provider value={setRoute}>
			{children}
			{route && (
				<PublicDialog title="Поступить на курс" close={() => setRoute(null)}>
					<p>Оставь контакт — обсудим программу и условия обучения.</p>
					<div className="public-notice" role="status">
						<Info size={20} aria-hidden="true" />
						<p>
							Приём заявок через сайт пока недоступен. Можно выбрать маршрут и
							познакомиться с программой. Контактные данные сейчас не
							собираются.
						</p>
					</div>
					<form
						className="public-application"
						onSubmit={(event) => event.preventDefault()}
					>
						<label>
							<span id="public-route-label">Маршрут Java</span>
							<select
								aria-labelledby="public-route-label"
								value={route}
								onChange={(event) => setRoute(event.target.value as JavaRoute)}
							>
								<option value="beginner">С нуля</option>
								<option value="advanced">Advanced</option>
								<option value="unsure">Пока не знаю</option>
							</select>
						</label>
						<fieldset
							disabled
							aria-describedby="public-application-unavailable"
						>
							<div className="public-form-row">
								<label>
									Имя
									<input name="name" autoComplete="given-name" />
								</label>
								<label>
									Email
									<input name="email" type="email" autoComplete="email" />
								</label>
							</div>
							<label>
								Что хочешь обсудить? <span>(необязательно)</span>
								<textarea name="message" rows={3} />
							</label>
							<label className="public-consent">
								<input type="checkbox" /> Согласен на обработку персональных
								данных
							</label>
							<button type="submit" className="public-button primary">
								Отправить заявку
							</button>
						</fieldset>
						<p id="public-application-unavailable" className="public-fineprint">
							Форма откроется после публикации условий приёма заявок и политики
							обработки данных.{" "}
							<Link href="/privacy" onClick={() => setRoute(null)}>
								Документы
							</Link>
						</p>
					</form>
					<p className="public-fineprint">
						Ищешь английский? Программа Галины Свириной — партнёрское
						направление. Адрес сайта уточняется.
					</p>
					<div className="public-actions">
						<Link
							className="public-button secondary"
							href={
								route === "unsure" ? "/courses/java" : `/courses/java/${route}`
							}
							onClick={() => setRoute(null)}
						>
							Посмотреть программу <ArrowRight size={18} aria-hidden="true" />
						</Link>
						<button
							type="button"
							className="public-button text"
							onClick={() => setRoute(null)}
						>
							Отмена
						</button>
					</div>
				</PublicDialog>
			)}
		</ApplicationContext.Provider>
	);
}
export function ApplyButton({
	route = "unsure",
	children = "Поступить на курс",
	className = "primary",
	onClick,
}: {
	route?: JavaRoute;
	children?: ReactNode;
	className?: string;
	onClick?: () => void;
}) {
	const open = useContext(ApplicationContext);
	return (
		<button
			type="button"
			className={`public-button ${className}`}
			onClick={() => {
				onClick?.();
				open(route);
			}}
		>
			{children}
			<ArrowRight size={18} aria-hidden="true" />
		</button>
	);
}
