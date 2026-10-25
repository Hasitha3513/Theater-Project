import { BankWorkspace } from "../../../components/bank";
import { BankFinance } from "../../../components/bank-finance";
import { BankInstructionPanel } from "../../../components/bank-instructions";
import { PageHeading } from "../../../components/primitives";
export default async function Page({
	searchParams,
}: {
	searchParams: Promise<{ venue?: string | string[] }>;
}) {
	const query = await searchParams;
	const venue =
		typeof query.venue === "string" && /^[0-9a-f-]{36}$/i.test(query.venue)
			? query.venue
			: "";
	return (
		<>
			<PageHeading
				eyebrow="Synthetic staff workspace"
				title="Bank receipt review"
			>
				No real payments or booking confirmation.
			</PageHeading>
			<BankWorkspace key={venue} reviewer={true} initialVenueId={venue} />
			<BankFinance />
			<BankInstructionPanel />
		</>
	);
}
