"use client";
import Image from "next/image";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { cinemaAssets } from "./cinema-imagery";
import { Button, Card, Field, PageHeading } from "./primitives";

type Content = {
	title: string;
	description: string;
	altText: string;
	assetId: string;
	assetVersion: number;
	startsAt: string;
	endsAt: string;
	displayOrder: number;
	linkTarget: string;
};
type Banner = {
	bannerId: string;
	resourceRevision: number;
	contentRevision: number;
	reviewHash: string;
	status: string;
	publishedRevision: number | null;
	content: Content;
};
type PublicBanner = {
	bannerId: string;
	title: string;
	description: string;
	altText: string;
	image: { id: string };
	linkTarget: string;
	visibleForMillis: number;
};
const blank = (): Content => ({
	title: "",
	description: "",
	altText: "Cinema concept illustration",
	assetId: "hero",
	assetVersion: 1,
	startsAt: new Date().toISOString(),
	endsAt: new Date(Date.now() + 86400000).toISOString(),
	displayOrder: 0,
	linkTarget: "NONE",
});
function Illustration({ asset, alt }: { asset: string; alt: string }) {
	if (!Object.hasOwn(cinemaAssets, asset)) return null;
	return (
		<figure className="cinema-illustration">
			<Image
				unoptimized
				src={`/cinema/${asset}-960.webp`}
				width={960}
				height={540}
				alt={alt}
			/>
			<figcaption>
				AI-generated illustration · Not a photograph of this venue or room.
			</figcaption>
		</figure>
	);
}
export function PublishedBanners() {
	const [rows, setRows] = useState<PublicBanner[]>([]);
	const serial = useRef(0);
	useEffect(() => {
		let active = true;
		let timer: ReturnType<typeof setTimeout>;
		let expiry: ReturnType<typeof setTimeout>;
		const load = async () => {
			const n = ++serial.current;
			clearTimeout(timer);
			clearTimeout(expiry);
			setRows([]);
			try {
				const sentAt = performance.now();
				const response = await fetch("/api/catalog/banners", {
					cache: "no-store",
				});
				if (!response.ok) throw Error();
				const data: PublicBanner[] = await response.json();
				if (!active || n !== serial.current) return;
				if (
					!Array.isArray(data) ||
					data.length > 100 ||
					data.some(
						(b) =>
							!Object.hasOwn(cinemaAssets, b.image?.id) ||
							!Number.isFinite(b.visibleForMillis),
					)
				)
					throw Error();
				setRows(data);
				const life = Math.max(
					0,
					Math.min(
						15000,
						...data.map(
							(b) => b.visibleForMillis - (performance.now() - sentAt),
						),
					),
				);
				expiry = setTimeout(() => setRows([]), life);
				timer = setTimeout(() => void load(), Math.max(250, life));
			} catch {
				if (active && n === serial.current) {
					setRows([]);
					timer = setTimeout(() => void load(), 15000);
				}
			}
		};
		const hidden = () => {
			if (document.hidden) {
				serial.current++;
				setRows([]);
				clearTimeout(timer);
				clearTimeout(expiry);
			} else void load();
		};
		void load();
		window.addEventListener("focus", load);
		document.addEventListener("visibilitychange", hidden);
		return () => {
			active = false;
			serial.current++;
			clearTimeout(timer);
			clearTimeout(expiry);
			window.removeEventListener("focus", load);
			document.removeEventListener("visibilitychange", hidden);
		};
	}, []);
	if (!rows.length) return null;
	return (
		<section aria-label="Venue advertisements" className="content-grid">
			{rows.map((b) => (
				<Card key={b.bannerId} title={b.title}>
					<Illustration asset={b.image.id} alt={b.altText} />
					<p>{b.description}</p>
					{b.linkTarget === "ROOMS" && (
						<Link href="/rooms">Explore rooms →</Link>
					)}
				</Card>
			))}
		</section>
	);
}
export function BannerAdmin() {
	const [venue, setVenue] = useState("");
	const [venues, setVenues] = useState<
		{ venueId: string; displayName: string; timezone: string }[]
	>([]);
	const [rows, setRows] = useState<Banner[]>([]);
	const [selected, setSelected] = useState<Banner | null>(null);
	const [content, setContent] = useState<Content>(blank);
	const [notice, setNotice] = useState("");
	const [busy, setBusy] = useState(false);
	const [preview, setPreview] = useState<Banner | null>(null);
	const [history, setHistory] = useState<Banner[]>([]);
	const generation = useRef(0);
	const pending = useRef<{ payload: string; key: string } | null>(null);
	const root = `/api/admin/venues/${venue}/banners`;
	const clear = useCallback(() => {
		generation.current++;
		setRows([]);
		setSelected(null);
		setPreview(null);
		setHistory([]);
		setContent(blank());
		setBusy(false);
		pending.current = null;
	}, []);
	useEffect(() => {
		fetch("/api/catalog/venues", { cache: "no-store" })
			.then((r) => (r.ok ? r.json() : []))
			.then(setVenues)
			.catch(() => setVenues([]));
		const invalidate = () => {
			clear();
			setNotice(
				"Protected content cleared. Reload to check current authority.",
			);
		};
		window.addEventListener("cinenest-session-change", invalidate);
		window.addEventListener("pagehide", invalidate);
		window.addEventListener("focus", invalidate);
		return () => {
			generation.current++;
			window.removeEventListener("cinenest-session-change", invalidate);
			window.removeEventListener("pagehide", invalidate);
			window.removeEventListener("focus", invalidate);
		};
	}, [clear]);
	async function api(path: string, method = "GET", body?: object) {
		const headers: Record<string, string> = {};
		if (method !== "GET") {
			const r = await fetch("/api/session/csrf", { cache: "no-store" });
			if (!r.ok) throw Error("Sign in with current content authority.");
			const c = await r.json();
			headers["X-CSRF-TOKEN"] = c.token;
			headers["Content-Type"] = "application/json";
		}
		const r = await fetch(path, {
			method,
			headers,
			body: body ? JSON.stringify(body) : undefined,
			cache: "no-store",
		});
		if (r.status === 401 || r.status === 403) {
			clear();
			setNotice(
				"Denied. Exact-venue authority and an independent publisher are required.",
			);
			throw Error("Access denied");
		}
		if (!r.ok)
			throw Error(
				r.status === 409
					? "Revision or command conflict. Reload and compare; your edits are retained."
					: r.status === 403
						? "Denied. Exact-venue authority and an independent publisher are required."
						: "Advertising unavailable, invalid or denied. Local feature must be enabled.",
			);
		return r.json();
	}
	async function load() {
		const n = ++generation.current;
		setBusy(true);
		setRows([]);
		setSelected(null);
		setPreview(null);
		setHistory([]);
		try {
			const data = await api(root);
			if (n === generation.current) {
				setRows(data);
				setNotice("Current protected list loaded.");
			}
		} catch (e) {
			if (n === generation.current) setNotice((e as Error).message);
		} finally {
			if (n === generation.current) setBusy(false);
		}
	}
	async function inspect(b: Banner) {
		const n = ++generation.current;
		setBusy(true);
		setPreview(null);
		setHistory([]);
		try {
			const data = await api(`${root}/${b.bannerId}`);
			if (n === generation.current) {
				setSelected(data);
				setContent(data.content);
				pending.current = null;
				setNotice("Draft loaded. Review exact revision before publication.");
			}
		} catch (e) {
			if (n === generation.current) {
				clear();
				setNotice((e as Error).message);
			}
		} finally {
			if (n === generation.current) setBusy(false);
		}
	}
	async function command(action: "save" | "publish" | "unpublish") {
		const n = generation.current;
		setBusy(true);
		setNotice("");
		try {
			let path = root;
			let method = "POST";
			let body: Record<string, unknown> = { content };
			if (selected) {
				path += `/${selected.bannerId}`;
				body.expectedRevision = selected.resourceRevision;
				method = "PUT";
			}
			if (action !== "save") {
				if (!selected) throw Error("Select and preview a current draft.");
				path += action === "publish" ? "/publications" : "/unpublish";
				method = "POST";
				body = { expectedRevision: selected.resourceRevision };
				if (action === "publish") {
					if (
						!preview ||
						preview.resourceRevision !== selected.resourceRevision
					)
						throw Error("Preview the current saved revision first.");
					body.contentRevision = preview.contentRevision;
					body.reviewHash = preview.reviewHash;
				}
			}
			const payload = JSON.stringify({ path, method, body });
			if (pending.current?.payload !== payload)
				pending.current = { payload, key: crypto.randomUUID() };
			body.commandId = pending.current.key;
			const result = await api(path, method, body);
			if (n !== generation.current) return;
			const data = await api(`${root}/${result.bannerId}`);
			if (n !== generation.current) return;
			setSelected(data);
			setContent(data.content);
			setPreview(null);
			const updatedRows = await api(root);
			if (n !== generation.current) return;
			setRows(updatedRows);
			pending.current = null;
			setNotice(
				action === "save"
					? "Draft saved. Homepage publication is unchanged."
					: action === "publish"
						? "Publication recorded. Server schedule controls visibility."
						: "Banner unpublished.",
			);
		} catch (e) {
			if (n === generation.current) setNotice((e as Error).message);
		} finally {
			if (n === generation.current) setBusy(false);
		}
	}
	async function show(kind: "preview" | "revisions") {
		if (!selected) return;
		const n = generation.current;
		setBusy(true);
		try {
			const data = await api(`${root}/${selected.bannerId}/${kind}`);
			if (n !== generation.current) return;
			if (kind === "preview") {
				setPreview(data);
				setSelected(data);
			} else setHistory(data);
		} catch (e) {
			if (n === generation.current) setNotice((e as Error).message);
		} finally {
			if (n === generation.current) setBusy(false);
		}
	}
	const update = (key: keyof Content, value: string | number) => {
		setContent((c) => ({ ...c, [key]: value }));
		setPreview(null);
	};
	const timezone = venues.find((v) => v.venueId === venue)?.timezone ?? "UTC";
	return (
		<>
			<PageHeading eyebrow="LOCAL SYNTHETIC ONLY" title="Venue advertising">
				Independent content publication; no discounts, booking or payment
				changes.
			</PageHeading>
			<Card title="Select venue">
				<label htmlFor="banner-venue">Venue</label>
				<select
					id="banner-venue"
					value={venue}
					onChange={(e) => {
						clear();
						setVenue(e.target.value);
						setNotice("");
					}}
				>
					<option value="">Select a published venue</option>
					{venues.map((v) => (
						<option key={v.venueId} value={v.venueId}>
							{v.displayName}
						</option>
					))}
				</select>
				<Button disabled={!venue || busy} onClick={load}>
					Load banners
				</Button>
				<p role="status" aria-live="polite">
					{notice}
				</p>
			</Card>
			{venue && (
				<>
					<Card title="Banner list">
						<p>Maximum 100 current entries. Times are server authoritative.</p>
						<div className="button-row">
							<Button
								disabled={busy}
								onClick={() => {
									setSelected(null);
									setContent(blank());
									setPreview(null);
									setHistory([]);
									pending.current = null;
								}}
							>
								New draft
							</Button>
							{rows.map((b) => (
								<Button
									key={b.bannerId}
									variant="secondary"
									disabled={busy}
									onClick={() => inspect(b)}
								>
									{b.content.title} · {b.status}
								</Button>
							))}
						</div>
					</Card>
					<Card
						title={
							selected
								? `Edit draft · revision ${selected.resourceRevision}`
								: "Create draft"
						}
					>
						<form
							onSubmit={(e) => {
								e.preventDefault();
								void command("save");
							}}
						>
							<fieldset disabled={busy}>
								<Field
									id="banner-title"
									label="Banner title"
									value={content.title}
									required
									maxLength={120}
									onChange={(e) => update("title", e.target.value)}
								/>
								<Field
									id="banner-description"
									label="Description"
									value={content.description}
									maxLength={1000}
									onChange={(e) => update("description", e.target.value)}
								/>
								<Field
									id="banner-alt"
									label="Image description"
									value={content.altText}
									required
									maxLength={240}
									onChange={(e) => update("altText", e.target.value)}
								/>
								<label htmlFor="banner-image">Approved illustration</label>
								<select
									id="banner-image"
									value={content.assetId}
									onChange={(e) => update("assetId", e.target.value)}
								>
									{Object.entries(cinemaAssets).map(([id, a]) => (
										<option key={id} value={id}>
											{a.label}
										</option>
									))}
								</select>
								<Illustration asset={content.assetId} alt={content.altText} />
								<p>
									Schedule: enter ISO 8601 instants with an explicit offset,
									e.g. 2026-11-01T18:00:00+05:30. Venue timezone: {timezone}.
								</p>
								<Field
									id="banner-start"
									label="Start instant (with offset)"
									value={content.startsAt}
									required
									onChange={(e) => update("startsAt", e.target.value)}
								/>
								<Field
									id="banner-end"
									label="End instant (with offset)"
									value={content.endsAt}
									required
									onChange={(e) => update("endsAt", e.target.value)}
								/>
								<Field
									id="banner-order"
									label="Display order"
									type="number"
									min={0}
									max={10000}
									value={content.displayOrder}
									onChange={(e) =>
										update("displayOrder", Number(e.target.value))
									}
								/>
								<label htmlFor="banner-link">Safe link</label>
								<select
									id="banner-link"
									value={content.linkTarget}
									onChange={(e) => update("linkTarget", e.target.value)}
								>
									<option value="NONE">No link</option>
									<option value="ROOMS">Explore rooms</option>
								</select>
								<div className="button-row">
									<Button type="submit">Save draft</Button>
									<Button disabled={!selected} onClick={() => show("preview")}>
										Preview saved revision
									</Button>
									<Button
										disabled={!preview}
										onClick={() => command("publish")}
									>
										Publish reviewed revision
									</Button>
									<Button
										disabled={!selected?.publishedRevision}
										onClick={() => command("unpublish")}
									>
										Unpublish
									</Button>
									<Button
										disabled={!selected}
										onClick={() => show("revisions")}
									>
										Revision history
									</Button>
								</div>
							</fieldset>
						</form>
					</Card>
					{preview && (
						<Card title="Protected draft preview — unpublished changes">
							<h3>{preview.content.title}</h3>
							<p>{preview.content.description}</p>
							<Illustration
								asset={preview.content.assetId}
								alt={preview.content.altText}
							/>
							<p>
								{preview.content.startsAt} → {preview.content.endsAt}; order{" "}
								{preview.content.displayOrder}. Publication requires another
								authorized operator.
							</p>
						</Card>
					)}
					{history.length > 0 && (
						<Card title="Immutable revision history">
							{history.map((h) => (
								<p key={h.contentRevision}>
									Revision {h.contentRevision}: {h.content.title} ·{" "}
									{h.content.startsAt} → {h.content.endsAt}
								</p>
							))}
						</Card>
					)}
				</>
			)}
		</>
	);
}
