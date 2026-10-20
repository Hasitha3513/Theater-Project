import { PageHeading } from "../../../../../../../components/primitives";
import { StaffReviewDetail } from "../../../../../../../components/staff-review-detail";
export default async function Page({
	params,
}: {
	params: Promise<{ venueId: string; id: string }>;
}) {
	const { venueId, id } = await params;
	return (
		<>
			<PageHeading
				eyebrow="Synthetic staff workspace"
				title="Bank review details"
			>
				Protected venue-scoped context. No decision, financial action or
				physical admission is implied.
			</PageHeading>
			<StaffReviewDetail
				key={`${venueId}:${id}`}
				kind="bank"
				venue={venueId}
				request={id}
			/>
		</>
	);
}
