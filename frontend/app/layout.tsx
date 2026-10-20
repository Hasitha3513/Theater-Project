import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import "../styles/breakpoints.css";
import { ApplicationShell } from "../components/shell";

export const metadata: Metadata = {
	title: "CineNest · Private cinema",
	description:
		"Explore cinema rooms and indicative packages with CineNest. Synthetic booking environment; real payments and physical access are not enabled.",
	robots: { index: false, follow: false },
};

export default function RootLayout({
	children,
}: Readonly<{ children: ReactNode }>) {
	return (
		<html lang="en">
			<body>
				<ApplicationShell>{children}</ApplicationShell>
			</body>
		</html>
	);
}
