// Navigation destinations only: these circles never claim eligibility or completion.
export function VerificationStages() {
	return (
		<nav
			className="verification-stages"
			aria-label="Identity verification sections"
		>
			<ol>
				{[
					["verification-context", "Quotation"],
					["verification-permission", "Consent"],
				].map(([id, label], i) => (
					<li key={id}>
						<a href={`#${id}`}>
							<span aria-hidden="true">{i + 1}</span>
							{label}
						</a>
					</li>
				))}
				<li>
					<span>
						<span aria-hidden="true">3</span>Synthetic evidence
					</span>
				</li>
				<li>
					<span>
						<span aria-hidden="true">4</span>Review status
					</span>
				</li>
			</ol>
			<p>
				Evidence and review appear after you explicitly start or reload a
				request. Steps do not indicate approval.
			</p>
		</nav>
	);
}

const guidance: Record<string, string> = {
	NOT_SUBMITTED:
		"No evidence has been submitted for this request. Choose an approved synthetic fixture when you are ready.",
	SUBMITTED:
		"Evidence has been submitted. Submission alone does not verify identity.",
	PROCESSING:
		"Synthetic evidence is processing. Reload the current request to check its status; no approval is implied.",
	REVIEW_REQUIRED:
		"Your synthetic evidence needs manual review. A submitted image is not an identity decision.",
	VERIFIED:
		"The server reports a verified digital test decision. This is not physical admission approval; bank-request prerequisites are checked separately.",
	REJECTED:
		"The server reports a rejected test decision. Review the recorded decision history. A correction needs the current quotation, policy and fresh consent.",
	EXPIRED:
		"The server reports an expired decision. Reload the quotation and current request before attempting a correction.",
	REVOKED:
		"The server reports a revoked decision. Do not treat an earlier decision as current approval.",
};
export function VerificationReviewGuidance({
	state,
	superseded,
}: {
	state: string;
	superseded: boolean;
}) {
	return (
		<section role="status" className="verification-review-guidance">
			<p>
				<strong>Server status: {state}</strong>
			</p>
			<p>
				{superseded
					? "This request has been superseded. Reload the current request before continuing."
					: (guidance[state] ??
						"Reload the protected request to check the current server status.")}
			</p>
		</section>
	);
}
