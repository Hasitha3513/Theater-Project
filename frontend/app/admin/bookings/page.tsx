import { StaffBookingList } from "../../../components/staff-bookings";
import { PageHeading } from "../../../components/primitives";
export default async function Page({
	searchParams,
}: {
	searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
	const values = await searchParams,
		q = new URLSearchParams();
	for (const [key, value] of Object.entries(values)) {
		if (Array.isArray(value)) for (const v of value) q.append(key, v);
		else if (value !== undefined) q.append(key, value);
	}
	return (
		<>
			<PageHeading
				eyebrow="Synthetic read-only administration"
				title="Staff booking administration"
			>
				Venue-scoped reads only. No commercial or physical acceptance.
			</PageHeading>
			<StaffBookingList key={q.toString()} query={q.toString()} />
		</>
	);
}
