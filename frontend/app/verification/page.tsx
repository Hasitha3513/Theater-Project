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
				eyebrow="Synthetic development only"
				title="Booker verification test"
			>
				Private synthetic evidence; no physical admission approval.
			</PageHeading>
			<VerificationWorkspace
				key={quoteId}
				reviewer={false}
				initialQuoteId={quoteId}
			/>
		</>
	);
}
