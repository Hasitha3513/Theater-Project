import { ManualAuthForm } from "../../components/manual-auth-form";
import { PageHeading } from "../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="CONFIGURED ACCOUNT ACCESS"
				title="Reset your password"
			>
				Submit your private recovery token and a new password. A successful
				reset requires a fresh login.
			</PageHeading>
			<ManualAuthForm mode="reset" />
		</>
	);
}
