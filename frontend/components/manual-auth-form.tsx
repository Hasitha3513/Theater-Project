"use client";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { FacebookSso } from "./facebook-sso";
import { GoogleSso } from "./google-sso";
import { Button, Card, Field } from "./primitives";

type Mode = "register" | "login" | "verify" | "recovery" | "reset";
type Configuration = {
	mode: "LOCAL_SYNTHETIC" | "CUSTOMER_CREDENTIAL";
	privacyRevision?: string;
	policyRevision?: string;
	deliveryMode?: "QUALIFICATION_ONLY" | "APPROVED";
};
const actions: Record<Mode, string> = {
	register: "registrations",
	login: "login",
	verify: "email-verifications",
	recovery: "recovery-requests",
	reset: "recoveries",
};
const labels: Record<Mode, string> = {
	register: "Customer registration",
	login: "Customer login",
	verify: "Email verification",
	recovery: "Account recovery request",
	reset: "Password reset",
};

export function VerificationRequestHelp() {
	return (
		<p>
			<a href="/register">Request another verification message</a>. For a
			pending registration, return to Create account and submit the same email
			and the password you chose for that registration. The configured cooldown
			applies. A generic response does not confirm account existence, that a
			message was sent, or delivery.
		</p>
	);
}

export function ManualAuthForm({ mode }: { mode: Mode }) {
	const [configuration, setConfiguration] = useState<Configuration | null>(
		null,
	);
	const [notice, setNotice] = useState("");
	const [busy, setBusy] = useState(false);
	const [pending, setPending] = useState(false);
	const [uncertain, setUncertain] = useState(false);
	const generation = useRef(0);
	const controller = useRef<AbortController | null>(null);
	const locked = useRef(false);
	const commandId = useRef<string | null>(null);
	const form = useRef<HTMLFormElement>(null);
	const local = configuration?.mode !== "CUSTOMER_CREDENTIAL";
	const actionMode: Mode = pending ? "verify" : mode;
	const unsupported = local && (mode === "recovery" || mode === "reset");

	useEffect(() => {
		const abort = new AbortController();
		const clear = () => {
			generation.current++;
			controller.current?.abort();
			locked.current = false;
			commandId.current = null;
			form.current?.reset();
		};
		// Auth links carry no capabilities. Even an unsupported URL is never consumed.
		if (window.location.search || window.location.hash)
			window.history.replaceState(null, "", window.location.pathname);
		fetch("/api/auth/csrf", { cache: "no-store", signal: abort.signal })
			.then(async (response) => {
				if (!response.ok) throw Error();
				const value = await response.json();
				if (value.mode === "CUSTOMER_CREDENTIAL") {
					if (
						typeof value.privacyRevision !== "string" ||
						typeof value.policyRevision !== "string" ||
						!["QUALIFICATION_ONLY", "APPROVED"].includes(value.deliveryMode)
					)
						throw Error();
					setConfiguration({
						mode: value.mode,
						privacyRevision: value.privacyRevision,
						policyRevision: value.policyRevision,
						deliveryMode: value.deliveryMode,
					});
				} else if (
					value.mode === undefined &&
					typeof value.token === "string"
				) {
					setConfiguration({ mode: "LOCAL_SYNTHETIC" });
				} else throw Error();
			})
			.catch(() => {
				if (!abort.signal.aborted)
					setNotice("Account access is unavailable. No request was submitted.");
			});
		const hide = () => {
			clear();
			setBusy(false);
			setNotice("");
			setPending(false);
			setUncertain(false);
		};
		window.addEventListener("pagehide", hide);
		return () => {
			abort.abort();
			clear();
			window.removeEventListener("pagehide", hide);
		};
	}, []);

	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (locked.current || !configuration || unsupported) return;
		locked.current = true;
		setBusy(true);
		setNotice("");
		const current = ++generation.current;
		const abort = new AbortController();
		controller.current = abort;
		const data = new FormData(event.currentTarget);
		const passwordAction =
			["register", "login", "reset"].includes(actionMode) ||
			(!local && actionMode === "verify");
		if (passwordAction) {
			const length = Array.from(
				String(data.get("password") ?? "").normalize("NFC"),
			).length;
			if (length < 15 || length > 128) {
				form.current?.reset();
				setNotice(
					"Password must contain 15–128 characters. Nothing was submitted or shortened.",
				);
				locked.current = false;
				setBusy(false);
				return;
			}
		}
		const body: Record<string, string> =
			actionMode === "verify"
				? { token: String(data.get("token") ?? "") }
				: actionMode === "reset"
					? {
							token: String(data.get("token") ?? ""),
							newPassword: String(data.get("password") ?? ""),
						}
					: actionMode === "recovery"
						? { email: String(data.get("email") ?? "") }
						: {
								email: String(data.get("email") ?? ""),
								password: String(data.get("password") ?? ""),
							};
		if (!local) {
			if (actionMode === "verify")
				body.password = String(data.get("password") ?? "");
			commandId.current ??= crypto.randomUUID();
			body.commandId = commandId.current;
			if (actionMode === "register")
				body.privacyRevision = configuration.privacyRevision ?? "";
		}
		// Credentials are transient request data only; clear all visible inputs immediately.
		form.current?.reset();
		try {
			const csrfResponse = await fetch("/api/auth/csrf", {
				cache: "no-store",
				signal: abort.signal,
			});
			if (!csrfResponse.ok) throw Error();
			const csrf = await csrfResponse.json();
			if (current !== generation.current) return;
			if (
				csrf.headerName !== "X-CSRF-TOKEN" ||
				typeof csrf.token !== "string" ||
				(local
					? csrf.mode !== undefined
					: csrf.mode !== configuration.mode ||
						csrf.privacyRevision !== configuration.privacyRevision ||
						csrf.policyRevision !== configuration.policyRevision ||
						csrf.deliveryMode !== configuration.deliveryMode)
			)
				throw Error();
			const response = await fetch(`/api/auth/${actions[actionMode]}`, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					[csrf.headerName]: csrf.token,
				},
				body: JSON.stringify(body),
				cache: "no-store",
				signal: abort.signal,
			});
			if (current !== generation.current) return;
			if (response.status === 503) throw Error();
			setUncertain(false);
			if (response.status === 202) {
				commandId.current = null;
				setPending(actionMode === "register");
				setNotice(
					local
						? "If this test registration can proceed, a private local test-outbox message is available. No real email was sent. Obtain the verification token from your local test operator."
						: "If this request can proceed, a verification or recovery message will be prepared through the configured delivery route. This response does not disclose whether an account exists.",
				);
			} else if (response.status === 204) {
				commandId.current = null;
				setPending(false);
				setNotice(
					actionMode === "reset"
						? "Password reset confirmed. Previous sessions are no longer valid. Log in with your new password."
						: actionMode === "verify"
							? local
								? "Test email verified. You can now log in. This does not establish commercial or physical-access eligibility."
								: "Email verification confirmed. You can now log in. This does not establish identity, commercial or physical-access eligibility."
							: "Login confirmed. Open Account to check your current server session.",
				);
			} else {
				if (response.status !== 409) commandId.current = null;
				setNotice(
					response.status === 429
						? "Too many attempts. Wait for the configured security window before retrying."
						: response.status === 409
							? "This request conflicts with its recorded command or current account state. Do not change a retry payload. Start a new request only after checking the outcome."
							: response.status === 401
								? actionMode === "verify"
									? "Verification was not accepted. Enter the password chosen for this registration together with its private email token."
									: "Login was not accepted. Check your credentials and email verification."
								: response.status === 400
									? "Request not accepted. Check the email, password or verification token; expired and used tokens cannot be renewed here."
									: "Authentication is unavailable or denied. No success is assumed.",
				);
			}
		} catch {
			if (current === generation.current) {
				setUncertain(true);
				setNotice(
					"The result was not confirmed. Check Account before retrying login. For a retry, re-enter exactly the same request; its command identity is retained in this page only. No success is assumed.",
				);
			}
		} finally {
			for (const key of Object.keys(body)) body[key] = "";
			if (current === generation.current) {
				locked.current = false;
				setBusy(false);
			}
		}
	}
	const title =
		actionMode === "verify"
			? local
				? "Verify test email"
				: "Verify email"
			: mode === "register"
				? "Create account"
				: mode === "login"
					? "Email/password login"
					: mode === "recovery"
						? "Recover account"
						: "Reset password";
	return (
		<Card title={title}>
			{!configuration ? (
				<p>Checking configured account access…</p>
			) : local ? (
				<p>
					Local synthetic accounts only: use an address ending in @example.test.
					Real email delivery is unavailable.
				</p>
			) : (
				<>
					<p>
						{configuration.deliveryMode === "QUALIFICATION_ONLY"
							? "QUALIFICATION ONLY — private test-mail delivery. No real email was sent and production customer activation is unavailable."
							: "Account requests use the configured protected delivery route."}
					</p>
					<p>
						Privacy notice reference: {configuration.privacyRevision}. Security
						policy reference: {configuration.policyRevision}.
					</p>
				</>
			)}
			{unsupported && configuration ? (
				<p>Recovery is unavailable in the local synthetic account mode.</p>
			) : null}
			{!local && (actionMode === "verify" || mode === "recovery") ? (
				<VerificationRequestHelp />
			) : null}
			<form
				method="post"
				action={`/api/auth/${actions[actionMode]}`}
				ref={form}
				onSubmit={submit}
				aria-label={
					pending && local ? "Test email verification" : labels[actionMode]
				}
			>
				{actionMode === "verify" || actionMode === "reset" ? (
					<Field
						id="verification-token"
						name="token"
						label={
							local
								? "Private test-email verification token"
								: actionMode === "reset"
									? "Private recovery token"
									: "Private email verification token"
						}
						required
						minLength={64}
						pattern="[0-9a-f]{64}"
						autoComplete="off"
						type="password"
					/>
				) : (
					<Field
						id="auth-email"
						name="email"
						label={local ? "Test email address" : "Email address"}
						type="email"
						required
						maxLength={254}
						autoComplete="username"
						hint={
							local
								? "Reserved domain: example.test"
								: "Use your own email address."
						}
					/>
				)}
				{["register", "login", "reset"].includes(actionMode) ||
				(!local && actionMode === "verify") ? (
					<Field
						id="auth-password"
						name="password"
						label={
							actionMode === "reset"
								? "New password"
								: actionMode === "verify"
									? "Registration password"
									: "Password"
						}
						type="password"
						required
						minLength={15}
						autoComplete={
							["login", "verify"].includes(actionMode)
								? "current-password"
								: "new-password"
						}
						hint="15–128 characters. Common weak passwords are rejected; passwords are never silently shortened."
					/>
				) : null}
				{!local && actionMode === "register" ? (
					<Field
						id="privacy-acknowledgement"
						name="privacyAcknowledged"
						type="checkbox"
						label="I acknowledge the configured privacy notice reference shown above."
						required
					/>
				) : null}
				<Button
					type="submit"
					busy={busy}
					disabled={!!configuration && unsupported}
				>
					{actionMode === "verify"
						? local
							? "Verify test email"
							: "Verify email"
						: actionMode === "register"
							? local
								? "Create test account"
								: "Create account"
							: actionMode === "login"
								? "Log in"
								: actionMode === "recovery"
									? "Request recovery"
									: "Reset password"}
				</Button>
			</form>
			<p role="status" aria-live="polite">
				{notice}
			</p>
			{uncertain || notice.startsWith("This request conflicts") ? (
				<Button
					variant="secondary"
					disabled={busy}
					onClick={() => {
						commandId.current = null;
						form.current?.reset();
						setUncertain(false);
						setNotice(
							"A new request will use a new command identity. Check the previous outcome before submitting.",
						);
					}}
				>
					Start a new request
				</Button>
			) : null}
			<p>
				<a href="/account">Account and logout</a> · <a href="/login">Log in</a>{" "}
				·{" "}
				<a href="/register">
					{local ? "Create test account" : "Create account"}
				</a>{" "}
				· <a href="/verify-email">Verify email</a> ·{" "}
				<a href="/recover-account">Recover account</a>
			</p>
			<GoogleSso /> <FacebookSso />
		</Card>
	);
}
