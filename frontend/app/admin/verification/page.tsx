import { VerificationWorkspace } from "../../../components/verification";
import { PageHeading } from "../../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="Synthetic development only"
				title="Manual verification review"
			>
				Private synthetic evidence; no physical admission approval.
			</PageHeading>
			<VerificationWorkspace reviewer={true} />
		</>
	);
}
