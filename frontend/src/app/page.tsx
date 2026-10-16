import { CabinetEntry } from "@/components/cabinet-entry";
import { PublicHome } from "@/components/public/home";
import { PublicShell } from "@/components/public/shell";
export default async function HomePage({
	searchParams,
}: {
	searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
	const params = await searchParams;
	// Existing course, lesson and administration bookmarks retain their behavior.
	if (
		params.view !== undefined ||
		params.course !== undefined ||
		params.lesson !== undefined
	)
		return <CabinetEntry />;
	return (
		<PublicShell>
			<PublicHome />
		</PublicShell>
	);
}
