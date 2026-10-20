import Link from "next/link";
import { Badge, Card, PageHeading } from "../../components/primitives";
export default function AdminHome() {
	return (
		<>
			<PageHeading
				eyebrow="CINENEST · ADMINISTRATION"
				title="Your cinema workspace"
			>
				Find bookings, review requests and manage venue configuration with your
				assigned permissions.
			</PageHeading>
			<div className="workspace-banner">
				<div>
					<Badge tone="info">Synthetic operations only</Badge>
					<h2>Start with your bookings</h2>
					<p>
						Open the existing venue-scoped booking list. Staff access is checked
						by the server; opening a tool does not grant permission.
					</p>
					<Link
						className="button button-primary"
						href="/admin/bookings"
						prefetch={false}
					>
						View bookings
					</Link>
				</div>
				<span className="banner-mark" aria-hidden="true">
					◉
				</span>
			</div>
			<div className="content-grid">
				<Card title="Rooms and packages">
					<p>
						Manage venue and room configuration, package drafts and indicative
						publications.
					</p>
					<div className="button-row">
						<Link href="/admin/rooms">Room configuration →</Link>
						<Link href="/admin/settings/packages">Package drafts →</Link>
					</div>
				</Card>
				<Card title="Review requests">
					<p>
						Review synthetic identity evidence and bank claims. Receipt review
						does not establish verified funds or booking confirmation.
					</p>
					<div className="button-row">
						<Link href="/admin/verification">Verification review →</Link>
						<Link href="/admin/bank">Bank review →</Link>
					</div>
				</Card>
				<Card title="Room operations">
					<p>
						Inspect synthetic turnover cases and simulator records. Physical
						readiness, admission and real equipment control remain unqualified.
					</p>
					<Link href="/admin/operations">Room operations →</Link>
				</Card>
				<Card title="Account and access">
					<p>
						Check your session. Venue permissions and independent
						financial-review restrictions still apply to each protected action.
					</p>
					<Link href="/admin/account">My account →</Link>
				</Card>
			</div>
			<Card title="Operational reporting">
				<p className="muted">
					Live dashboard metrics are not available. The existing bank report
					contains synthetic financial data only.
				</p>
				<Link href="/admin/reports/bank">Open synthetic bank report →</Link>
			</Card>
		</>
	);
}
