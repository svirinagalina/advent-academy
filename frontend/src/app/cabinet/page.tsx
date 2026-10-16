import type { Metadata } from "next";
import { CabinetEntry } from "@/components/cabinet-entry";
export const metadata: Metadata = { title: "Кабинет ученика" };
export default function Page() {
	return <CabinetEntry />;
}
