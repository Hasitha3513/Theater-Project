import { AccessWorkspace } from "../../../components/access";
import { bookingAccessContext } from "../../../lib/staff-booking-access";
export default async function Page({
	searchParams,
}: {
	searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
	const query = await searchParams;
	let context: ReturnType<typeof bookingAccessContext>;
	try {
		context = bookingAccessContext(query);
	} catch {
		return <AccessWorkspace admin invalidContext />;
	}
	return <AccessWorkspace admin context={context} />;
}
