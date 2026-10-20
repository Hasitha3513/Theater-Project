import { StaffBookingDetail } from "../../../../../../components/staff-bookings";
import { PageHeading } from "../../../../../../components/primitives";
export default async function Page({
	params,
	searchParams,
}: {
	params: Promise<{ venueId: string; booking: string }>;
	searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
	const { venueId, booking } = await params,
		values = await searchParams,
		q = new URLSearchParams();
	for (const [key, value] of Object.entries(values)) {
		if (Array.isArray(value)) for (const v of value) q.append(key, v);
		else if (value !== undefined) q.append(key, value);
	}
	return (
		<>
			<PageHeading
				eyebrow="Synthetic read-only administration"
				title="Staff booking details"
			>
				Protected current booking context.
			</PageHeading>
			<StaffBookingDetail
				key={`${venueId}:${booking}:${q}`}
				venue={venueId}
				booking={booking}
				query={q.toString()}
			/>
		</>
	);
}
