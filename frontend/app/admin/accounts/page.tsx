import { ManagedGrantInspection } from "../../../components/managed-grant-inspection";
import { AdminReads } from "../../../components/admin-reads";
import { PageHeading } from "../../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="Internal synthetic administration"
				title="Accounts and audit"
			>
				Current venue-scoped read permissions are enforced by the backend.
			</PageHeading>
			<ManagedGrantInspection />
			<AdminReads />
		</>
	);
}
