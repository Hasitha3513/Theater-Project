import { PageHeading } from "../../components/primitives";
import { SessionPanel } from "../../components/session-panel";
export default function AccountPage() {
	return (
		<>
			<PageHeading eyebrow="YOUR ACCOUNT" title="Account session">
				Only the minimum account representation supported by the current
				backend.
			</PageHeading>
			<SessionPanel />
		</>
	);
}
