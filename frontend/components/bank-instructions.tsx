"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type BankInstruction,
	type InstructionContext,
	instructionPath,
	loadInstruction,
} from "../lib/bank-instructions";
import { RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField } from "./primitives";
export function BankInstructionPanel() {
	const [context, setContext] = useState<InstructionContext>({
		venue: "",
		target: "",
		kind: "revision",
	});
	const [value, setValue] = useState<BankInstruction>();
	const [busy, setBusy] = useState(false),
		[failed, setFailed] = useState(false);
	const [message, setMessage] = useState(
		"No instruction loaded. Enter exact references and load explicitly.",
	);
	const generation = useRef(0),
		pending = useRef<AbortController | undefined>(undefined),
		status = useRef<HTMLParagraphElement>(null);
	const clear = useCallback(() => {
		generation.current++;
		pending.current?.abort();
		setValue(undefined);
		setBusy(false);
	}, []);
	useEffect(() => {
		const hide = () => {
			clear();
			setFailed(false);
			setMessage(
				"Protected instructions cleared. Load explicitly to check current access.",
			);
		};
		const events = [
			"blur",
			"focus",
			"pagehide",
			"pageshow",
			"popstate",
			"cinenest-session-change",
		];
		for (const e of events) window.addEventListener(e, hide);
		document.addEventListener("visibilitychange", hide);
		return () => {
			generation.current++;
			pending.current?.abort();
			for (const e of events) window.removeEventListener(e, hide);
			document.removeEventListener("visibilitychange", hide);
		};
	}, [clear]);
	function change(c: InstructionContext) {
		clear();
		setFailed(false);
		setContext(c);
		setMessage("Context changed. Load the selected instructions explicitly.");
	}
	async function load() {
		clear();
		const token = generation.current,
			selected = { ...context };
		try {
			instructionPath(selected);
		} catch {
			setFailed(true);
			setMessage(
				"Enter canonical venue and instruction or request UUID references.",
			);
			status.current?.focus();
			return;
		}
		const controller = new AbortController();
		pending.current = controller;
		setBusy(true);
		setFailed(false);
		setMessage("Loading protected staging instructions…");
		try {
			const result = await loadInstruction(
				selected,
				AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]),
			);
			if (token !== generation.current) return;
			setValue(result);
			setMessage(
				"Immutable staging instruction loaded. No account activation or payment collection.",
			);
		} catch (error) {
			if (token !== generation.current) return;
			setValue(undefined);
			setFailed(true);
			const code = error instanceof RoomRequestError ? error.status : 503;
			setMessage(
				code === 401
					? "Session expired. Sign in, then load again."
					: code === 403
						? "Access denied. Current exact-venue bank.instructions.read is required; request reads also require existing bank review permissions."
						: code === 404
							? "Instructions unavailable in this venue, or the feature is disabled."
							: code === 409
								? "No staging binding recorded. Existing requests are not backfilled. Select another context explicitly."
								: code === 400
									? "Check the exact venue and target references."
									: "Staging instructions unavailable or response invalid. Retry explicitly; no account details are shown.",
			);
			queueMicrotask(() => {
				if (token === generation.current) status.current?.focus();
			});
		} finally {
			if (token === generation.current) setBusy(false);
		}
	}
	return (
		<Card title="Staging Bank Instructions" className="bank-instruction-panel">
			<p className="fixture-notice">
				NON-PAYABLE STAGING FIXTURE · DO NOT TRANSFER MONEY
			</p>
			<p>
				Protected instruction inspection only. Receipt review, verified credit,
				allocation, booking confirmation and physical access remain separate.
			</p>
			<div className="filter-grid">
				<Field
					id="instruction-venue"
					label="Instruction venue reference"
					value={context.venue}
					onChange={(e) => change({ ...context, venue: e.target.value })}
				/>
				<SelectField
					id="instruction-kind"
					label="Instruction lookup"
					value={context.kind}
					onChange={(e) =>
						change({
							...context,
							kind: e.target.value as InstructionContext["kind"],
						})
					}
				>
					<option value="revision">Exact instruction revision</option>
					<option value="request">Exact bank request</option>
				</SelectField>
				<Field
					id="instruction-target"
					label="Instruction or request reference"
					value={context.target}
					onChange={(e) => change({ ...context, target: e.target.value })}
				/>
			</div>
			<div className="instruction-actions">
				<Button busy={busy} onClick={() => load()}>
					Load Instructions
				</Button>
				<Button
					variant="secondary"
					disabled={busy || !failed}
					onClick={() => load()}
				>
					Retry Instructions
				</Button>
				<Button
					variant="secondary"
					onClick={() => {
						clear();
						setFailed(false);
						setMessage("Protected instructions cleared.");
					}}
				>
					Clear Instructions
				</Button>
			</div>
			<p
				ref={status}
				tabIndex={-1}
				role={failed ? "alert" : "status"}
				aria-live={failed ? "assertive" : "polite"}
			>
				{message}
			</p>
			{value && (
				<dl
					className="account-summary instruction-facts"
					aria-label="Protected staging instruction detail"
				>
					<div>
						<dt>Instruction version</dt>
						<dd>{value.instructionVersion}</dd>
					</div>
					<div>
						<dt>Venue</dt>
						<dd>{value.venueId}</dd>
					</div>
					<div>
						<dt>Effective interval (UTC)</dt>
						<dd>
							{value.effectiveFrom} — {value.effectiveUntil}
						</dd>
					</div>
					<div>
						<dt>Fixture approval recorded</dt>
						<dd>
							{value.approvedAt} · Independent staging metadata; not bank
							ownership verification.
						</dd>
					</div>
					<div>
						<dt>Server observation</dt>
						<dd>{value.observedAt}</dd>
					</div>
					<div>
						<dt>Receiving account</dt>
						<dd>{value.instruction.accountDisplay}</dd>
					</div>
					<div>
						<dt>Payment collection</dt>
						<dd>DISABLED — {value.instruction.warning}</dd>
					</div>
				</dl>
			)}
		</Card>
	);
}
