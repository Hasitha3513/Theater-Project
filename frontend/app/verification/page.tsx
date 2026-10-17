import { VerificationWorkspace } from "../../components/verification";
import { PageHeading } from "../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="Synthetic development only"
				title="Booker verification test"
			>
				Private synthetic evidence; no physical admission approval.
			</PageHeading>
			<VerificationWorkspace reviewer={false} />
		</>
	);
}
