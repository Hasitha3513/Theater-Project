import { CustomerBookings } from "../../../components/customer-bookings";
export default async function Page({
	params,
}: {
	params: Promise<{ id: string }>;
}) {
	const { id } = await params;
	return <CustomerBookings bookingId={id} />;
}
