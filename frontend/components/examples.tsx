"use client";
import { useState } from "react";
import {
	Badge,
	Button,
	Card,
	Field,
	SelectField,
	StateMessage,
	type StateKind,
} from "./primitives";
// Generic layout examples, never production room/booking/payment records.
export const examples = [
	{ id: "SAMPLE-01", name: "Example alpha", status: "Ready", tone: "success" },
	{
		id: "SAMPLE-02",
		name: "Example beta",
		status: "Needs review",
		tone: "warning",
	},
	{
		id: "SAMPLE-03",
		name: "Example gamma",
		status: "Unavailable",
		tone: "danger",
	},
] as const;
export function Examples() {
	const [filter, setFilter] = useState("");
	const [view, setView] = useState("cards");
	const [kind, setKind] = useState<StateKind>("empty");
	const shown = examples.filter((row) =>
		row.name.toLowerCase().includes(filter.toLowerCase()),
	);
	return (
		<>
			<p className="fixture-notice">
				<strong>SYNTHETIC UI EXAMPLES</strong> — Isolated layout fixtures. No
				cinema inventory or business operation is represented.
			</p>
			<Card title="One collection, two presentations">
				<div className="controls">
					<Field
						id="example-filter"
						label="Filter examples"
						value={filter}
						onChange={(event) => setFilter(event.target.value)}
						hint="Filters only the three synthetic examples."
					/>
					<fieldset className="view-switch">
						<legend>Example presentation</legend>
						<Button
							variant="secondary"
							aria-pressed={view === "cards"}
							onClick={() => setView("cards")}
						>
							Card view
						</Button>
						<Button
							variant="secondary"
							aria-pressed={view === "table"}
							onClick={() => setView("table")}
						>
							Table view
						</Button>
					</fieldset>
				</div>
				{shown.length === 0 ? (
					<StateMessage kind="empty">
						<Button variant="secondary" onClick={() => setFilter("")}>
							Clear filter
						</Button>
					</StateMessage>
				) : view === "cards" ? (
					<ul className="example-grid">
						{shown.map((row) => (
							<li className="example-card" key={row.id}>
								<span className="sample-id">{row.id}</span>
								<h3>{row.name}</h3>
								<Badge tone={row.tone}>{row.status}</Badge>
								<p className="muted">Synthetic component state</p>
							</li>
						))}
					</ul>
				) : (
					<section
						className="table-region"
						aria-label="Synthetic example table"
					>
						<table>
							<caption>
								Synthetic layout examples — same filtered collection as card
								view
							</caption>
							<thead>
								<tr>
									<th scope="col">Example ID</th>
									<th scope="col">Label</th>
									<th scope="col">Example state</th>
								</tr>
							</thead>
							<tbody>
								{shown.map((row) => (
									<tr key={row.id}>
										<th scope="row">{row.id}</th>
										<td>{row.name}</td>
										<td>
											<Badge tone={row.tone}>{row.status}</Badge>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</section>
				)}
			</Card>
			<div className="content-grid">
				<Card title="Shared request states">
					<SelectField
						id="state-example"
						label="Preview a synthetic state"
						value={kind}
						onChange={(event) => setKind(event.target.value as StateKind)}
					>
						{[
							"empty",
							"loading",
							"error",
							"unauthenticated",
							"denied",
							"unconfigured",
						].map((value) => (
							<option key={value} value={value}>
								{value}
							</option>
						))}
					</SelectField>
					<StateMessage kind={kind} />
				</Card>
				<Card title="Control examples">
					<Field
						id="example-name"
						label="Example label"
						defaultValue="Synthetic example"
					/>
					<Field
						id="invalid-example"
						label="Validation example"
						defaultValue=""
						error="Example error: a label would be required."
					/>
					<div className="button-row">
						<Button disabled>Unavailable example</Button>
						<Button busy>Loading example</Button>
					</div>
					<p className="muted">
						Disabled and busy controls never imply a completed operation.
					</p>
					<div className="button-row">
						<Badge tone="info">Information</Badge>
						<Badge>Unknown</Badge>
					</div>
				</Card>
			</div>
		</>
	);
}
