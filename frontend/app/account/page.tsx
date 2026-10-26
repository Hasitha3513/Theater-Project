import { PageHeading } from "../../components/primitives";
import { SessionPanel } from "../../components/session-panel";
export default function AccountPage() {
	return (
		<>
			<PageHeading eyebrow="YOUR ACCOUNT" title="Account session">
				Only the minimum account representation supported by the current
				backend.
			</PageHeading>
			<p>
				<a href="/login">Log in</a> ·{" "}
				<a href="/register">Create local test account</a>
			</p>
			<SessionPanel />
		</>
	);
}
