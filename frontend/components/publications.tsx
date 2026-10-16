"use client";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import type { Draft } from "../lib/packages";
import { displayMoney, packageRequest, stateFor } from "../lib/packages";
import { catalogItems } from "../lib/publications";
import { Button, Card, Field, StateMessage } from "./primitives";

type Snapshot = {
	name: string;
	description: string;
	basePriceMinor: string;
	includedMinutes: number;
	includedGuests: number;
	options: { label: string; mode: string; additionalChargeMinor: string }[];
};
type Publication = {
	publicationId: string;
	versionId: string;
	revision: number;
	versionNo: number;
	state: string;
	salesFrom: string;
	salesTo: string;
	sessionFrom: string;
	sessionTo: string;
	timezone: string;
	failureReason?: string;
	snapshot: Snapshot;
};
type Review = {
	contentHash: string;
	timezone: string;
	salesFrom: string;
	salesTo: string;
	sessionFrom: string;
	sessionTo: string;
	snapshot: Snapshot;
};
export function PublicationAdministration({ draft }: { draft: Draft }) {
	const base = `/api/admin/venues/${draft.venueId}/packages/${draft.packageId}`;
	const [open, setOpen] = useState(false),
		[rows, setRows] = useState<Publication[]>(),
		[actions, setActions] = useState<string[]>([]),
		[error, setError] = useState<unknown>(),
		[busy, setBusy] = useState(false),
		[review, setReview] = useState<Review>(),
		[request, setRequest] = useState<Record<string, unknown>>(),
		[message, setMessage] = useState("");
	async function load() {
		setError(undefined);
		try {
			const d = await packageRequest<{
				items: Publication[];
				permittedActions: string[];
			}>(`${base}/publications`);
			setRows(d.items);
			setActions(d.permittedActions);
		} catch (e) {
			setError(e);
		}
	}
	async function preview(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		setError(undefined);
		setBusy(true);
		setMessage("");
		try {
			const f = new FormData(event.currentTarget);
			const body: Record<string, unknown> = {
				expectedRevision: draft.revision,
				idempotencyKey: crypto.randomUUID(),
				reason: f.get("reason"),
			};
			for (const key of ["salesFrom", "salesTo", "sessionFrom", "sessionTo"])
				body[`${key}Local`] = f.get(`${key}Local`);
			const replacement = f.get("replacement");
			if (replacement) {
				body.replacesPublicationId = replacement;
				body.replacesRevision = rows?.find(
					(x) => x.publicationId === replacement,
				)?.revision;
			}
			const r = await packageRequest<Review>(`${base}/publication-preview`, {
				method: "POST",
				body: JSON.stringify(body),
			});
			setRequest({ ...body, contentHash: r.contentHash });
			setReview(r);
		} catch (e) {
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	async function publish() {
		if (!request) return;
		setBusy(true);
		setError(undefined);
		try {
			await packageRequest(`${base}/publications`, {
				method: "POST",
				body: JSON.stringify(request),
			});
			setMessage(
				"Publication recorded. Indicative only; purchasing is disabled.",
			);
			setReview(undefined);
			setRequest(undefined);
			await load();
		} catch (e) {
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	async function command(
		row: Publication,
		kind: "deactivate" | "retry",
		event: FormEvent<HTMLFormElement>,
	) {
		event.preventDefault();
		const form = event.currentTarget;
		const reason = new FormData(form).get("reason");
		setBusy(true);
		setError(undefined);
		try {
			await packageRequest(
				`${base}/publications/${row.publicationId}/${kind}`,
				{
					method: "POST",
					body: JSON.stringify({
						expectedRevision: row.revision,
						idempotencyKey: crypto.randomUUID(),
						reason,
					}),
				},
			);
			setMessage("Publication state updated; historical content retained.");
			await load();
		} catch (e) {
			setError(e);
		} finally {
			setBusy(false);
		}
	}
	return (
		<section>
			<Button
				variant="secondary"
				aria-expanded={open}
				onClick={() => {
					setOpen(!open);
					if (!open) void load();
				}}
			>
				Publications {draft.code}
			</Button>
			{open && (
				<Card title="Indicative publication and history">
					<p>
						Independent publisher required. Tax and checkout fees unresolved. No
						checkout, booking or availability is enabled.
					</p>
					{Boolean(error) && (
						<StateMessage kind={stateFor(error)}>
							<p>
								{error instanceof Error ? error.message : "Service unavailable"}
							</p>
							<Button onClick={() => void load()}>Reload publications</Button>
							<p>
								For a conflict, reload the draft and compare its current
								revision before reviewing again.
							</p>
						</StateMessage>
					)}
					{message && <p role="status">{message}</p>}
					{!rows && !error && <StateMessage kind="loading" />}
					{rows?.length === 0 && (
						<StateMessage kind="empty">
							No published or scheduled versions.
						</StateMessage>
					)}
					{actions.includes("package.publish") && (
						<form
							onSubmit={preview}
							onChange={() => {
								setReview(undefined);
								setRequest(undefined);
							}}
						>
							<p>
								Enter dates in the venue timezone. The review shows the
								authoritative timezone and resolved UTC boundaries before
								approval.
							</p>
							{["salesFrom", "salesTo", "sessionFrom", "sessionTo"].map((k) => (
								<Field
									key={k}
									id={`${draft.packageId}-${k}`}
									name={`${k}Local`}
									label={`${k} — venue local`}
									type="datetime-local"
									required
								/>
							))}
							<div className="field">
								<label htmlFor={`${draft.packageId}-replace`}>
									Explicit schedule replacement (optional)
								</label>
								<p>
									Replacement requires withdrawal authority and immediately
									withdraws the selected publication while preserving history.
								</p>
								<select id={`${draft.packageId}-replace`} name="replacement">
									<option value="">No replacement</option>
									{rows
										?.filter((x) => ["ACTIVE", "SCHEDULED"].includes(x.state))
										.map((x) => (
											<option key={x.publicationId} value={x.publicationId}>
												Version {x.versionNo} · {x.state}
											</option>
										))}
								</select>
							</div>
							<Field
								id={`${draft.packageId}-reason`}
								name="reason"
								label="Publication reason"
								required
								maxLength={500}
							/>
							<Button type="submit" busy={busy}>
								Review publication {draft.code}
							</Button>
						</form>
					)}
					{review && (
						<section aria-label="Publication review">
							<p>{review.timezone} · INDICATIVE ONLY</p>
							<p>
								{review.salesFrom} → {review.salesTo} (sales UTC)
							</p>
							<p>
								{review.sessionFrom} → {review.sessionTo} (sessions UTC)
							</p>
							<p>
								{review.snapshot.name} · LKR{" "}
								{displayMoney(review.snapshot.basePriceMinor)} base ·{" "}
								{review.snapshot.includedMinutes} minutes
							</p>
							<p>
								Approval binds the exact revision, content, scope, reason and
								dates.
							</p>
							<Button busy={busy} onClick={() => void publish()}>
								Approve reviewed publication {draft.code}
							</Button>
						</section>
					)}
					{rows?.map((row) => (
						<Card
							key={row.publicationId}
							title={`Version ${row.versionNo} · ${row.state}`}
						>
							<p>
								{row.snapshot.name} · LKR{" "}
								{displayMoney(row.snapshot.basePriceMinor)} ·{" "}
								{row.snapshot.includedMinutes} minutes
							</p>
							<p>
								{row.timezone} · sales {row.salesFrom}–{row.salesTo}
							</p>
							<p>
								Session validity {row.sessionFrom}–{row.sessionTo}
							</p>
							{row.failureReason && <p role="status">{row.failureReason}</p>}
							{((actions.includes("package.deactivate") &&
								row.state !== "INACTIVE") ||
								(actions.includes("package.publish") &&
									row.state === "BLOCKED" &&
									row.failureReason?.startsWith("ACTIVATION:"))) && (
								<form
									onSubmit={(e) =>
										void command(
											row,
											row.state === "BLOCKED" &&
												row.failureReason?.startsWith("ACTIVATION:") &&
												actions.includes("package.publish")
												? "retry"
												: "deactivate",
											e,
										)
									}
								>
									<Field
										id={`reason-${row.publicationId}`}
										name="reason"
										label="State-change reason"
										required
										maxLength={500}
									/>
									<Button type="submit" busy={busy}>
										{row.state === "BLOCKED" &&
										row.failureReason?.startsWith("ACTIVATION:") &&
										actions.includes("package.publish")
											? "Revalidate activation"
											: "Withdraw publication"}{" "}
										{row.versionNo}
									</Button>
								</form>
							)}
						</Card>
					))}
				</Card>
			)}
		</section>
	);
}
export function IndicativeCatalog({
	venueId,
	roomId,
}: {
	venueId: string;
	roomId: string;
}) {
	const [items, setItems] =
			useState<(Snapshot & { packageId: string; versionId: string })[]>(),
		[detail, setDetail] = useState<string>(),
		[error, setError] = useState<unknown>(),
		[retry, setRetry] = useState(() => new AbortController());
	useEffect(() => {
		const controller = retry;
		setItems(undefined);
		setError(undefined);
		packageRequest<{
			items: (Snapshot & { packageId: string; versionId: string })[];
		}>(`/api/venues/${venueId}/packages?roomId=${roomId}`, {
			signal: controller.signal,
		})
			.then((d) => {
				if (!controller.signal.aborted) setItems(catalogItems(d));
			})
			.catch((e) => {
				if (!controller.signal.aborted) setError(e);
			});
		return () => controller.abort();
	}, [venueId, roomId, retry]);
	async function inspect(packageId: string) {
		try {
			const d = await packageRequest<{
				items: (Snapshot & { versionId: string })[];
			}>(`/api/venues/${venueId}/packages/${packageId}?roomId=${roomId}`);
			setDetail(catalogItems(d)[0]?.versionId);
		} catch (e) {
			setError(e);
		}
	}
	return (
		<Card title="Indicative published packages">
			<p>
				Tax and checkout fees unresolved. Prices are not payable quotations.
				Purchasing and live availability are unavailable.
			</p>
			{error ? (
				<StateMessage kind={stateFor(error)}>
					<p>Package catalog unavailable.</p>
					<Button
						onClick={() =>
							setRetry((previous) => {
								previous.abort();
								return new AbortController();
							})
						}
					>
						Retry catalog
					</Button>
				</StateMessage>
			) : !items ? (
				<StateMessage kind="loading" />
			) : items.length === 0 ? (
				<StateMessage kind="empty">
					No eligible published package for this room.
				</StateMessage>
			) : (
				items.map((p) => (
					<Card key={p.versionId} title={p.name}>
						<p>
							LKR {displayMoney(p.basePriceMinor)} base · {p.includedMinutes}{" "}
							minutes · {p.includedGuests} included guests
						</p>
						<Button
							variant="secondary"
							onClick={() => void inspect(p.packageId)}
						>
							Package details {p.name}
						</Button>
						{detail === p.versionId && (
							<section>
								<p>{p.description}</p>
								{p.options.map((o) => (
									<p key={o.label}>
										{o.label} · {o.mode} ·{" "}
										{o.mode === "INCLUDED"
											? "No additional charge"
											: `Optional LKR ${displayMoney(o.additionalChargeMinor)} per session`}
									</p>
								))}
								<p>Indicative only · purchasable=false</p>
							</section>
						)}
					</Card>
				))
			)}
		</Card>
	);
}
