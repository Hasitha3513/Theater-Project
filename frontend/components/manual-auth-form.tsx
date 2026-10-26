"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { GoogleSso } from "./google-sso";
import { Button, Card, Field } from "./primitives";
export function ManualAuthForm({ mode }: { mode: "register" | "login" }) {
	const [notice, setNotice] = useState("");
	const [busy, setBusy] = useState(false);
	const [pending, setPending] = useState(false);
	const generation = useRef(0);
	const controller = useRef<AbortController | null>(null);
	const locked = useRef(false);
	const form = useRef<HTMLFormElement>(null);
	useEffect(() => {
		const clear = () => {
			generation.current++;
			controller.current?.abort();
			locked.current = false;
			setBusy(false);
			setNotice("");
			setPending(false);
			form.current?.reset();
		};
		window.addEventListener("pagehide", clear);
		return () => {
			clear();
			window.removeEventListener("pagehide", clear);
		};
	}, []);
	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (locked.current) return;
		locked.current = true;
		setBusy(true);
		setNotice("");
		const current = ++generation.current;
		const abort = new AbortController();
		controller.current = abort;
		const data = new FormData(event.currentTarget);
		const verify = pending;
		if (!verify) {
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
		const body = verify
			? { token: String(data.get("token") ?? "") }
			: {
					email: String(data.get("email") ?? ""),
					password: String(data.get("password") ?? ""),
				};
		// Clear credentials immediately; nothing is written to browser storage or URLs.
		form.current?.reset();
		try {
			const csrfResponse = await fetch("/api/auth/csrf", {
				cache: "no-store",
				signal: abort.signal,
			});
			if (!csrfResponse.ok) throw Error();
			const csrf = await csrfResponse.json();
			if (current !== generation.current) return;
			const action = verify
				? "email-verifications"
				: mode === "register"
					? "registrations"
					: "login";
			const response = await fetch(`/api/auth/${action}`, {
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
			if (response.status === 202) {
				setPending(true);
				setNotice(
					"If this test registration can proceed, a private local test-outbox message is available. No real email was sent. Obtain the verification token from your local test operator.",
				);
			} else if (response.status === 204) {
				setPending(false);
				setNotice(
					verify
						? "Test email verified. You can now log in. This does not establish commercial or physical-access eligibility."
						: "Login confirmed. Open Account to check your current server session.",
				);
			} else {
				setPending(false);
				setNotice(
					response.status === 429
						? "Too many attempts. Wait for the configured local test window before retrying."
						: response.status === 401
							? "Login was not accepted. Check your credentials and test-email verification."
							: response.status === 400
								? "Request not accepted. Check the test email, password or verification token; expired and used tokens cannot be renewed here."
								: "Authentication is unavailable or denied. The local synthetic profile must be explicitly enabled. No success is assumed.",
				);
			}
		} catch {
			if (current === generation.current) {
				setPending(false);
				setNotice(
					"The result was not confirmed. Check Account before retrying login. Registration retries do not replace an existing account or password.",
				);
			}
		} finally {
			if (current === generation.current) {
				locked.current = false;
				setBusy(false);
			}
		}
	}
	return (
		<Card
			title={
				pending
					? "Verify test email"
					: mode === "register"
						? "Create account"
						: "Email/password login"
			}
		>
			<p>
				Local synthetic accounts only: use an address ending in @example.test.
				Real email delivery and password recovery are unavailable.
			</p>
			<form
				method="post"
				action={
					pending
						? "/api/auth/email-verifications"
						: mode === "register"
							? "/api/auth/registrations"
							: "/api/auth/login"
				}
				ref={form}
				onSubmit={submit}
				aria-label={
					pending
						? "Test email verification"
						: mode === "register"
							? "Customer registration"
							: "Customer login"
				}
			>
				{pending ? (
					<Field
						id="verification-token"
						name="token"
						label="Private test-email verification token"
						required
						minLength={64}
						pattern="[0-9a-f]{64}"
						autoComplete="off"
						type="password"
					/>
				) : (
					<>
						<Field
							id="auth-email"
							name="email"
							label="Test email address"
							type="email"
							required
							maxLength={254}
							autoComplete="username"
							hint="Reserved domain: example.test"
						/>
						<Field
							id="auth-password"
							name="password"
							label="Password"
							type="password"
							required
							minLength={15}
							autoComplete={
								mode === "register" ? "new-password" : "current-password"
							}
							hint="15–128 characters. Common weak passwords are rejected; passwords are never silently shortened."
						/>
					</>
				)}
				<Button type="submit" busy={busy}>
					{pending
						? "Verify test email"
						: mode === "register"
							? "Create test account"
							: "Log in"}
				</Button>
			</form>
			<p role="status" aria-live="polite">
				{notice}
			</p>
			<p>
				<a href="/account">Account and logout</a> ·{" "}
				<a href={mode === "register" ? "/login" : "/register"}>
					{mode === "register" ? "Log in" : "Create test account"}
				</a>
			</p>
			<GoogleSso /> <Button disabled>Facebook Login unavailable</Button>
		</Card>
	);
}
