import { PageHeading } from "../../../components/primitives";
import { SessionPanel } from "../../../components/session-panel";
export default function AdminAccountPage() {
	return (
		<>
			<PageHeading
				eyebrow="SERVER-AUTHORITATIVE SESSION"
				title="Account session"
			>
				Account access is separate from administration permissions.
			</PageHeading>
			<SessionPanel />
		</>
	);
}
