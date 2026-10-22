"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	grantPath,
	loadManagedGrants,
	type ManagedGrant,
	type ManagedGrantPage,
} from "../lib/managed-grants";
import { RoomRequestError } from "../lib/rooms";
import { Badge, Button, Card, Field, SelectField } from "./primitives";

function GrantFacts({ grant }: { grant: ManagedGrant }) {
	return (
		<dl className="grant-facts">
			<div>
				<dt>Stored state</dt>
				<dd>
					<Badge tone={grant.storedState === "ACTIVE" ? "info" : "neutral"}>
						{grant.storedState}
					</Badge>
				</dd>
			</div>
			<div>
				<dt>Validity (UTC)</dt>
				<dd>
					<time dateTime={grant.validFrom}>{grant.validFrom}</time>
					<br />
					to <time dateTime={grant.validUntil}>{grant.validUntil}</time>{" "}
					(exclusive)
				</dd>
			</div>
			<div>
				<dt>Revision</dt>
				<dd>{grant.revision}</dd>
			</div>
			<div>
				<dt>Effective at observation</dt>
				<dd>{grant.effectiveAtObservation ? "Yes" : "No"}</dd>
			</div>
			<div>
				<dt>Overlapping active intervals</dt>
				<dd>
					{grant.overlapsAnotherActiveGrant ? "Recorded" : "None reported"}
				</dd>
			</div>
			<div>
				<dt>Another effective grant</dt>
				<dd>
					{grant.anotherEffectiveGrant
						? "Yes — another path remains effective"
						: "None reported in managed subset"}
				</dd>
			</div>
		</dl>
	);
}
export function ManagedGrantInspection() {
	const [venue, setVenue] = useState(""),
		[account, setAccount] = useState(""),
		[limit, setLimit] = useState(20);
	const [page, setPage] = useState<ManagedGrantPage>(),
		[busy, setBusy] = useState(false);
	const [message, setMessage] = useState(
		"No grant data loaded. Enter exact references and load explicitly.",
	);
	const generation = useRef(0),
		controller = useRef<AbortController | undefined>(undefined),
		status = useRef<HTMLParagraphElement>(null);
	const clear = useCallback(() => {
		generation.current++;
		controller.current?.abort();
		setPage(undefined);
		setBusy(false);
	}, []);
	useEffect(() => {
		const hide = () => {
			clear();
			setMessage(
				"Protected grant data cleared. Load again to check current access.",
			);
		};
		const events = [
			"blur",
			"focus",
			"pagehide",
			"pageshow",
			"popstate",
			"cinenest-session-change",
		];
		for (const event of events) window.addEventListener(event, hide);
		document.addEventListener("visibilitychange", hide);
		return () => {
			generation.current++;
			controller.current?.abort();
			for (const event of events) window.removeEventListener(event, hide);
			document.removeEventListener("visibilitychange", hide);
		};
	}, [clear]);
	function change(update: () => void) {
		clear();
		update();
		setMessage("Inspection context changed. Load the first page explicitly.");
	}
	async function load(next = false) {
		const cursor = next && page?.hasNext ? page.nextCursor : null;
		clear();
		const token = generation.current;
		const context = { venue, account, limit };
		try {
			grantPath(context);
		} catch {
			setMessage("Enter canonical venue and account references.");
			status.current?.focus();
			return;
		}
		const c = new AbortController();
		controller.current = c;
		setBusy(true);
		setMessage("Checking current access and loading managed grants…");
		try {
			const result = await loadManagedGrants(
				context,
				cursor,
				AbortSignal.any([c.signal, AbortSignal.timeout(10000)]),
			);
			if (generation.current !== token) return;
			setPage(result);
			setMessage(
				result.items.length
					? "Managed subset loaded. Effectiveness is observed, not a complete authorization inventory."
					: "No managed grants recorded for this context. Other permissions may exist.",
			);
		} catch (error) {
			if (generation.current !== token) return;
			setPage(undefined);
			const code = error instanceof RoomRequestError ? error.status : 502;
			setMessage(
				code === 401
					? "Session unavailable. Sign in, then load again."
					: code === 403
						? "Access denied. Current identity.grant.read at this exact venue is required."
						: code === 404
							? "Inspection target unavailable or hidden. Check the references; the read feature may be disabled."
							: code === 409
								? "Inspection context changed. Results and page references cleared. Explicitly reload the first page."
								: code === 400
									? "Invalid request or page reference. Check the references and explicitly reload the first page."
									: "Inspection unavailable or response invalid. No grant data is shown. Retry explicitly.",
			);
			queueMicrotask(() => {
				if (generation.current === token) status.current?.focus();
			});
		} finally {
			if (generation.current === token) setBusy(false);
		}
	}
	return (
		<section
			className="staff-workspace managed-grant-inspection"
			aria-label="Managed Grant Inspection"
		>
			<Card title="Managed Grant Inspection">
				<Badge tone="info">Synthetic read-only inspection</Badge>
				<p>
					<strong>Managed grant subset only.</strong> Booking and guest read
					grants are not the account’s complete effective authorization
					inventory.
				</p>
				<p>
					Independent venue-scoped identity.grant.read is required. Inspection
					assignments remain unassigned; bootstrap and delegation remain
					disabled. This view cannot assign, approve or revoke grants.
				</p>
				<form
					onSubmit={(event) => {
						event.preventDefault();
						if (!busy) void load();
					}}
				>
					<div className="grant-filter-grid">
						<Field
							id="grant-venue"
							label="Venue reference"
							required
							value={venue}
							onChange={(e) => change(() => setVenue(e.target.value))}
						/>
						<Field
							id="grant-account"
							label="Account reference"
							required
							value={account}
							onChange={(e) => change(() => setAccount(e.target.value))}
						/>
						<SelectField
							id="grant-limit"
							label="Rows per page"
							value={limit}
							onChange={(e) => change(() => setLimit(Number(e.target.value)))}
						>
							<option value={20}>20</option>
							<option value={50}>50</option>
							<option value={100}>100</option>
						</SelectField>
					</div>
					<div className="button-row">
						<Button type="submit" busy={busy}>
							Load managed grants
						</Button>
						<Button
							variant="secondary"
							disabled={busy}
							onClick={() => void load()}
						>
							Reload first page
						</Button>
						<Button
							variant="secondary"
							disabled={busy || !page?.hasNext}
							onClick={() => void load(true)}
						>
							Next page
						</Button>
					</div>
				</form>
				<p
					role="status"
					aria-live="polite"
					aria-atomic="true"
					aria-busy={busy || undefined}
					ref={status}
					tabIndex={-1}
				>
					{message}
				</p>
				{page && (
					<section className="grant-result" aria-label="Managed grant results">
						<h3>Current observation</h3>
						<dl className="grant-context">
							<div>
								<dt>Venue</dt>
								<dd>{page.venueId}</dd>
							</div>
							<div>
								<dt>Account</dt>
								<dd>{page.accountId}</dd>
							</div>
							<div>
								<dt>Target eligibility</dt>
								<dd>{page.targetEligibility}</dd>
							</div>
							<div>
								<dt>Observed (UTC)</dt>
								<dd>
									<time dateTime={page.observedAt}>{page.observedAt}</time>
								</dd>
							</div>
						</dl>
						<p>
							Only this page is displayed.{" "}
							{page.hasNext
								? "More managed grants are available on the next page."
								: "No next page reported."}{" "}
							Overlap refers to stored ACTIVE validity intervals, not
							necessarily current effectiveness. A revoked row does not
							establish that all authority was removed.
						</p>
						{page.items.length > 0 && (
							<>
								<div className="grant-desktop table-scroll">
									<table>
										<caption>Managed booking and guest read grants</caption>
										<thead>
											<tr>
												<th scope="col">Managed permission / reference</th>
												<th scope="col">Recorded and observed details</th>
											</tr>
										</thead>
										<tbody>
											{page.items.map((g) => (
												<tr key={g.grantId}>
													<th scope="row">
														{g.permission}
														<small>{g.grantId}</small>
													</th>
													<td>
														<GrantFacts grant={g} />
													</td>
												</tr>
											))}
										</tbody>
									</table>
								</div>
								<div className="grant-mobile">
									{page.items.map((g) => (
										<article key={g.grantId}>
											<h4>{g.permission}</h4>
											<p>Grant reference: {g.grantId}</p>
											<GrantFacts grant={g} />
										</article>
									))}
								</div>
							</>
						)}
					</section>
				)}
			</Card>
		</section>
	);
}
