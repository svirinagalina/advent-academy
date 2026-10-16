"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronDown, LockKeyhole, Menu } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ThemeToggle } from "@/components/theme";
import { publicCourses } from "@/lib/public-courses";
import { ApplyButton } from "./application";
import { PublicDialog } from "./dialog";

export function PublicNavigation() {
	const pathname = usePathname();
	const [mobile, setMobile] = useState(false);
	const [expanded, setExpanded] = useState(false);
	const dropdown = useRef<HTMLDetailsElement>(null);
	useEffect(() => {
		const outside = (event: PointerEvent) => {
			if (
				event.target instanceof Node &&
				!dropdown.current?.contains(event.target) &&
				dropdown.current
			)
				dropdown.current.open = false;
		};
		const breakpoint = matchMedia("(min-width: 1200px)");
		const resize = () => {
			if (breakpoint.matches) setMobile(false);
		};
		document.addEventListener("pointerdown", outside);
		breakpoint.addEventListener("change", resize);
		return () => {
			document.removeEventListener("pointerdown", outside);
			breakpoint.removeEventListener("change", resize);
		};
	}, []);
	const links = (
		<>
			{publicCourses.map((course) => (
				<Link
					key={course.id}
					href={course.href}
					aria-current={pathname === course.href ? "page" : undefined}
					onClick={() => {
						setMobile(false);
						if (dropdown.current) dropdown.current.open = false;
					}}
				>
					<strong>{course.title}</strong>
					<span>
						{course.level === "Есть база" ? "Есть основы Java" : course.level}
					</span>
				</Link>
			))}
			<div className="public-menu-note">
				<strong>Английский с Галиной Свириной</strong>
				<span>Партнёрское направление · адрес уточняется</span>
			</div>
			<div className="public-menu-note">
				<strong>
					<LockKeyhole size={16} aria-hidden="true" /> Go-разработка
				</strong>
				<span>Скоро · запись пока закрыта</span>
			</div>
			<Link
				href="/courses"
				onClick={() => {
					setMobile(false);
					if (dropdown.current) dropdown.current.open = false;
				}}
			>
				Все направления
			</Link>
		</>
	);
	return (
		<header className="public-header">
			<div className="public-header-inner">
				<Link
					href="/"
					className="public-logo"
					aria-label="Advent Academy — главная"
				>
					<span>Advent</span> Academy
				</Link>
				<nav className="public-desktop-nav" aria-label="Основная навигация">
					<details
						ref={dropdown}
						className="public-dropdown"
						onToggle={(event) => setExpanded(event.currentTarget.open)}
						onKeyDown={(event) => {
							if (event.key === "Escape" && dropdown.current) {
								dropdown.current.open = false;
								dropdown.current.querySelector("summary")?.focus();
							}
						}}
					>
						{/* biome-ignore lint/a11y/useSemanticElements: Native summary preserves no-JS disclosure; its button role exposes the ARIA state consistently. */}
						<summary
							role="button"
							aria-expanded={expanded}
							aria-controls="public-course-menu"
						>
							Курсы <ChevronDown size={16} aria-hidden="true" />
						</summary>
						<div id="public-course-menu" className="public-course-menu">
							{links}
						</div>
					</details>
					<Link
						href="/practice"
						aria-current={pathname.startsWith("/practice") ? "page" : undefined}
					>
						Как учим
					</Link>
					<Link
						href="/about"
						aria-current={pathname === "/about" ? "page" : undefined}
					>
						Об академии
					</Link>
				</nav>
				<div className="public-header-actions">
					<span className="public-header-partner">
						Английский с Галиной<span>Адрес партнёра уточняется</span>
					</span>
					<ThemeToggle />
					<Link
						href="/login"
						className="public-button secondary public-desktop-action"
					>
						Войти
					</Link>
					<div className="public-desktop-action">
						<ApplyButton />
					</div>
					<button
						type="button"
						className="public-icon-button public-menu-toggle"
						aria-label="Открыть меню"
						aria-expanded={mobile}
						aria-haspopup="dialog"
						onClick={() => setMobile(true)}
					>
						<Menu size={24} aria-hidden="true" />
					</button>
				</div>
				{mobile && (
					<PublicDialog
						title="Advent Academy"
						className="public-mobile-dialog"
						close={() => setMobile(false)}
					>
						<nav aria-label="Мобильная навигация">
							<h3>Курсы</h3>
							{links}
							<Link href="/practice" onClick={() => setMobile(false)}>
								Как учим
							</Link>
							<Link href="/about" onClick={() => setMobile(false)}>
								Об академии
							</Link>
							<Link href="/contacts" onClick={() => setMobile(false)}>
								Контакты
							</Link>
							<ApplyButton onClick={() => setMobile(false)} />
							<Link
								className="public-button secondary"
								href="/login"
								onClick={() => setMobile(false)}
							>
								Войти
							</Link>
						</nav>
					</PublicDialog>
				)}
			</div>
		</header>
	);
}
