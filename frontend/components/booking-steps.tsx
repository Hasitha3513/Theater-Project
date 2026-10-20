import { Card } from "./primitives";
export const bookingSections = [
	["room-overview", "Room & packages"],
	["booking-date", "Date & time"],
	["booking-hold", "Availability & hold"],
	["booking-packages", "Package choices"],
	["booking-review", "Review quotation"],
	["booking-accept", "Explicit acceptance"],
] as const;
export function BookingStepGuide() {
	return (
		<nav className="booking-step-guide" aria-label="Booking section guide">
			<ol>
				{bookingSections.map(([id, label], index) => (
					<li key={id}>
						<a href={`#${id}`}>
							<span className="booking-step-number" aria-hidden="true">
								{index + 1}
							</span>
							<span>{label}</span>
						</a>
					</li>
				))}
			</ol>
			<p>
				Follow each section. Nothing is reserved or accepted until you choose
				its action.
			</p>
		</nav>
	);
}
export function AwaitingBookingQuote() {
	return (
		<Card title="Package choices and quotation">
			<section id="booking-packages">
				<h3>Choose a package</h3>
				<p>
					Create an active inventory hold first. Then explicitly load the
					packages applicable to that room and interval.
				</p>
			</section>
			<section id="booking-review">
				<h3>Review your quotation</h3>
				<p>
					A calculated quotation will show the server-priced line items,
					revision and expiry here.
				</p>
			</section>
			<section id="booking-accept">
				<h3>Accept this revision</h3>
				<p>
					Review the exact revision and disclosures before choosing acceptance.
					Acceptance does not confirm payment or a booking.
				</p>
			</section>
		</Card>
	);
}
