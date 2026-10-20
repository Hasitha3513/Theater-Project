import { statePresentation, type StateKind } from "../lib/state-presentation";
export type { StateKind } from "../lib/state-presentation";
import type {
	ButtonHTMLAttributes,
	InputHTMLAttributes,
	ReactNode,
	SelectHTMLAttributes,
} from "react";

export function Button({
	children,
	busy = false,
	variant = "primary",
	disabled,
	...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
	busy?: boolean;
	variant?: "primary" | "secondary";
}) {
	return (
		<button
			{...props}
			type={props.type ?? "button"}
			className={`button button-${variant}`}
			disabled={disabled || busy}
			aria-busy={busy || undefined}
		>
			<span className="button-label" aria-hidden={busy || undefined}>
				{children}
			</span>
			<span className="button-busy" aria-hidden={!busy}>
				Please wait…
			</span>
		</button>
	);
}
export function Card({
	title,
	children,
	className = "",
}: {
	title: string;
	children: ReactNode;
	className?: string;
}) {
	return (
		<section className={`card ${className}`}>
			<h2>{title}</h2>
			{children}
		</section>
	);
}
export function Badge({
	children,
	tone = "neutral",
}: {
	children: ReactNode;
	tone?: "neutral" | "success" | "warning" | "danger" | "info";
}) {
	return <span className={`badge badge-${tone}`}>{children}</span>;
}
export function Field({
	id,
	label,
	hint,
	error,
	...props
}: InputHTMLAttributes<HTMLInputElement> & {
	id: string;
	label: string;
	hint?: string;
	error?: string;
}) {
	return (
		<div
			className={`field${props.type === "checkbox" ? " field-checkbox" : ""}`}
		>
			<label htmlFor={id}>{label}</label>
			<input
				{...props}
				id={id}
				aria-invalid={error ? true : undefined}
				aria-describedby={hint || error ? `${id}-help` : undefined}
			/>
			{(hint || error) && (
				<p id={`${id}-help`} className={error ? "field-error" : "muted"}>
					{error ?? hint}
				</p>
			)}
		</div>
	);
}
export function SelectField({
	id,
	label,
	children,
	...props
}: SelectHTMLAttributes<HTMLSelectElement> & { id: string; label: string }) {
	return (
		<div className="field">
			<label htmlFor={id}>{label}</label>
			<select {...props} id={id}>
				{children}
			</select>
		</div>
	);
}
export function PageHeading({
	eyebrow,
	title,
	children,
}: {
	eyebrow: string;
	title: string;
	children: ReactNode;
}) {
	return (
		<header className="page-heading">
			<p className="eyebrow">{eyebrow}</p>
			<h1>{title}</h1>
			<p className="muted">{children}</p>
		</header>
	);
}
export function StateMessage({
	kind,
	children,
	recovery,
}: {
	kind: StateKind;
	children?: ReactNode;
	recovery?: ReactNode;
}) {
	const { title, message, symbol, urgent } = statePresentation[kind];
	return (
		<section
			className={`state state-${kind}`}
			role={urgent ? "alert" : "status"}
			aria-label={title}
			aria-atomic="true"
			aria-busy={kind === "loading" || undefined}
		>
			<div className="state-heading">
				<span className="state-symbol" aria-hidden="true">
					{symbol}
				</span>
				<h2>{title}</h2>
			</div>
			<p>{message}</p>
			{children}
			{recovery && <div className="state-actions">{recovery}</div>}
		</section>
	);
}
