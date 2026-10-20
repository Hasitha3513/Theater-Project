// Display context only. A valid reference never establishes an applicable grant.
export function StaffVenueContext({ venue }: { venue: string }) {
	const valid =
		/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
			venue,
		);
	return (
		<aside className="staff-venue-context" aria-label="Selected staff venue">
			<p>
				<strong>Selected venue</strong> ·{" "}
				{valid ? venue.toLowerCase() : "Select an assigned venue reference"}
			</p>
			<p>
				Selection is not authorization. The server checks current permission and
				resource scope on every protected request.
			</p>
		</aside>
	);
}
