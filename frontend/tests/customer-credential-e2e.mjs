// Actual Chromium -> Next.js -> Spring HTTPS -> PostgreSQL. No response interception.
// The protected test mailbox is not a real email provider or production TLS qualification.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { lstat, readFile, realpath, writeFile } from "node:fs/promises";
import http from "node:http";
import https from "node:https";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const expectedGroups = [
	"configured-real-backend-forms",
	"registration-postgres-and-private-test-mail",
	"verification-single-use-and-pending-login-denial",
	"login-own-account-and-session-security",
	"generic-recovery-request-and-private-test-mail",
	"reset-invalidates-old-sessions-and-password",
	"csrf-command-replay-conflict-and-no-secret-storage",
];
export function validateCustomerCredentialBrowserResults(value) {
	assert.ok(
		value?.status === "PASS" &&
			value.interception === false &&
			value.interceptedRequests === 0 &&
			value.cookieInjection === false,
		"Real-backend result flags required",
	);
	assert.ok(
		Array.isArray(value.groups) &&
			value.groups.length === expectedGroups.length &&
			value.groups.every(
				(group, index) =>
					group.id === expectedGroups[index] && group.status === "PASS",
			),
		"Every planned browser group must execute and pass",
	);
	assert.ok(
		Array.isArray(value.requests) &&
			value.requests.length > 0 &&
			value.requests.every(
				(request) =>
					/^\/api\/[A-Za-z0-9/-]+$/.test(request.path) &&
					Number.isInteger(request.status) &&
					request.status >= 100 &&
					request.status <= 599,
			),
		"Sanitized actual HTTP evidence required",
	);
	return value;
}
export async function readCustomerCredentialConfiguration(run) {
	assert.ok(
		typeof run === "string" &&
			/^\/tmp\/cinenest-customer-credential-[A-Za-z0-9_-]+$/.test(run),
		"Owned qualification run directory required",
	);
	assert.equal(await realpath(run), run, "Canonical run directory required");
	const rootStat = await lstat(run);
	assert.ok(
		rootStat.isDirectory() &&
			rootStat.uid === process.getuid() &&
			(rootStat.mode & 511) === 448,
		"Private owned run directory required",
	);
	const configPath = `${run}/config.json`;
	const configStat = await lstat(configPath);
	assert.ok(
		configStat.isFile() &&
			!configStat.isSymbolicLink() &&
			configStat.uid === process.getuid() &&
			(configStat.mode & 511) === 384,
		"Private test configuration required",
	);
	const config = JSON.parse(await readFile(configPath, "utf8"));
	assert.deepEqual(
		Object.keys(config).sort(),
		[
			"backendOrigin",
			"caPath",
			"tlsCertPath",
			"tlsKeyPath",
			"controlOrigin",
		].sort(),
		"Exact qualification configuration required",
	);
	function origin(value, protocol) {
		const url = new URL(value);
		assert.ok(
			url.protocol === protocol &&
				["127.0.0.1", "localhost", "[::1]"].includes(url.hostname) &&
				url.port &&
				url.pathname === "/" &&
				!url.search &&
				!url.hash &&
				!url.username &&
				!url.password,
			"Explicit loopback origin required",
		);
		return url.origin;
	}
	const backend = origin(config.backendOrigin, "https:");
	const controlOrigin = origin(config.controlOrigin, "http:");
	const tls = `${run}/tls`;
	const tlsStat = await lstat(tls);
	assert.ok(
		tlsStat.isDirectory() &&
			!tlsStat.isSymbolicLink() &&
			tlsStat.uid === process.getuid() &&
			(tlsStat.mode & 511) === 448 &&
			(await realpath(tls)) === tls,
		"Private canonical test TLS directory required",
	);
	for (const [field, filename] of Object.entries({
		caPath: "ca.crt",
		tlsCertPath: "server.crt",
		tlsKeyPath: "server.key",
	})) {
		const path = config[field];
		assert.ok(
			typeof path === "string" &&
				path === `${tls}/${filename}` &&
				dirname(path) === tls &&
				resolve(path) === path &&
				(await realpath(path)) === path,
			"Only exact owned test TLS inputs permitted",
		);
		const stat = await lstat(path);
		assert.ok(
			stat.isFile() &&
				!stat.isSymbolicLink() &&
				stat.uid === process.getuid() &&
				(stat.mode & 511) === 384,
			"Private owned test TLS inputs required",
		);
	}
	return { config, backend, controlOrigin };
}
export async function runCustomerCredentialJourney() {
	const run = process.env.CINENEST_CUSTOMER_RUN ?? process.argv[2];
	const { config, backend, controlOrigin } =
		await readCustomerCredentialConfiguration(run);
	const controlKey = process.env.CINENEST_CUSTOMER_CONTROL_KEY;
	assert.ok(
		typeof controlKey === "string" &&
			controlKey.length >= 32 &&
			controlKey.length <= 256,
		"Protected test-control authority required",
	);
	const { chromium } = await import(
		pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
	);
	let frontend,
		relay,
		browser,
		base,
		activeStage = "startup";
	const results = [],
		requests = [];
	let pageError = false;
	async function control(payload) {
		const response = await fetch(new URL("/control", controlOrigin), {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				"X-Cinenest-Test-Control": controlKey,
			},
			body: JSON.stringify(payload),
			signal: AbortSignal.timeout(10000),
			redirect: "error",
		});
		assert.ok(response.ok, "Protected test-control operation failed");
		return response.json();
	}
	async function group(name, action) {
		activeStage = name;
		await action();
		results.push({ id: name, status: "PASS" });
	}
	async function reservePort() {
		const server = http.createServer();
		server.listen(0, "127.0.0.1");
		await once(server, "listening");
		const port = server.address().port;
		await new Promise((r) => server.close(r));
		return port;
	}
	async function stopProcess(child) {
		if (!child || child.exitCode !== null) return;
		const exited = once(child, "exit");
		child.kill("SIGTERM");
		const ended = await Promise.race([
			exited.then(() => true),
			new Promise((r) => setTimeout(() => r(false), 5000)),
		]);
		if (!ended) {
			child.kill("SIGKILL");
			await exited;
		}
	}
	const email = "credential-browser@qualification.invalid";
	const password = "Qualification customer phrase 2026!";
	const newPassword = "Changed qualification customer phrase 2026!";
	let verificationToken = "",
		recoveryToken = "";
	try {
		const port = await reservePort();
		frontend = spawn(
			process.execPath,
			[
				"node_modules/next/dist/bin/next",
				"start",
				"--hostname",
				"127.0.0.1",
				"--port",
				String(port),
			],
			{
				cwd: new URL("..", import.meta.url),
				env: {
					PATH: process.env.PATH,
					HOME: process.env.HOME,
					NODE_EXTRA_CA_CERTS: config.caPath,
					NEXT_TELEMETRY_DISABLED: "1",
					CINENEST_AUTH_ORIGIN: backend,
					CINENEST_FACEBOOK_MODE: "DISABLED",
					CINENEST_FACEBOOK_LIVE_EXECUTION: "false",
					CINENEST_GOOGLE_MODE: "DISABLED",
					CINENEST_GOOGLE_LIVE_EXECUTION: "false",
				},
				stdio: "ignore",
			},
		);
		for (let attempt = 0; attempt < 200; attempt++) {
			if (frontend.exitCode !== null)
				throw Error("Owned frontend stopped before readiness");
			try {
				if (
					(
						await fetch(`http://127.0.0.1:${port}/login`, {
							signal: AbortSignal.timeout(1000),
						})
					).status === 200
				)
					break;
			} catch {}
			if (attempt === 199) throw Error("Frontend readiness timeout");
			await new Promise((r) => setTimeout(r, 100));
		}
		relay = https.createServer(
			{
				key: await readFile(config.tlsKeyPath),
				cert: await readFile(config.tlsCertPath),
				minVersion: "TLSv1.2",
			},
			(request, response) => {
				const authority = new URL(base).host;
				response.setHeader("Cache-Control", "no-store");
				if (
					request.headers.host !== authority ||
					Object.keys(request.headers).some((key) =>
						/^(forwarded|x-forwarded-|x-original-|x-rewrite-|proxy-)/i.test(
							key,
						),
					) ||
					(request.headers.origin && request.headers.origin !== base) ||
					!/^\/(?!\/)/.test(request.url ?? "")
				) {
					response.writeHead(400);
					response.end();
					return;
				}
				const headers = {
					...request.headers,
					host: authority,
					"x-forwarded-host": authority,
					"x-forwarded-proto": "https",
					"x-forwarded-port": new URL(base).port,
					"x-forwarded-for": "127.0.0.1",
				};
				delete headers.connection;
				const upstream = http.request(
					{
						hostname: "127.0.0.1",
						port,
						path: request.url,
						method: request.method,
						headers,
						timeout: 15000,
					},
					(incoming) => {
						response.writeHead(incoming.statusCode ?? 502, incoming.headers);
						incoming.pipe(response);
						incoming.on("error", () => response.destroy());
					},
				);
				upstream.on("timeout", () => upstream.destroy());
				upstream.on("error", () => {
					if (!response.headersSent) response.writeHead(503);
					response.end();
				});
				request.on("aborted", () => upstream.destroy());
				request.pipe(upstream);
			},
		);
		relay.requestTimeout = 30000;
		relay.headersTimeout = 10000;
		relay.on("upgrade", (_request, socket) => socket.destroy());
		relay.listen(0, "127.0.0.1");
		await once(relay, "listening");
		base = `https://127.0.0.1:${relay.address().port}`;
		browser = await chromium.launch({
			headless: true,
			args: ["--disable-dev-shm-usage"],
		});
		// Only this task-owned UI uses a generated self-signed test certificate.
		const context = await browser.newContext({ ignoreHTTPSErrors: true });
		const page = await context.newPage();
		page.on("pageerror", () => {
			pageError = true;
		});
		page.on("response", (response) => {
			const url = new URL(response.url());
			if (url.origin === base && url.pathname.startsWith("/api/"))
				requests.push({ path: url.pathname, status: response.status() });
		});
		async function visit(path) {
			await page.goto(base + path);
			await page.getByText("QUALIFICATION ONLY", { exact: false }).waitFor();
		}
		async function post(action, body) {
			return page.evaluate(
				async ({ action, body }) => {
					const csrf = await (
						await fetch("/api/auth/csrf", { cache: "no-store" })
					).json();
					return (
						await fetch(`/api/auth/${action}`, {
							method: "POST",
							headers: {
								"Content-Type": "application/json",
								[csrf.headerName]: csrf.token,
							},
							body: JSON.stringify(body),
							cache: "no-store",
						})
					).status;
				},
				{ action, body },
			);
		}
		async function login(value) {
			await visit("/login");
			await page.getByLabel("Email address", { exact: true }).fill(email);
			await page.getByLabel("Password", { exact: true }).fill(value);
			await page.getByRole("button", { name: "Log in", exact: true }).click();
		}
		await group("configured-real-backend-forms", async () => {
			for (const width of [375, 768, 1280]) {
				await page.setViewportSize({ width, height: 900 });
				for (const path of [
					"/register",
					"/login",
					"/verify-email",
					"/recover-account",
					"/reset-password",
				]) {
					await visit(path);
					assert.equal(await page.locator("main").count(), 1);
					assert.equal(await page.locator("h1").count(), 1);
					assert.ok(
						await page.evaluate(
							() => document.documentElement.scrollWidth <= innerWidth,
						),
					);
				}
			}
			assert.ok(
				await page
					.getByRole("button", { name: /Google sign-in unavailable/ })
					.isDisabled(),
			);
			assert.ok(
				await page
					.getByRole("button", { name: /Facebook Login unavailable/ })
					.isDisabled(),
			);
		});
		await group("registration-postgres-and-private-test-mail", async () => {
			await visit("/register");
			await page.getByLabel("Email address", { exact: true }).fill(email);
			await page.getByLabel("Password", { exact: true }).fill(password);
			await page
				.getByLabel(
					"I acknowledge the configured privacy notice reference shown above.",
				)
				.check();
			await page
				.getByRole("button", { name: "Create account", exact: true })
				.click();
			await page
				.getByText("If this request can proceed,", { exact: false })
				.waitFor();
			assert.ok(
				(await control({ op: "assert-state", email, phase: "REGISTERED" }))
					.ok === true,
			);
			const mail = await control({
				op: "mail",
				email,
				purpose: "VERIFY_EMAIL",
			});
			assert.ok(
				typeof mail.token === "string" && /^[0-9a-f]{64}$/.test(mail.token),
				"Private verification capability missing",
			);
			verificationToken = mail.token;
		});
		await group(
			"verification-single-use-and-pending-login-denial",
			async () => {
				await login(password);
				await page
					.getByText("Login was not accepted.", { exact: false })
					.waitFor();
				assert.equal(
					await post("email-verifications", {
						commandId: crypto.randomUUID(),
						token: verificationToken,
						password: "Wrong registration intent phrase!",
					}),
					401,
					"Token alone cannot activate an attacker-chosen password",
				);
				await visit("/verify-email");
				const registrationCount = requests.filter(
					(request) => request.path === "/api/auth/registrations",
				).length;
				const requestAgain = page.getByRole("link", {
					name: "Request another verification message",
					exact: true,
				});
				assert.equal(await requestAgain.getAttribute("href"), "/register");
				await requestAgain.click();
				await page
					.getByRole("heading", { name: "Create an account", exact: true })
					.waitFor();
				assert.equal(new URL(page.url()).pathname, "/register");
				assert.equal(new URL(page.url()).search, "");
				assert.equal(
					requests.filter(
						(request) => request.path === "/api/auth/registrations",
					).length,
					registrationCount,
					"Navigation must not automatically resend a request",
				);
				await visit("/verify-email");
				await page
					.getByLabel("Private email verification token", { exact: true })
					.fill(verificationToken);
				await page
					.getByLabel("Registration password", { exact: true })
					.fill(password);
				await page
					.getByRole("button", { name: "Verify email", exact: true })
					.click();
				await page
					.getByText("Email verification confirmed.", { exact: false })
					.waitFor();
				assert.ok(
					(await control({ op: "assert-state", email, phase: "VERIFIED" }))
						.ok === true,
				);
				assert.ok(
					[400, 409].includes(
						await post("email-verifications", {
							commandId: crypto.randomUUID(),
							token: verificationToken,
							password,
						}),
					),
				);
				verificationToken = "";
			},
		);
		await group("login-own-account-and-session-security", async () => {
			await login(password);
			await page.getByText("Login confirmed.", { exact: false }).waitFor();
			const cookie = (await context.cookies()).find(
				(c) => c.name === "CINENEST_SESSION",
			);
			assert.ok(
				cookie?.secure && cookie.httpOnly && cookie.sameSite === "Lax",
				"Secure session cookie required",
			);
			await page.goto(`${base}/account`);
			await page.getByRole("button", { name: /Check session/ }).click();
			await page.getByText("Session confirmed by server").waitFor();
			const own = await page.evaluate(async () => {
				const response = await fetch("/api/accounts/me");
				return { status: response.status, body: await response.json() };
			});
			assert.equal(own.status, 200);
			assert.deepEqual(Object.keys(own.body).sort(), [
				"accountId",
				"eligibility",
			]);
		});
		const recoveryContext = await browser.newContext({
			ignoreHTTPSErrors: true,
		});
		const recoveryPage = await recoveryContext.newPage();
		await group("generic-recovery-request-and-private-test-mail", async () => {
			await recoveryPage.goto(`${base}/recover-account`);
			await recoveryPage
				.getByText("QUALIFICATION ONLY", { exact: false })
				.waitFor();
			await recoveryPage
				.getByLabel("Email address", { exact: true })
				.fill(email);
			await recoveryPage
				.getByRole("button", { name: "Request recovery", exact: true })
				.click();
			await recoveryPage
				.getByText("If this request can proceed,", { exact: false })
				.waitFor();
			const mail = await control({
				op: "mail",
				email,
				purpose: "RECOVER_ACCOUNT",
			});
			assert.ok(
				typeof mail.token === "string" && /^[0-9a-f]{64}$/.test(mail.token),
				"Private recovery capability missing",
			);
			recoveryToken = mail.token;
		});
		await group("reset-invalidates-old-sessions-and-password", async () => {
			await recoveryPage.goto(`${base}/reset-password`);
			await recoveryPage
				.getByText("QUALIFICATION ONLY", { exact: false })
				.waitFor();
			await recoveryPage
				.getByLabel("Private recovery token", { exact: true })
				.fill(recoveryToken);
			await recoveryPage
				.getByLabel("New password", { exact: true })
				.fill(newPassword);
			await recoveryPage
				.getByRole("button", { name: "Reset password", exact: true })
				.click();
			await recoveryPage
				.getByText("Password reset confirmed.", { exact: false })
				.waitFor();
			recoveryToken = "";
			assert.ok(
				(await control({ op: "assert-state", email, phase: "RESET" })).ok ===
					true,
			);
			assert.equal(
				await page.evaluate(
					async () =>
						(await fetch("/api/accounts/me", { cache: "no-store" })).status,
				),
				401,
			);
			assert.equal(
				await recoveryPage.evaluate(
					async () =>
						(await fetch("/api/accounts/me", { cache: "no-store" })).status,
				),
				401,
			);
			await login(password);
			await page
				.getByText("Login was not accepted.", { exact: false })
				.waitFor();
			await login(newPassword);
			await page.getByText("Login confirmed.", { exact: false }).waitFor();
		});
		await group(
			"csrf-command-replay-conflict-and-no-secret-storage",
			async () => {
				assert.equal(
					await page.evaluate(
						async () =>
							(
								await fetch("/api/auth/registrations", {
									method: "POST",
									headers: { "Content-Type": "application/json" },
									body: "{}",
								})
							).status,
					),
					403,
				);
				const id = crypto.randomUUID();
				const csrf = await page.evaluate(async () =>
					(await fetch("/api/auth/csrf")).json(),
				);
				const body = {
					commandId: id,
					email: "duplicate-browser@qualification.invalid",
					password,
					privacyRevision: csrf.privacyRevision,
				};
				assert.equal(await post("registrations", body), 202);
				assert.equal(await post("registrations", body), 202);
				assert.equal(
					await post("registrations", { ...body, password: newPassword }),
					409,
				);
				assert.ok(
					(
						await control({
							op: "assert-state",
							email: body.email,
							phase: "REGISTERED",
						})
					).ok === true,
				);
				assert.ok(
					await page.evaluate(
						() => localStorage.length === 0 && sessionStorage.length === 0,
					),
				);
				assert.equal(new URL(page.url()).search, "");
				assert.equal(new URL(page.url()).hash, "");
				assert.ok(!pageError, "Browser application error occurred");
			},
		);
		await recoveryContext.close();
		await context.close();
		await writeFile(
			`${run}/browser-results.json`,
			JSON.stringify(
				validateCustomerCredentialBrowserResults({
					status: "PASS",
					groups: results,
					interception: false,
					interceptedRequests: 0,
					cookieInjection: false,
					delivery: "PRIVATE_TEST_MAILBOX_ONLY",
					tls: "Self-signed task UI trust exception only;not production TLS qualification",
					requests,
					screenshots: 0,
				}),
				null,
				2,
			),
			{ mode: 0o600, flag: "wx" },
		);
	} catch {
		await writeFile(
			`${run}/browser-results.json`,
			JSON.stringify(
				{
					status: "FAIL",
					failedStage: activeStage,
					groups: results,
					interception: false,
					interceptedRequests: 0,
					requests,
				},
				null,
				2,
			),
			{ mode: 0o600, flag: "wx" },
		).catch(() => {});
		process.exitCode = 1;
	} finally {
		verificationToken = "";
		recoveryToken = "";
		await browser?.close();
		if (relay) {
			relay.closeAllConnections();
			await new Promise((r) => relay.close(r));
		}
		await stopProcess(frontend);
	}
}
if (
	process.argv[1] &&
	import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
	await runCustomerCredentialJourney();
}
