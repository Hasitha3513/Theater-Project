import { StaffReviewDetail } from "../../../../../../../components/staff-review-detail";
export default async function Page({
	params,
}: {
	params: Promise<{ venueId: string; id: string }>;
}) {
	const { venueId, id } = await params;
	return (
		<StaffReviewDetail
			key={`${venueId}:${id}`}
			kind="bank"
			venue={venueId}
			request={id}
		/>
	);
}
