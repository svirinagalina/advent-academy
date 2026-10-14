import type { Metadata } from "next";
import { Golos_Text, JetBrains_Mono } from "next/font/google";
import type { ReactNode } from "react";
import "./globals.css";

const sans = Golos_Text({
	subsets: ["latin", "cyrillic"],
	display: "swap",
	variable: "--font-sans",
});
const mono = JetBrains_Mono({
	subsets: ["latin", "cyrillic"],
	display: "swap",
	variable: "--font-mono",
});

export const metadata: Metadata = {
	title: {
		default: "Кабинет ученика | Advent Academy",
		template: "%s | Advent Academy",
	},
	description:
		"Курсы, учебные материалы и практические задания Advent Academy.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
	return (
		<html lang="ru">
			<body className={`${sans.variable} ${mono.variable}`}>{children}</body>
		</html>
	);
}
