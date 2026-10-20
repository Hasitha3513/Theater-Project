import Link from "next/link";
import { Card, PageHeading } from "../components/primitives";
export default function CustomerHome() {
	return (
		<>
			<div className="cinema-hero">
				<div>
					<PageHeading
						eyebrow="YOUR MOVIES. YOUR SPACE."
						title="A little more cinema."
					>
						Discover your room. Find a package. Plan your cinema experience.
					</PageHeading>
					<p>
						<strong>Development preview · Synthetic bookings only</strong>
					</p>
					<p>
						Explore configured cinema rooms and indicative packages, then try
						the existing synthetic booking journey. Catalog content depends on
						the configured venue and room.
					</p>
					<div className="button-row">
						<Link
							className="button button-primary"
							href="/rooms"
							prefetch={false}
							aria-describedby="discovery-entry-note"
						>
							Explore Cinema Rooms
						</Link>
						<Link
							className="button button-secondary"
							href="/rooms"
							prefetch={false}
							aria-describedby="discovery-entry-note"
						>
							View Available Packages
						</Link>
						<Link
							className="button button-secondary"
							href="/rooms"
							prefetch={false}
							aria-describedby="discovery-entry-note"
						>
							Start Booking
						</Link>
					</div>
					<p id="discovery-entry-note">
						All three paths start with room selection. Open Room details to view
						that room’s applicable packages and begin an inventory inquiry.
						Starting here does not reserve a room or confirm a booking.
					</p>
				</div>
				<div className="cinema-art" aria-hidden="true">
					<span>
						YOUR NEXT
						<br />
						GREAT ESCAPE.
					</span>
					<div className="cinema-seats">▰ ▰ ▰ ▰</div>
				</div>
			</div>
			<div className="content-grid">
				<Card title="1. Explore your room">
					<p>
						Browse listed room details, capacity and facilities. Room
						descriptions and operating windows do not establish live
						availability or physical readiness.
					</p>
				</Card>
				<Card title="2. Compare indicative packages">
					<p>
						Select a room to see applicable published packages. Displayed
						package prices are indicative; a current server quotation provides
						the synthetic itemized terms.
					</p>
				</Card>
				<Card title="3. Plan a synthetic booking">
					<p>
						Use the existing room inventory and quotation flow. Protected
						actions require an eligible account and server authorization.
						Nothing is submitted merely by following these links.
					</p>
				</Card>
				<Card title="Know the current limits">
					<p>
						Do not transfer real money. Real bank payments are not qualified;
						PayHere is deferred. Smart-lock and equipment operations are
						simulated, and production booking is not authorized.
					</p>
					<p>
						Loyalty, referrals and promotions are deferred for the initial
						release. No rewards or discounts are active here.
					</p>
				</Card>
			</div>
		</>
	);
}
