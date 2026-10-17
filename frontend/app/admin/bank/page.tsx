import { BankWorkspace } from "../../../components/bank";
import { PageHeading } from "../../../components/primitives";
export default function Page() {
	return (
		<>
			<PageHeading
				eyebrow="Internal synthetic dry run"
				title="Bank receipt review"
			>
				No real payments or booking confirmation.
			</PageHeading>
			<BankWorkspace reviewer={true} />
		</>
	);
}
