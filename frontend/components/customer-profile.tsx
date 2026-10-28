"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { type CustomerProfile, profileRecord } from "../lib/customer-profile";
import { CustomerBookings } from "./customer-bookings";
import { Button, Card, Field } from "./primitives";
export function CustomerProfilePanel() {
	const [profile, setProfile] = useState<CustomerProfile | null>(null),
		[busy, setBusy] = useState(false),
		[editing, setEditing] = useState(false),
		[name, setName] = useState(""),
		[phone, setPhone] = useState(""),
		[notice, setNotice] = useState("");
	const generation = useRef(0);
	const load = useCallback(async () => {
		const epoch = ++generation.current;
		setProfile(null);
		setEditing(false);
		setName("");
		setPhone("");
		setNotice("");
		setBusy(true);
		try {
			const r = await fetch("/api/accounts/me/profile", {
				credentials: "same-origin",
				cache: "no-store",
			});
			if (!r.ok) throw Error();
			const p = profileRecord(await r.json());
			if (epoch !== generation.current) return;
			setProfile(p);
			setName(p.fullName);
			setPhone(p.phone ?? "");
		} catch {
			if (epoch === generation.current)
				setNotice(
					"Profile unavailable. Sign in to an eligible local test account; this feature must be enabled in the isolated environment.",
				);
		} finally {
			if (epoch === generation.current) setBusy(false);
		}
	}, []);
	useEffect(() => {
		void load();
		const clear = () => {
			generation.current++;
			setProfile(null);
			setName("");
			setPhone("");
			setEditing(false);
			setBusy(false);
			setNotice("Profile cleared. Reload to check the current session.");
		};
		window.addEventListener("pagehide", clear);
		window.addEventListener("focus", clear);
		window.addEventListener("cinenest-session-change", clear);
		const visibility = () => {
			if (document.visibilityState === "hidden") clear();
		};
		document.addEventListener("visibilitychange", visibility);
		return () => {
			generation.current++;
			window.removeEventListener("pagehide", clear);
			window.removeEventListener("focus", clear);
			window.removeEventListener("cinenest-session-change", clear);
			document.removeEventListener("visibilitychange", visibility);
		};
	}, [load]);
	async function save(event: React.FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!profile || busy) return;
		const epoch = ++generation.current;
		setBusy(true);
		setNotice("");
		try {
			const c = await fetch("/api/session/csrf", {
				credentials: "same-origin",
				cache: "no-store",
			});
			if (!c.ok) throw Error();
			const csrf = await c.json();
			if (
				csrf.headerName !== "X-CSRF-TOKEN" ||
				typeof csrf.token !== "string" ||
				!csrf.token
			)
				throw Error();
			const r = await fetch("/api/accounts/me/profile", {
				method: "PUT",
				credentials: "same-origin",
				cache: "no-store",
				headers: {
					"Content-Type": "application/json",
					"X-CSRF-TOKEN": csrf.token,
				},
				body: JSON.stringify({
					fullName: name,
					phone: phone.trim() || null,
					expectedRevision: profile.revision,
				}),
			});
			if (epoch !== generation.current) return;
			if (r.status === 409) {
				setProfile(null);
				setEditing(false);
				setName("");
				setPhone("");
				setNotice(
					"Profile changed in another session. Reload the latest profile before editing again.",
				);
				return;
			}
			if (r.status === 400) {
				setNotice(
					"Check the full name and phone format; no update was accepted.",
				);
				return;
			}
			if (!r.ok) throw Error();
			const p = profileRecord(await r.json());
			if (epoch !== generation.current) return;
			setProfile(p);
			setName(p.fullName);
			setPhone(p.phone ?? "");
			setEditing(false);
			setNotice("Profile saved.");
		} catch {
			if (epoch === generation.current) {
				setProfile(null);
				setEditing(false);
				setName("");
				setPhone("");
				setNotice(
					"Save was not confirmed. Reload the authoritative profile before retrying.",
				);
			}
		} finally {
			if (epoch === generation.current) setBusy(false);
		}
	}
	function cancel() {
		setName(profile?.fullName ?? "");
		setPhone(profile?.phone ?? "");
		setEditing(false);
		setNotice("");
	}
	return (
		<>
			<Card title="My profile">
				<p className="muted">
					Local synthetic profile only. Phone is unverified. Profile changes do
					not change booking, payment or access eligibility.
				</p>
				<p role="status" aria-live="polite">
					{busy ? "Loading profile…" : notice}
				</p>
				{profile && (
					<>
						<dl className="account-summary">
							<div>
								<dt>Customer ID</dt>
								<dd>{profile.customerId}</dd>
							</div>
							<div>
								<dt>Full name</dt>
								<dd>{profile.fullName || "Not provided"}</dd>
							</div>
							<div>
								<dt>Phone (unverified)</dt>
								<dd>{profile.phone ?? "Not provided"}</dd>
							</div>
							<div>
								<dt>Verified email</dt>
								<dd>
									{profile.verifiedEmail ??
										"Not available from existing verified records"}
								</dd>
							</div>
							<div>
								<dt>Email verification status</dt>
								<dd>{profile.emailVerificationStatus}</dd>
							</div>
							<div>
								<dt>Account status</dt>
								<dd>{profile.accountStatus}</dd>
							</div>
							<div>
								<dt>Linked login methods</dt>
								<dd>
									{profile.linkedProviderTypes.join(", ") || "None recorded"}
								</dd>
							</div>
							<div>
								<dt>Profile revision</dt>
								<dd>{profile.revision}</dd>
							</div>
							<div>
								<dt>Last updated</dt>
								<dd>{profile.updatedAt ?? "Not updated yet"}</dd>
							</div>
						</dl>
						<p className="muted">
							Recorded provider associations are not live-provider or production
							qualification.
						</p>
						{editing ? (
							<form
								method="post"
								action="/api/accounts/me/profile"
								onSubmit={save}
							>
								<Field
									id="profile-full-name"
									label="Full name"
									value={name}
									onChange={(e) => setName(e.target.value)}
									required
									maxLength={240}
									autoComplete="name"
									hint="1–120 Unicode characters; single line."
								/>
								<Field
									id="profile-phone"
									label="Phone number (optional, unverified)"
									value={phone}
									onChange={(e) => setPhone(e.target.value)}
									maxLength={32}
									autoComplete="tel"
									type="tel"
									hint="6–15 digits; optional leading +, spaces, parentheses and hyphens."
								/>
								<Button type="submit" busy={busy}>
									Save profile
								</Button>{" "}
								<Button variant="secondary" disabled={busy} onClick={cancel}>
									Cancel profile edit
								</Button>
							</form>
						) : (
							<Button disabled={busy} onClick={() => setEditing(true)}>
								Edit profile
							</Button>
						)}
					</>
				)}
				<p>
					<Button variant="secondary" busy={busy} onClick={() => void load()}>
						Reload profile
					</Button>
				</p>
			</Card>
			{profile && <CustomerBookings embedded />}
		</>
	);
}
