import { PageHeading } from "../../components/primitives";
import { ManualAuthForm } from "../../components/manual-auth-form";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="LOCAL SYNTHETIC TEST ONLY"
				title="Log in to your local test account"
			>
				Real customer registration and provider sign-in are unavailable.
			</PageHeading>
			<ManualAuthForm mode="login" />
		</>
	);
}
