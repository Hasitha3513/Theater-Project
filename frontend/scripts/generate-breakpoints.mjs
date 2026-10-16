import { readFileSync, writeFileSync } from "node:fs";
const { breakpoint } = JSON.parse(
	readFileSync(
		new URL(
			"../../knowledge/tokens/cinenest-design-tokens.json",
			import.meta.url,
		),
	),
);
export const generated = `/* Generated from canonical breakpoint tokens; do not change values independently. */
@media (min-width: ${breakpoint.sm}px) { .topbar .preview-label { display: inline; } .example-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .banner-mark { display: block; } }
@media (min-width: ${breakpoint.md}px) { .page-content { padding: var(--cn-spacing-6); } .content-grid, .cinema-hero { grid-template-columns: repeat(2, minmax(0, 1fr)); } .card { padding: var(--cn-spacing-6); } }
@media (min-width: ${breakpoint.lg}px) { .admin-shell { display: grid; grid-template-columns: var(--cn-layout-sidebar) minmax(0, 1fr); } .sidebar { display: flex; flex-direction: column; } .sidebar-home { margin-top: auto; } .menu-toggle { display: none; } .desktop-customer-nav { display: block; } .desktop-customer-nav nav ul { display: flex; } .desktop-customer-nav a { color: var(--cn-color-cinematic-text); } .page-content { padding: var(--cn-spacing-8); } .example-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
`;
if (process.argv.includes("--write"))
	writeFileSync(
		new URL("../styles/breakpoints.css", import.meta.url),
		generated,
	);
