import { CustomerProfilePanel } from "../../components/customer-profile";
import { PageHeading } from "../../components/primitives";
import { SessionPanel } from "../../components/session-panel";
export default function AccountPage() {
	return (
		<>
			<PageHeading eyebrow="YOUR ACCOUNT" title="Account session">
				Your current session, own profile and protected booking history.
			</PageHeading>
			<p>
				<a href="/login">Log in</a> · <a href="/register">Create account</a> ·{" "}
				<a href="/recover-account">Recover account</a>
			</p>
			<SessionPanel />
			<CustomerProfilePanel />
		</>
	);
}
