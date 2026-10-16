import Link from "next/link";
import { Badge, Card, PageHeading } from "../../components/primitives";
export default function AdminHome() {
	return (
		<>
			<PageHeading
				eyebrow="CINENEST · ADMINISTRATION PREVIEW"
				title="A clear space to manage."
			>
				The shared interface foundation. Business modules are not connected.
			</PageHeading>
			<div className="workspace-banner">
				<div>
					<Badge tone="info">Shell preview</Badge>
					<h2>Built for the next chapter.</h2>
					<p>
						Consistent navigation, accessible controls and clear states — ready
						for approved feature work.
					</p>
					<Link className="button button-primary" href="/admin/components">
						Explore UI examples
					</Link>
				</div>
				<span className="banner-mark" aria-hidden="true">
					◉
				</span>
			</div>
			<div className="content-grid">
				<Card title="One visual language">
					<p>
						Navy navigation, a light workspace and existing violet actions.
						Shared tokens keep the customer and administration experiences
						consistent.
					</p>
					<Link href="/admin/components">View reusable primitives →</Link>
				</Card>
				<Card title="Access stays with the server">
					<p>
						This public shell preview does not establish a role, venue grant or
						permission. Future modules remain unavailable.
					</p>
					<Link href="/admin/account">Check your account session →</Link>
				</Card>
			</div>
			<Card title="Operational information">
				<p className="muted">
					No operational metrics are available. No generated screenshot
					statistics, customer records or live device statuses are shown.
				</p>
			</Card>
		</>
	);
}
