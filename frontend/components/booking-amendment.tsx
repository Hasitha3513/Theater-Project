"use client";
import { useEffect, useRef, useState } from "react";
import { roomRequest } from "../lib/rooms";
import { readSession } from "../lib/session";
import { Badge, Button, Card, Field } from "./primitives";

type WindowFacts = {
	sessionStart: string;
	sessionEnd: string;
	timezone: string;
	packageVersionId: string;
	totalMinor: string | number;
	currency: string;
	[key: string]: unknown;
};
type Proposal = {
	id: string;
	bookingId: string;
	revision: number;
	state: string;
	proposalHash: string;
	expiresAt: string;
	original: WindowFacts;
	proposed: WindowFacts;
	differenceMinor: string;
	synthetic: true;
	realOperations: false;
	serverNow: string;
};
type Booking = {
	id: string;
	revision: number;
	latestAmendmentId?: string;
	amendmentPlanning?: {
		packageId: string;
		guests: number;
		optionalServiceIds: string[];
		paymentMethod: string;
	};
	purchase: {
		roomId: string;
		sessionStart: string;
		sessionEnd: string;
		timezone: string;
		packageVersionId: string;
	};
};
const uuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/;
export function amendmentRecord(value: unknown, booking: string): Proposal {
	const a = value as Proposal;
	if (
		a?.synthetic !== true ||
		a.realOperations !== false ||
		a.bookingId !== booking ||
		!uuid.test(a.id) ||
		!Number.isSafeInteger(a.revision) ||
		a.revision < 0 ||
		!["PROPOSED", "CONFIRMED", "EXPIRED", "WITHDRAWN"].includes(a.state) ||
		!/^([a-f0-9]{64})$/.test(a.proposalHash) ||
		a.differenceMinor !== "0" ||
		![
			a.expiresAt,
			a.serverNow,
			a.original?.sessionStart,
			a.original?.sessionEnd,
			a.proposed?.sessionStart,
			a.proposed?.sessionEnd,
		].every((v) => Number.isFinite(Date.parse(v)))
	)
		throw Error("Invalid amendment response; refresh your booking");
	for (const k of [
		"package",
		"packageVersionId",
		"guests",
		"optionalServiceIds",
		"lines",
		"totalMinor",
		"currency",
		"paymentMethod",
	])
		if (JSON.stringify(a.original[k]) !== JSON.stringify(a.proposed[k]))
			throw Error("Purchased terms differ; confirmation unavailable");
	return a;
}
function windowText(w: WindowFacts) {
	const f = new Intl.DateTimeFormat("en-GB", {
		timeZone: w.timezone,
		dateStyle: "medium",
		timeStyle: "short",
	});
	return `${f.format(new Date(w.sessionStart))} – ${f.format(new Date(w.sessionEnd))} (${w.timezone})`;
}
export function BookingAmendment({
	booking,
	onCommitted,
}: {
	booking: Booking;
	onCommitted: () => void;
}) {
	const [local, setLocal] = useState(""),
		[proposal, setProposal] = useState<Proposal | null>(null),
		[busy, setBusy] = useState(false),
		[error, setError] = useState(""),
		[accepted, setAccepted] = useState(false);
	const epoch = useRef(0),
		locked = useRef(false),
		workflow = useRef<{
			local: string;
			holdBody?: Record<string, unknown>;
			hold?: Record<string, unknown>;
			quoteBody?: Record<string, unknown>;
			quote?: Record<string, unknown>;
			body?: Record<string, unknown>;
		}>({ local: "" }),
		commands = useRef<Record<string, Record<string, unknown>>>({});
	useEffect(() => {
		const clear = () => {
			epoch.current++;
			setProposal(null);
			setAccepted(false);
			setError(
				"Session context changed. Refresh your booking before continuing.",
			);
			workflow.current = { local: "" };
			commands.current = {};
			setBusy(false);
			locked.current = false;
		};
		const hidden = () => {
			if (document.hidden) clear();
		};
		window.addEventListener("cinenest-session-change", clear);
		window.addEventListener("pagehide", clear);
		document.addEventListener("visibilitychange", hidden);
		return () => {
			epoch.current++;
			window.removeEventListener("cinenest-session-change", clear);
			window.removeEventListener("pagehide", clear);
			document.removeEventListener("visibilitychange", hidden);
		};
	}, []);
	const path = `/api/bookings/${booking.id}/amendments`;
	async function run(operation: (generation: number) => Promise<void>) {
		if (locked.current) return;
		locked.current = true;
		setBusy(true);
		setError("");
		const generation = epoch.current;
		try {
			if ((await readSession()).kind !== "authenticated")
				throw Error("Current sign-in required");
			if (generation !== epoch.current) return;
			await operation(generation);
		} catch (e) {
			if (generation === epoch.current)
				setError(
					e instanceof Error
						? e.message
						: "Amendment unavailable; refresh before retrying",
				);
		} finally {
			if (generation === epoch.current) {
				setBusy(false);
				locked.current = false;
			}
		}
	}
	const post = <T,>(url: string, body: Record<string, unknown>) =>
		roomRequest<T>(url, { method: "POST", body: JSON.stringify(body) });
	function refresh() {
		void run(async (g) => {
			const id = proposal?.id ?? booking.latestAmendmentId;
			if (!id || !uuid.test(id))
				throw Error("No recorded proposal. Prepare one below.");
			const a = amendmentRecord(await roomRequest(`${path}/${id}`), booking.id);
			if (g === epoch.current) {
				setProposal(a);
				setAccepted(false);
			}
		});
	}
	function prepare() {
		void run(async (g) => {
			const p = booking.amendmentPlanning;
			if (!p || booking.revision !== 1 || !local)
				throw Error(
					"A future target time and eligible original purchase are required",
				);
			const w = workflow.current;
			if (w.local && w.local !== local)
				throw Error(
					"Finish or withdraw the prior preparation before changing its target",
				);
			w.local = local;
			if (!w.holdBody) {
				const durationMinutes =
					(Date.parse(booking.purchase.sessionEnd) -
						Date.parse(booking.purchase.sessionStart)) /
					60000;
				const query = new URLSearchParams({
					sessionStartLocal: local,
					durationMinutes: String(durationMinutes),
					guests: String(p.guests),
				});
				const available = await roomRequest<{
					available: boolean;
					configuration: string;
				}>(`/api/rooms/${booking.purchase.roomId}/availability?${query}`);
				if (g !== epoch.current) return;
				if (!available.available)
					throw Error("Target unavailable; original booking is unchanged");
				w.holdBody = {
					roomId: booking.purchase.roomId,
					sessionStartLocal: local,
					durationMinutes,
					guests: p.guests,
					expectedConfiguration: available.configuration,
					idempotencyKey: crypto.randomUUID(),
				};
			}
			if (!w.hold) {
				w.hold = await post<Record<string, unknown>>("/api/holds", w.holdBody);
				if (g !== epoch.current) return;
			}
			if (!w.quoteBody)
				w.quoteBody = {
					holdId: w.hold.holdId,
					packageId: p.packageId,
					packageVersionId: booking.purchase.packageVersionId,
					expectedHoldRevision: w.hold.revision,
					optionalServiceIds: p.optionalServiceIds,
					paymentMethod: p.paymentMethod,
					idempotencyKey: crypto.randomUUID(),
				};
			if (!w.quote) {
				w.quote = await post<Record<string, unknown>>(
					"/api/quotes",
					w.quoteBody,
				);
				if (g !== epoch.current) return;
			}
			if (!w.body)
				w.body = {
					targetHoldId: w.hold.holdId,
					quoteId: w.quote.quoteId,
					quoteRevision: w.quote.revision,
					quoteHash: w.quote.contentHash,
					expectedBookingRevision: 1,
					expectedAmendmentRevision: 0,
					commandKey: crypto.randomUUID(),
					reason: "SYNTHETIC owner rescheduling request",
				};
			const a = amendmentRecord(await post(path, w.body), booking.id);
			if (g === epoch.current) {
				setProposal(a);
				setAccepted(false);
			}
		});
	}
	function resetPreparation() {
		void run(async (g) => {
			const w = workflow.current;
			if (w.holdBody && !w.hold)
				throw Error(
					"Retry preparation with its retained command key to recover the unknown hold result first",
				);
			if (w.hold) {
				commands.current.release ??= {
					expectedRevision: w.hold.revision,
					idempotencyKey: crypto.randomUUID(),
				};
				await post(
					`/api/holds/${w.hold.holdId}/release`,
					commands.current.release,
				);
			}
			if (g === epoch.current) {
				workflow.current = { local: "" };
				commands.current = {};
				setProposal(null);
				setLocal("");
				setAccepted(false);
			}
		});
	}

	function command(action: "confirm" | "withdrawals") {
		void run(async (g) => {
			if (proposal?.state !== "PROPOSED" || (action === "confirm" && !accepted))
				throw Error("Review and explicitly accept the exact proposal first");
			const k = `${proposal.id}:${proposal.revision}:${action}`;
			commands.current[k] ??= {
				expectedBookingRevision: booking.revision,
				expectedAmendmentRevision: proposal.revision,
				proposalHash: proposal.proposalHash,
				commandKey: crypto.randomUUID(),
				reason:
					action === "confirm"
						? "SYNTHETIC exact proposal acceptance"
						: "SYNTHETIC proposal withdrawal",
			};
			const a = amendmentRecord(
				await post(`${path}/${proposal.id}/${action}`, commands.current[k]),
				booking.id,
			);
			if (g === epoch.current) {
				setProposal(a);
				setAccepted(false);
				if (a.state === "CONFIRMED") onCommitted();
			}
		});
	}
	return (
		<Card title="Synthetic rescheduling">
			<p>
				SYNTHETIC TEST — NO REAL OPERATIONS. One same-room, same-duration change
				before either session starts and before any credential has ever been
				issued. Original inventory remains reserved until confirmation.
			</p>
			{error && (
				<p role="alert">
					{error} No success is implied. Refresh authoritative state before
					retrying.
				</p>
			)}
			<Button busy={busy} variant="secondary" onClick={refresh}>
				Refresh amendment
			</Button>
			{!proposal && booking.revision === 1 && (
				<>
					<p>
						No proposal loaded. Times use {booking.purchase.timezone}. Purchased
						selections and amounts remain unchanged.
					</p>
					<Field
						id="amendment-target"
						label="Replacement session start (venue local)"
						type="datetime-local"
						value={local}
						disabled={busy || Boolean(workflow.current.local)}
						onChange={(e) => setLocal(e.target.value)}
					/>
					<Button busy={busy} onClick={prepare}>
						Prepare synthetic proposal
					</Button>
				</>
			)}

			{!proposal && workflow.current.local && (
				<Button busy={busy} variant="secondary" onClick={resetPreparation}>
					Release preparation and choose another target
				</Button>
			)}
			{proposal && (
				<div aria-live="polite">
					<Badge>{proposal.state}</Badge>
					<p>Original: {windowText(proposal.original)}</p>
					<p>Proposed: {windowText(proposal.proposed)}</p>
					<p>
						Same purchased package version {proposal.proposed.packageVersionId}.
						Financial difference: LKR 0.00. Original money lines are retained.
					</p>
					<p>
						Server expiry:{" "}
						{new Intl.DateTimeFormat("en-GB", {
							timeZone: proposal.proposed.timezone,
							dateStyle: "medium",
							timeStyle: "short",
						}).format(new Date(proposal.expiresAt))}
						. Observed {proposal.serverNow}.
					</p>
					{proposal.state === "PROPOSED" && (
						<>
							<label>
								<input
									type="checkbox"
									checked={accepted}
									disabled={busy}
									onChange={(e) => setAccepted(e.target.checked)}
								/>{" "}
								I accept this exact replacement interval and unchanged purchased
								terms.
							</label>
							<div className="actions">
								<Button
									busy={busy}
									disabled={!accepted}
									onClick={() => command("confirm")}
								>
									Confirm synthetic reschedule
								</Button>
								<Button
									variant="secondary"
									busy={busy}
									onClick={() => command("withdrawals")}
								>
									Withdraw proposal
								</Button>
							</div>
						</>
					)}
					{proposal.state === "CONFIRMED" && (
						<p>
							Backend confirmed revision 2; revision 1 remains immutable.
							Delivery and physical entry are separate.
						</p>
					)}
					{["EXPIRED", "WITHDRAWN"].includes(proposal.state) && (
						<p>
							Original reservation retained. Replacement released. Refresh the
							booking to prepare another eligible proposal.
						</p>
					)}
				</div>
			)}

			{proposal &&
				["WITHDRAWN", "EXPIRED"].includes(proposal.state) &&
				booking.revision === 1 && (
					<Button
						busy={busy}
						variant="secondary"
						onClick={() => {
							workflow.current = { local: "" };
							commands.current = {};
							setProposal(null);
							setLocal("");
							setAccepted(false);
						}}
					>
						Prepare another target
					</Button>
				)}
			{booking.revision === 2 && !proposal && (
				<p>
					One reschedule already committed. Refresh amendment for retained
					history.
				</p>
			)}
			<p>
				<a href="/access">Protected Access workspace</a> ·{" "}
				<a href="/notifications">Protected Notifications workspace</a>
			</p>
		</Card>
	);
}
