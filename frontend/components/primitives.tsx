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
			<span className="button-label" aria-hidden={busy || undefined}>{children}</span>
            <span className="button-busy" aria-hidden={!busy}>Please wait…</span>
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
		<div className="field">
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
export type StateKind =
	| "loading"
	| "empty"
	| "error"
	| "unauthenticated"
	| "denied"
	| "unconfigured";
const states: Record<StateKind, [string, string]> = {
	loading: ["Loading", "Waiting for a response. No result has been confirmed."],
	empty: [
		"Nothing here yet",
		"There are no matching examples. Try a different filter.",
	],
	error: [
		"Unable to connect",
		"The request could not be completed. Try again when the service is available.",
	],
	unauthenticated: [
		"Sign-in required",
		"No active session was found. An expired session also returns this state.",
	],
	denied: [
		"Access denied",
		"The server did not permit this request. No protected account data is displayed.",
	],
	unconfigured: [
		"Session service not configured",
		"This shell does not create accounts or simulate sign-in. A configured backend connection is required.",
	],
};
export function StateMessage({
	kind,
	children,
}: {
	kind: StateKind;
	children?: ReactNode;
}) {
	const [title, message] = states[kind];
	return (
		<section
			className={`state state-${kind}`}
			role={kind === "error" || kind === "denied" ? "alert" : "status"}
			aria-busy={kind === "loading" || undefined}
		>
			<h2>{title}</h2>
			<p>{message}</p>
			{children}
		</section>
	);
}
