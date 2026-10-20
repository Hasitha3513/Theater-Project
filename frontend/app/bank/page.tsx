import { BankWorkspace } from "../../components/bank";
import { PageHeading } from "../../components/primitives";
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
				eyebrow="Internal synthetic dry run"
				title="Synthetic bank request"
			>
				No real payments or booking confirmation.
			</PageHeading>
			<BankWorkspace reviewer={false} initialQuoteId={quoteId} />
		</>
	);
}
