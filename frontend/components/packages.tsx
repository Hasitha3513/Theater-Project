"use client";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Button, Card, Field, PageHeading, StateMessage } from "./primitives";
import {
	packageRequest,
	RoomRequestError,
	stateFor,
	parseMoney,
	displayMoney,
} from "../lib/packages";
import type { Draft, Option, OptionCatalog, Preview } from "../lib/packages";
type PackagePage = {
	items: Draft[];
	total: number;
	page: number;
	size: number;
	permittedActions: string[];
};
export function PackageAdministration() {
	const [venue, setVenue] = useState("");
	const [query, setQuery] = useState("");
	const [page, setPage] = useState(0);
	const [reload, setReload] = useState(() => new AbortController());
	const [data, setData] = useState<PackagePage>();
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<unknown>();
	const [editor, setEditor] = useState<Draft | null | undefined>();
	const editorTrigger = useRef<HTMLElement | null>(null);
	const [catalog, setCatalog] = useState<OptionCatalog>();
	const [detail, setDetail] = useState<{ title: string; value: unknown }>();
	const base = `/api/admin/venues/${encodeURIComponent(venue)}/packages`;
	useEffect(() => {
		setData(undefined);
		setCatalog(undefined);
		setError(undefined);
		if (!venue) return;
		let active = true;
		setLoading(true);
		Promise.all([
			packageRequest<PackagePage>(
				`${base}?q=${encodeURIComponent(query)}&page=${page}&size=24`,
				{ signal: reload.signal },
			),
			packageRequest<OptionCatalog>(
				`/api/admin/venues/${encodeURIComponent(venue)}/package-options`,
				{ signal: reload.signal },
			),
		])
			.then(([rows, options]) => {
				if (active) {
					setData(rows);
					setCatalog(options);
				}
			})
			.catch((e) => {
				if (active) setError(e);
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [venue, base, query, page, reload]);
	async function inspect(d: Draft, kind: "preview" | "history") {
		if (kind === "history") {
			setDetail({ title: "Draft history", value: { packageId: d.packageId } });
			return;
		}
		try {
			const value = await packageRequest(
				`${base}/${d.packageId}/${kind}`,
				kind === "preview"
					? {
							method: "POST",
							body: JSON.stringify({ expectedRevision: d.revision }),
						}
					: undefined,
			);
			setDetail({
				title:
					kind === "preview" ? "Draft preview — not for sale" : "Draft history",
				value,
			});
		} catch (e) {
			setError(e);
		}
	}
	async function duplicate(d: Draft) {
		const code = window.prompt("New draft code (same venue)");
		if (!code) return;
		try {
			await packageRequest(`${base}/${d.packageId}/duplicate`, {
				method: "POST",
				body: JSON.stringify({
					code,
					expectedRevision: d.revision,
					reason: "Duplicate draft from package administration",
				}),
			});
			setReload(new AbortController());
		} catch (e) {
			setError(e);
		}
	}
	return (
		<>
			<PageHeading
				eyebrow="System Settings · Packages & Pricing"
				title="Package drafts"
			>
				Unpublished commercial configuration. Drafts cannot be sold or
				published. Taxes, checkout fees and final payable quotations remain
				unresolved.
			</PageHeading>
			<div className="filter-grid">
				<Field
					id="package-venue"
					label="Assigned venue ID"
					hint="Your server-side package grant is required; an entered ID does not grant access."
					value={venue}
					onChange={(e) => {
						setVenue(e.target.value);
						setPage(0);
					}}
				/>
				<Field
					id="package-search"
					label="Package name or code"
					value={query}
					onChange={(e) => {
						setQuery(e.target.value);
						setPage(0);
					}}
				/>
			</div>
			{loading && <StateMessage kind="loading" />}
			{error !== undefined && (
				<StateMessage kind={stateFor(error)}>
					<Button onClick={() => setReload(new AbortController())}>
						Retry
					</Button>
				</StateMessage>
			)}
			{data && catalog && (
				<>
					{catalog.constraintStatus !== "CONFIGURED" && (
						<p role="status">
							Approved minimum policy or compatible room configuration is
							missing. Validated save and preview are blocked.
						</p>
					)}
					{data.permittedActions.includes("package.edit") && (
						<Button
							onClick={(e) => {
								editorTrigger.current = e.currentTarget;
								setEditor(null);
							}}
						>
							Create draft
						</Button>
					)}
					<p>
						Publication, scheduling, deactivation and customer sales are
						unavailable.
					</p>
					{data.items.length === 0 ? (
						<p role="status">No matching package drafts.</p>
					) : (
						<div className="room-grid">
							{data.items.map((d) => (
								<Card key={d.packageId} title={d.name}>
									<p>
										{d.code} · DRAFT · revision {d.revision}
									</p>
									<p>
										LKR {displayMoney(d.basePriceMinor)} · {d.includedMinutes}{" "}
										customer minutes
									</p>
									<p>
										{d.includedGuests} included guests · maximum{" "}
										{d.maximumGuests}
									</p>
									<div className="toolbar">
										<Button
											variant="secondary"
											onClick={() => inspect(d, "preview")}
										>
											Preview {d.code}
										</Button>
										<Button
											variant="secondary"
											onClick={() => inspect(d, "history")}
										>
											History {d.code}
										</Button>
										{data.permittedActions.includes("package.edit") && (
											<>
												<Button
													onClick={async (event) => {
														try {
															editorTrigger.current = event.currentTarget;
															setEditor(
																await packageRequest<Draft>(
																	`${base}/${d.packageId}`,
																),
															);
														} catch (e) {
															setError(e);
														}
													}}
												>
													Edit {d.code}
												</Button>
												<Button
													variant="secondary"
													onClick={() => duplicate(d)}
												>
													Duplicate {d.code}
												</Button>
											</>
										)}
									</div>
								</Card>
							))}
						</div>
					)}
					<div className="toolbar">
						<Button disabled={page === 0} onClick={() => setPage(page - 1)}>
							Previous page
						</Button>
						<span role="status">
							{data.total} drafts · Page {page + 1}
						</span>
						<Button
							disabled={(page + 1) * data.size >= data.total}
							onClick={() => setPage(page + 1)}
						>
							Next page
						</Button>
					</div>
				</>
			)}
			{detail && (
				<DetailDialog title={detail.title} onClose={() => setDetail(undefined)}>
					{detail.title.startsWith("Draft preview") ? (
						<PreviewView value={detail.value as Preview} />
					) : (
						<HistoryView
							base={`${base}/${(detail.value as { packageId: string }).packageId}/history`}
						/>
					)}
				</DetailDialog>
			)}
			{editor !== undefined && catalog && (
				<DraftEditor
					key={editor?.packageId ?? "new"}
					trigger={editorTrigger.current}
					venue={venue}
					initial={editor ?? undefined}
					catalog={catalog}
					onClose={() => setEditor(undefined)}
					onSaved={() => {
						setEditor(undefined);
						setReload(new AbortController());
					}}
				/>
			)}
		</>
	);
}
function Modal({
	title,
	children,
	onClose,
	trigger,
}: {
	title: string;
	children: React.ReactNode;
	onClose: () => void;
	trigger?: HTMLElement | null;
}) {
	const ref = useRef<HTMLDialogElement>(null);
	useEffect(() => {
		const previous = trigger ?? (document.activeElement as HTMLElement);
		const modal = ref.current;
		modal?.showModal();
		return () => {
			modal?.close();
			queueMicrotask(() => {
				if (previous?.isConnected) previous.focus();
				else document.getElementById("main-content")?.focus();
			});
		};
	}, [trigger]);
	return (
		<dialog
			ref={ref}
			className="configuration-dialog"
			aria-labelledby="package-dialog-title"
			onCancel={(e) => {
				e.preventDefault();
				onClose();
			}}
			onKeyDown={(e) => {
				if (e.key !== "Tab") return;
				const fields = Array.from(
					e.currentTarget.querySelectorAll<HTMLElement>(
						"button:not([disabled]),input:not([disabled]),select,textarea",
					),
				);
				if (e.shiftKey && document.activeElement === fields[0]) {
					e.preventDefault();
					fields.at(-1)?.focus();
				} else if (!e.shiftKey && document.activeElement === fields.at(-1)) {
					e.preventDefault();
					fields[0]?.focus();
				}
			}}
		>
			<div className="drawer-heading">
				<h2 id="package-dialog-title">{title}</h2>
				<Button variant="secondary" onClick={onClose}>
					Close dialog
				</Button>
			</div>
			{children}
		</dialog>
	);
}
function DetailDialog({
	title,
	children,
	onClose,
}: {
	title: string;
	children: React.ReactNode;
	onClose: () => void;
}) {
	return (
		<Modal title={title} onClose={onClose}>
			{children}
		</Modal>
	);
}
export function PreviewView({ value }: { value: Preview }) {
	return (
		<section>
			<h3>{value.label}</h3>
			<p>
				Known package components: LKR{" "}
				{displayMoney(value.knownPackageComponentsMinor)}
			</p>
			<p>
				Tax: {value.taxStatus} · Checkout fees: {value.feeStatus}
			</p>
			<p>
				This is not a total payable amount. Optional services are not selected
				in the illustrated components.
			</p>
			{value.options.map((o) => (
				<p key={o.serviceId}>
					{o.label}: {o.mode} ·{" "}
					{o.mode === "INCLUDED"
						? "No additional charge"
						: `Optional LKR ${displayMoney(o.additionalChargeMinor)} per session`}
				</p>
			))}
		</section>
	);
}
function DraftEditor({
	venue,
	initial,
	catalog,
	onClose,
	onSaved,
	trigger,
}: {
	venue: string;
	initial?: Draft;
	catalog: OptionCatalog;
	onClose: () => void;
	onSaved: () => void;
	trigger?: HTMLElement | null;
}) {
	const [error, setError] = useState<unknown>();
	const [busy, setBusy] = useState(false);
	const [dirty, setDirty] = useState(false);
	const [revision, setRevision] = useState(initial?.revision);
	const [comparison, setComparison] = useState<Draft>();
	const [preview, setPreview] = useState<Preview>();
	const [extensions, setExtensions] = useState(
		initial?.extensionsEnabled ?? false,
	);
	const [wide, setWide] = useState(initial?.venueWide ?? false);
	const [selected, setSelected] = useState<string[]>(initial?.roomIds ?? []);
	const [options, setOptions] = useState<Option[]>(initial?.options ?? []);
	const base = `/api/admin/venues/${encodeURIComponent(venue)}/packages`;
	const close = () => {
		if (!busy && (!dirty || window.confirm("Discard unsaved draft changes?")))
			onClose();
	};
	async function submit(e: FormEvent<HTMLFormElement>) {
		e.preventDefault();
		setBusy(true);
		setError(undefined);
		const f = new FormData(e.currentTarget);
		try {
			const body = {
				code: f.get("code"),
				name: f.get("name"),
				description: f.get("description"),
				currency: "LKR",
				basePriceMinor: parseMoney(String(f.get("base"))),
				includedMinutes: Number(f.get("included")),
				minimumMinutes: Number(f.get("minimum")),
				maximumMinutes: Number(f.get("maximum")),
				extensionsEnabled: extensions,
				extensionStepMinutes: extensions ? Number(f.get("step")) : null,
				extensionPriceMinor: extensions
					? parseMoney(String(f.get("extensionPrice")))
					: null,
				includedGuests: Number(f.get("guests")),
				maximumGuests: Number(f.get("maxGuests")),
				extraGuestPriceMinor: parseMoney(String(f.get("guestPrice"))),
				venueWide: wide,
				roomIds: wide ? [] : selected,
				options,
				reason: f.get("reason"),
				...(initial ? { expectedRevision: revision } : {}),
			};
			await packageRequest(
				initial ? `${base}/${initial.packageId}/draft` : base,
				{ method: initial ? "PUT" : "POST", body: JSON.stringify(body) },
			);
			onSaved();
		} catch (value) {
			setError(value);
		} finally {
			setBusy(false);
		}
	}
	return (
		<Modal
			trigger={trigger}
			title={initial ? "Edit package draft" : "Create package draft"}
			onClose={close}
		>
			<p>
				DRAFT ONLY. No publication, booking, live availability or media rights
				are established.
			</p>
			<p>
				Effective venue minimum:{" "}
				{catalog.constraints?.minimumMinutes ?? "Missing"} minutes · policy
				revision {catalog.constraints?.policyRevision ?? "unavailable"}. Package
				controls cannot change it.
			</p>
			<form onSubmit={submit} onChange={() => setDirty(true)}>
				<div className="filter-grid">
					<Field
						id="draft-code"
						name="code"
						label="Package code"
						defaultValue={initial?.code ?? ""}
						required
						maxLength={64}
					/>
					<Field
						id="draft-name"
						name="name"
						label="Package name"
						defaultValue={initial?.name ?? ""}
						required
						maxLength={200}
					/>
					<Field
						id="draft-description"
						name="description"
						label="Neutral description and inclusions"
						defaultValue={initial?.description ?? ""}
						maxLength={2000}
					/>
					<Field
						id="draft-base"
						name="base"
						label="Base price (LKR)"
						inputMode="decimal"
						defaultValue={initial ? displayMoney(initial.basePriceMinor) : ""}
						required
						hint="Exact amount, at most two decimal places. No rounding."
					/>
					<Field
						id="draft-included"
						name="included"
						label="Included customer minutes"
						type="number"
						min="1"
						defaultValue={initial?.includedMinutes ?? ""}
						required
					/>
					<Field
						id="draft-minimum"
						name="minimum"
						label="Minimum selectable minutes"
						type="number"
						min="1"
						defaultValue={initial?.minimumMinutes ?? ""}
						required
					/>
					<Field
						id="draft-maximum"
						name="maximum"
						label="Maximum selectable minutes"
						type="number"
						min="1"
						defaultValue={initial?.maximumMinutes ?? ""}
						required
					/>
					<label>
						<input
							type="checkbox"
							checked={extensions}
							onChange={(e) => {
								setExtensions(e.target.checked);
								setDirty(true);
							}}
						/>
						Enable whole-unit extensions
					</label>
					{extensions && (
						<>
							<Field
								id="draft-step"
								name="step"
								label="Extension unit (minutes)"
								type="number"
								min="1"
								defaultValue={initial?.extensionStepMinutes ?? ""}
								required
							/>
							<Field
								id="draft-extension-price"
								name="extensionPrice"
								label="Price per extension unit (LKR)"
								inputMode="decimal"
								defaultValue={
									initial?.extensionPriceMinor
										? displayMoney(initial.extensionPriceMinor)
										: ""
								}
								required
							/>
						</>
					)}
					<Field
						id="draft-guests"
						name="guests"
						label="Included guests"
						type="number"
						min="1"
						defaultValue={initial?.includedGuests ?? ""}
						required
					/>
					<Field
						id="draft-max-guests"
						name="maxGuests"
						label="Maximum guests"
						type="number"
						min="1"
						defaultValue={initial?.maximumGuests ?? ""}
						required
					/>
					<Field
						id="draft-guest-price"
						name="guestPrice"
						label="Additional guest charge (LKR)"
						inputMode="decimal"
						defaultValue={
							initial ? displayMoney(initial.extraGuestPriceMinor) : ""
						}
						required
					/>
					<Field
						id="draft-reason"
						name="reason"
						label="Business reason for this change"
						required
						maxLength={500}
						hint="Do not include personal data, credentials or private evidence."
					/>
				</div>
				<fieldset>
					<legend>Room applicability</legend>
					<label>
						<input
							type="checkbox"
							checked={wide}
							onChange={(e) => {
								setWide(e.target.checked);
								setDirty(true);
							}}
						/>
						All configured rooms in this venue
					</label>
					{!wide &&
						catalog.constraints?.rooms.map((r) => (
							<label className="window-row" key={r.roomId}>
								<input
									type="checkbox"
									checked={selected.includes(r.roomId)}
									onChange={(e) => {
										setDirty(true);
										setSelected(
											e.target.checked
												? [...selected, r.roomId]
												: selected.filter((id) => id !== r.roomId),
										);
									}}
								/>
								Room {r.roomId} · capacity {r.capacity}
							</label>
						))}
					<p>Configuration compatibility only; not bookable availability.</p>
				</fieldset>
				<fieldset>
					<legend>Shared services and add-ons</legend>
					{catalog.services.length === 0 ? (
						<p>No approved service definitions; no selectable extras.</p>
					) : (
						catalog.services.map((s) => (
							<div className="window-row" key={s.rateVersionId}>
								<label htmlFor={`option-${s.rateVersionId}`}>
									{s.label} · revision {s.revision} · LKR{" "}
									{displayMoney(s.unitPriceMinor)} per session
								</label>
								<select
									id={`option-${s.rateVersionId}`}
									value={
										options.find((o) => o.rateVersionId === s.rateVersionId)
											?.mode ?? ""
									}
									onChange={(e) => {
										setDirty(true);
										const rest = options.filter(
											(o) => o.serviceId !== s.serviceId,
										);
										setOptions(
											e.target.value
												? [
														...rest,
														{
															serviceId: s.serviceId,
															rateVersionId: s.rateVersionId,
															mode: e.target.value as Option["mode"],
														},
													]
												: rest,
										);
									}}
								>
									<option value="">Not offered</option>
									<option value="INCLUDED">
										Included (no additional charge)
									</option>
									<option value="OPTIONAL">Optional (shared rate)</option>
								</select>
							</div>
						))
					)}
				</fieldset>
				{error !== undefined && (
					<section className="state" role="alert">
						<h3>
							{error instanceof RoomRequestError && error.status === 409
								? "Draft conflict"
								: "Draft save failed"}
						</h3>
						<p>
							{error instanceof Error ? error.message : "Request failed"} Your
							draft inputs are retained.
						</p>
						{initial &&
							error instanceof RoomRequestError &&
							error.status === 409 && (
								<Button
									variant="secondary"
									onClick={async () => {
										try {
											setComparison(
												await packageRequest<Draft>(
													`${base}/${initial.packageId}`,
												),
											);
										} catch (e) {
											setError(e);
										}
									}}
								>
									Load latest for comparison
								</Button>
							)}
					</section>
				)}
				{comparison && (
					<section>
						<h3>Latest revision {comparison.revision}</h3>
						<pre>{JSON.stringify(comparison, null, 2)}</pre>
						<Button
							onClick={() => {
								setRevision(comparison.revision);
								setComparison(undefined);
								setError(undefined);
							}}
						>
							Use latest revision with my reviewed draft
						</Button>
					</section>
				)}
				<div className="toolbar">
					<Button
						type="submit"
						busy={busy}
						disabled={catalog.constraintStatus !== "CONFIGURED"}
					>
						Save unpublished draft
					</Button>
					<Button variant="secondary" disabled={busy} onClick={close}>
						Cancel
					</Button>
					{initial && (
						<Button
							variant="secondary"
							disabled={busy}
							onClick={async () => {
								try {
									setPreview(
										await packageRequest<Preview>(
											`${base}/${initial.packageId}/preview`,
											{
												method: "POST",
												body: JSON.stringify({ expectedRevision: revision }),
											},
										),
									);
								} catch (e) {
									setError(e);
								}
							}}
						>
							Preview saved revision
						</Button>
					)}
				</div>
				{preview && (
					<>
						<p>Saved revision preview; unsaved form changes are excluded.</p>
						<PreviewView value={preview} />
					</>
				)}
			</form>
		</Modal>
	);
}

function HistoryView({ base }: { base: string }) {
	const [page, setPage] = useState(0);
	const [data, setData] = useState<{
		items: { revision: number; snapshot: Draft; occurredAt: string }[];
		total: number;
		size: number;
	}>();
	const [error, setError] = useState<unknown>();
	const [reload, setReload] = useState(() => new AbortController());
	useEffect(() => {
		let active = true;
		setData(undefined);
		setError(undefined);
		packageRequest<{
			items: { revision: number; snapshot: Draft; occurredAt: string }[];
			total: number;
			size: number;
		}>(`${base}?page=${page}&size=20`, { signal: reload.signal })
			.then((v) => {
				if (active) setData(v);
			})
			.catch((e) => {
				if (active) setError(e);
			});
		return () => {
			active = false;
		};
	}, [base, page, reload]);
	return (
		<>
			{error ? (
				<StateMessage kind={stateFor(error)}>
					<Button onClick={() => setReload(new AbortController())}>
						Retry history
					</Button>
				</StateMessage>
			) : !data ? (
				<StateMessage kind="loading" />
			) : (
				<>
					{data.items.map((h) => (
						<Card key={h.revision} title={`Revision ${h.revision}`}>
							<p>
								{h.snapshot.name} · {h.occurredAt}
							</p>
							<p>
								LKR {displayMoney(h.snapshot.basePriceMinor)} ·{" "}
								{h.snapshot.includedMinutes} minutes ·{" "}
								{h.snapshot.includedGuests} included guests
							</p>
							<p>{h.snapshot.description}</p>
							<details>
								<summary>Saved configuration references</summary>
								<pre>{JSON.stringify(h.snapshot, null, 2)}</pre>
							</details>
						</Card>
					))}
					<div className="toolbar">
						<Button disabled={page === 0} onClick={() => setPage(page - 1)}>
							Previous history page
						</Button>
						<span>
							{data.total} saved revisions · Page {page + 1}
						</span>
						<Button
							disabled={(page + 1) * data.size >= data.total}
							onClick={() => setPage(page + 1)}
						>
							Next history page
						</Button>
					</div>
				</>
			)}
		</>
	);
}
