import type { Metadata } from "next";
import { CabinetEntry } from "@/components/cabinet-entry";
export const metadata: Metadata = {
	title: "Войти в кабинет",
	robots: { index: false, follow: false },
};
export default function Page() {
	return <CabinetEntry />;
}
