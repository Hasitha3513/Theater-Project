import { ManualAuthForm } from "../../components/manual-auth-form";
import { PageHeading } from "../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="CONFIGURED ACCOUNT ACCESS"
				title="Recover your account"
			>
				Request recovery without disclosing whether an email has an account. No
				password is changed by this request.
			</PageHeading>
			<ManualAuthForm mode="recovery" />
		</>
	);
}
