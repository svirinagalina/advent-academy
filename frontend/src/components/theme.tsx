"use client";
import {
	createContext,
	type ReactNode,
	useContext,
	useEffect,
	useState,
} from "react";

const key = "advent-academy.theme";
type Theme = "light" | "dark";
const ThemeContext = createContext<{ theme: Theme; toggle: () => void }>({
	theme: "light",
	toggle: () => {},
});
export function ThemeProvider({ children }: { children: ReactNode }) {
	const [theme, setTheme] = useState<Theme>("light");
	const [announcement, setAnnouncement] = useState("");
	useEffect(() => {
		const media = matchMedia("(prefers-color-scheme: dark)");
		let manual: string | null = null;
		try {
			manual = localStorage.getItem(key);
		} catch {}
		const apply = (value: string | null) => {
			manual = value === "light" || value === "dark" ? value : null;
			const next = manual || (media.matches ? "dark" : "light");
			document.documentElement.dataset.theme = next;
			setTheme(next as Theme);
		};
		apply(manual);
		const system = () => {
			if (!manual) apply(null);
		};
		const storage = (event: StorageEvent) => {
			if (event.key === key || event.key === null) apply(event.newValue);
		};
		const local = () => {
			manual = document.documentElement.dataset.theme || "light";
			setTheme(manual as Theme);
		};
		media.addEventListener("change", system);
		window.addEventListener("storage", storage);
		window.addEventListener("academy-theme", local);
		return () => {
			media.removeEventListener("change", system);
			window.removeEventListener("storage", storage);
			window.removeEventListener("academy-theme", local);
		};
	}, []);
	const toggle = () => {
		const next = theme === "dark" ? "light" : "dark";
		document.documentElement.dataset.theme = next;
		setTheme(next);
		try {
			localStorage.setItem(key, next);
		} catch {}
		window.dispatchEvent(new Event("academy-theme"));
		setAnnouncement(
			next === "dark" ? "Тёмная тема включена" : "Светлая тема включена",
		);
	};
	return (
		<ThemeContext.Provider value={{ theme, toggle }}>
			{children}
			<span className="sr-only" aria-live="polite">
				{announcement}
			</span>
		</ThemeContext.Provider>
	);
}
export function ThemeToggle() {
	const { theme, toggle } = useContext(ThemeContext);
	const label =
		theme === "dark" ? "Включить светлую тему" : "Включить тёмную тему";
	return (
		<button
			type="button"
			className="icon-button theme-toggle"
			onClick={toggle}
			aria-label={label}
			title={label}
		>
			<svg
				width="20"
				height="20"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				strokeWidth="2"
				aria-hidden="true"
			>
				{theme === "dark" ? (
					<>
						<circle cx="12" cy="12" r="4" />
						<path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2" />
					</>
				) : (
					<path d="M20.9 13.2A9 9 0 0 1 10.8 3.1 9 9 0 1 0 20.9 13.2Z" />
				)}
			</svg>
		</button>
	);
}
