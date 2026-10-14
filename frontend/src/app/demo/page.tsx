import { Suspense } from "react";
import Academy from "@/components/academy";
export default function DemoPage() {
	return (
		<Suspense fallback={<p>Загружаем демоверсию…</p>}>
			<Academy />
		</Suspense>
	);
}
