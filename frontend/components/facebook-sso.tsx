"use client";
import { useEffect, useState } from "react";
import { Button } from "./primitives";
export function FacebookSso() {
	const [available, setAvailable] = useState(false);
	const [launching, setLaunching] = useState(false);
	const [failed, setFailed] = useState(false);
	const [liveConfigured, setLiveConfigured] = useState(false);
	useEffect(() => {
		let current = true;
		const abort = new AbortController();
		setFailed(
			["denied", "unavailable"].includes(
				new URLSearchParams(window.location.search).get("facebook") ?? "",
			),
		);
		void (async () => {
			try {
				const response = await fetch("/api/auth/facebook/status", {
					cache: "no-store",
					signal: abort.signal,
				});
				if (!response.ok) return;
				const value = await response.json();
				if (current) {
					setLiveConfigured(value.mode === "LIVE_QUALIFICATION");
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
			setLaunching(false);
			setLiveConfigured(false);
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
					Facebook sign-in was rejected, cancelled or unavailable. No account
					was created or linked. Check your current account session before
					retrying.
				</p>
			)}
			{available ? (
				<>
					<p className="muted">
						{liveConfigured ? (
							"Facebook local qualification only;production customer admission is disabled. Only pre-associated eligible test accounts are admitted."
						) : (
							<>
								Synthetic Facebook Web Login qualification only. This is an
								isolated provider fixture, not real Facebook Login. Only
								pre-associated eligible test accounts are admitted.
							</>
						)}
					</p>
					<Button
						variant="secondary"
						disabled={launching}
						onClick={() => {
							setLaunching(true);
							try {
								window.location.assign("/api/auth/facebook/start");
							} catch {
								setLaunching(false);
								setFailed(true);
							}
						}}
					>
						{launching
							? "Opening Facebook qualification…"
							: liveConfigured
								? "Continue with Facebook — local qualification"
								: "Continue with Facebook — synthetic qualification"}
					</Button>
				</>
			) : (
				<div>
					{liveConfigured && (
						<p className="muted">
							Facebook adapter configured; live execution remains disabled.
							Provider qualification is not established.
						</p>
					)}
					<Button disabled>Facebook Login unavailable</Button>
				</div>
			)}
		</div>
	);
}
