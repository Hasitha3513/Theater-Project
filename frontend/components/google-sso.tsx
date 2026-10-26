"use client";
import { useEffect, useState } from "react";
import { Button } from "./primitives";
export function GoogleSso() {
	const [available, setAvailable] = useState(false);
	const [live, setLive] = useState(false);
	const [configured, setConfigured] = useState(false);
	const [failed, setFailed] = useState(false);
	useEffect(() => {
		let current = true;
		const abort = new AbortController();
		setFailed(
			["denied", "unavailable"].includes(
				new URLSearchParams(window.location.search).get("google") ?? "",
			),
		);
		void (async () => {
			try {
				const response = await fetch("/api/auth/google/status", {
					cache: "no-store",
					signal: abort.signal,
				});
				if (!response.ok) return;
				const value = await response.json();
				if (current) {
					setLive(value.mode === "LIVE_QUALIFICATION");
					setConfigured(
						value.mode === "LIVE_QUALIFICATION" &&
							value.liveProviderQualified === false,
					);
					setAvailable(
						(value.mode === "CONTRACT_TEST" ||
							(value.mode === "LIVE_QUALIFICATION" &&
								value.executionEnabled === true)) &&
							value.liveProviderQualified === false,
					);
				}
			} catch {}
		})();
		const clear = () => {
			current = false;
			abort.abort();
			setAvailable(false);
			setConfigured(false);
			setLive(false);
		};
		window.addEventListener("pagehide", clear);
		return () => {
			clear();
			window.removeEventListener("pagehide", clear);
		};
	}, []);
	return (
		<div>
			{failed && (
				<p role="alert">
					Google sign-in was rejected, cancelled or unavailable. No account was
					created or linked. Check your current account session before retrying.
				</p>
			)}
			{available ? (
				<>
					<p className="muted">
						{live
							? "Google local provider qualification only; not production customer admission or completed provider qualification."
							: "Synthetic Google OIDC qualification only. This is a simulated provider, not real Google Login."}{" "}
						Only pre-associated eligible test accounts are admitted.
					</p>
					<Button
						variant="secondary"
						onClick={() => window.location.assign("/api/auth/google/start")}
					>
						{live
							? "Continue with Google — local qualification"
							: "Continue with Google — synthetic qualification"}
					</Button>
				</>
			) : (
				<div>
					{configured && (
						<p className="muted">
							Google adapter configured; live execution remains disabled.
							Provider qualification is not complete.
						</p>
					)}
					<Button disabled>Google sign-in unavailable</Button>
				</div>
			)}
		</div>
	);
}
