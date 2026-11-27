import { ManualAuthForm } from "../../components/manual-auth-form";
import { PageHeading } from "../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="CONFIGURED ACCOUNT ACCESS"
				title="Verify your email"
			>
				Enter the private verification token manually. Opening this page never
				consumes a token.
			</PageHeading>
			<ManualAuthForm mode="verify" />
		</>
	);
}
