import type { Metadata } from "next";
import { PublicCatalog } from "@/components/public/catalog";
import { Breadcrumbs } from "@/components/public/ui";
export const metadata: Metadata = {
	title: "Курсы",
	description:
		"Java с нуля и Java Advanced, партнёрское направление английского и готовящееся направление Go.",
};
export default async function Page({
	searchParams,
}: {
	searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
	const params = await searchParams;
	const direction =
		typeof params.direction === "string" &&
		["java", "english", "go"].includes(params.direction)
			? params.direction
			: "all";
	const level =
		typeof params.level === "string" &&
		["beginner", "advanced"].includes(params.level)
			? params.level
			: "all";
	return (
		<>
			<Breadcrumbs items={[{ label: "Курсы" }]} />
			<div className="public-page-heading">
				<h1>Выбери направление и свой уровень</h1>
				<p className="public-lead">
					Java — два маршрута обучения. Английский — у нашего партнёра. Go —
					готовится.
				</p>
			</div>
			<PublicCatalog initialDirection={direction} initialLevel={level} />
		</>
	);
}
