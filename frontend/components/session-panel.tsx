"use client";
import { useEffect, useRef, useState } from "react";
import { endSession, readSession, type SessionResult } from "../lib/session";
import { Badge, Button, Card, StateMessage } from "./primitives";
export function SessionPanel() {
	const [session, setSession] = useState<
		SessionResult | { kind: "idle" | "loading" }
	>({ kind: "idle" });
	const [notice, setNotice] = useState("");
	const generation = useRef(0);
	useEffect(
		() => () => {
			generation.current++;
		},
		[],
	);
	useEffect(() => {
		const clear = () => {
			generation.current++;
			setSession({ kind: "idle" });
			setNotice("");
		};
		window.addEventListener("pagehide", clear);
		window.addEventListener("focus", clear);
		return () => {
			window.removeEventListener("pagehide", clear);
			window.removeEventListener("focus", clear);
		};
	}, []);
	async function refresh() {
		const current = ++generation.current;
		setNotice("");
		setSession({ kind: "loading" });
		const result = await readSession();
		if (generation.current === current) setSession(result);
	}
	async function logout() {
		const current = ++generation.current;
		setSession({ kind: "loading" });
		const ok = await endSession();
		if (generation.current !== current) return;
		setSession({ kind: ok ? "unauthenticated" : "error" });
		setNotice(
			ok
				? "Current session ended. Other sessions are unchanged."
				: "Logout was not confirmed. No account details are retained in this view.",
		);
	}
	return (
		<Card title="Current account session">
			<p className="muted">
				Uses the existing backend account read. A valid account session does not
				establish administrator or venue permissions. Account details are
				cleared before each check and when focus returns.
			</p>
			{session.kind === "idle" ? (
				<p>
					No account data loaded. Check the server for current session state.
				</p>
			) : session.kind === "authenticated" ? (
				<div className="account-summary">
					<Badge tone="success">Session confirmed by server</Badge>
					<dl>
						<dt>Account ID</dt>
						<dd>{session.account.accountId}</dd>
						<dt>Eligibility</dt>
						<dd>{session.account.eligibility}</dd>
					</dl>
					<Button variant="secondary" onClick={logout}>
						Log out of current session
					</Button>
				</div>
			) : (
				<StateMessage kind={session.kind} />
			)}
			<p role="status">{notice}</p>
			<Button busy={session.kind === "loading"} onClick={refresh}>
				Check session
			</Button>
			<p className="muted">
				Production sign-up, provider onboarding and role administration are
				outside this shell. No local/session storage of account or token values.
			</p>
		</Card>
	);
}
