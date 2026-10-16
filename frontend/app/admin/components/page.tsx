import { Examples } from "../../../components/examples";
import { PageHeading } from "../../../components/primitives";
export default function ComponentsPage() {
	return (
		<>
			<PageHeading eyebrow="SHARED FOUNDATION" title="UI examples">
				Reusable controls and states, with explicitly synthetic layout examples.
			</PageHeading>
			<Examples />
		</>
	);
}
