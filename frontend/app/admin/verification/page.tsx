import { VerificationWorkspace } from "../../../components/verification";
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
				title="Manual verification review"
			>
				Private synthetic evidence; no physical admission approval.
			</PageHeading>
			<VerificationWorkspace
				key={venue}
				reviewer={true}
				initialVenueId={venue}
			/>
		</>
	);
}
