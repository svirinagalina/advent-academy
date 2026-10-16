import type { Metadata } from "next";
import { Golos_Text, JetBrains_Mono } from "next/font/google";
import type { ReactNode } from "react";
import "./globals.css";
import "./user-admin.css";
import "./public-site.css";
import { ThemeProvider } from "@/components/theme";

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
		default: "Advent Academy — Java и практика разработки",
		template: "%s — Advent Academy",
	},
	description:
		"Java с нуля и Java Advanced: самостоятельная практика, проверка результата и объяснение решений.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
	return (
		<html lang="ru" suppressHydrationWarning>
			<head>
			<script
				// biome-ignore lint/security/noDangerouslySetInnerHtml: Static theme initializer contains no user data.
				dangerouslySetInnerHTML={{
					__html: `(function(){var t;try{t=localStorage.getItem('advent-academy.theme')}catch(e){}document.documentElement.dataset.theme=t==='light'||t==='dark'?t:matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'})()`,
				}}
			/>
			</head>
			<body className={`${sans.variable} ${mono.variable}`}>
				<ThemeProvider>{children}</ThemeProvider>
			</body>
		</html>
	);
}
