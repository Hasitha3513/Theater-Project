import { ManualAuthForm } from "../../components/manual-auth-form";
import { PageHeading } from "../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="CONFIGURED ACCOUNT ACCESS"
				title="Create an account"
			>
				Registration follows the configured account, privacy and delivery
				policy. Missing configuration keeps access disabled.
			</PageHeading>
			<ManualAuthForm mode="register" />
		</>
	);
}
