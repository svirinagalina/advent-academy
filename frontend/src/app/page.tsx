import { Suspense } from "react";
import LiveAcademy from "@/components/live-academy";

export default function HomePage() {
	return (
		<Suspense
			fallback={
				<main className="demo-login card">
					<h1>Advent Academy</h1>
					<p role="status">Загружаем кабинет…</p>
				</main>
			}
		>
			<LiveAcademy />
		</Suspense>
	);
}
