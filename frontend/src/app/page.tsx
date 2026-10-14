import { Suspense } from "react";
import Academy from "@/components/academy";

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
			<Academy />
		</Suspense>
	);
}
