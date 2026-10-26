import { PageHeading } from "../../components/primitives";
import { ManualAuthForm } from "../../components/manual-auth-form";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="LOCAL SYNTHETIC TEST ONLY"
				title="Create a local test account"
			>
				Real customer registration and provider sign-in are unavailable.
			</PageHeading>
			<ManualAuthForm mode="register" />
		</>
	);
}
