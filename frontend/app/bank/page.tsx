import { BankRequestDetail } from "../../components/bank-request-detail";
import { BankWorkspace } from "../../components/bank";
import { PageHeading } from "../../components/primitives";
export default async function Page({
	searchParams,
}: {
	searchParams: Promise<{
		quote?: string | string[];
		request?: string | string[];
		evidence?: string | string[];
	}>;
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
			{"evidence" in query ? (
				<BankWorkspace
					key={String(query.evidence)}
					evidenceRequest={
						typeof query.evidence === "string" &&
						!("quote" in query) &&
						!("request" in query)
							? query.evidence
							: ""
					}
				/>
			) : "request" in query ? (
				<BankRequestDetail
					key={String(query.request)}
					reference={
						typeof query.request === "string" && !query.quote
							? query.request
							: ""
					}
				/>
			) : (
				<BankWorkspace reviewer={false} initialQuoteId={quoteId} />
			)}
		</>
	);
}
