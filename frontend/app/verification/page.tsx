import { PageHeading } from "../../components/primitives";
import { VerificationWorkspace } from "../../components/verification";
export default async function Page({
	searchParams,
}: {
	searchParams: Promise<{ quote?: string | string[] }>;
}) {
	const query = await searchParams;
	const quoteId = typeof query.quote === "string" ? query.quote : "";
	return (
		<>
			<PageHeading
				eyebrow="Your cinema journey · Synthetic identity test"
				title="Identity verification"
			>
				Review your quotation, give explicit consent and submit only approved
				synthetic evidence. This test does not approve physical admission.
			</PageHeading>
			<VerificationWorkspace
				key={quoteId}
				reviewer={false}
				initialQuoteId={quoteId}
			/>
		</>
	);
}
