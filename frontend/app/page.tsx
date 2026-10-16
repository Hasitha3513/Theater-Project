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
						A shared foundation for your future CineNest experience.
					</PageHeading>
					<p>
						Development foundation · Booking, payment and room controls are not
						available.
					</p>
					<div className="button-row">
						<Link className="button button-primary" href="/account">
							View account session
						</Link>
						<Link className="button button-secondary" href="/admin">
							Explore the admin shell
						</Link>
					</div>
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
				<Card title="Your space, thoughtfully connected">
					<p>
						This is the customer layout foundation. Room discovery, reservations
						and payments will arrive through separately approved work.
					</p>
				</Card>
				<Card title="Clear about what is available">
					<p>
						The account session view can check the existing bounded backend when
						configured. Navigation previews do not grant access or establish
						administrator permissions.
					</p>
				</Card>
			</div>
		</>
	);
}
