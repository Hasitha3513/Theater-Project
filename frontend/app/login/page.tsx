import { ManualAuthForm } from "../../components/manual-auth-form";
import { PageHeading } from "../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="CONFIGURED ACCOUNT ACCESS"
				title="Log in to your account"
			>
				Use your existing credentials. Provider sign-in remains separately
				controlled.
			</PageHeading>
			<ManualAuthForm mode="login" />
		</>
	);
}
