import type { Metadata } from "next";
import { PublicCourse } from "@/components/public/course";
export const metadata: Metadata = {
	title: "Java с нуля",
	description:
		"От первой программы к backend-приложению: основы Java, ООП, коллекции, SQL, Spring Boot и практика.",
};
export default async function Page({
	searchParams,
}: {
	searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
	const params = await searchParams;
	const query = new URLSearchParams();
	if (
		typeof params.direction === "string" &&
		["all", "java", "english", "go"].includes(params.direction)
	)
		query.set("direction", params.direction);
	if (
		typeof params.level === "string" &&
		["all", "beginner", "advanced"].includes(params.level)
	)
		query.set("level", params.level);
	return (
		<PublicCourse
			advanced={false}
			catalogHref={`/courses${query.size ? `?${query}` : ""}`}
		/>
	);
}
