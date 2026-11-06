// Genuine local synthetic login and API responses; no cookie injection or response fabrication.
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

assert.equal(process.env.CINENEST_COMMERCIAL_BROWSER, "AUTHORIZED");
const run = process.env.CINENEST_COMMERCIAL_RUN;
assert.match(run ?? "", /^\/tmp\/cn-commercial-/);
const m = JSON.parse(await readFile(`${run}/preflight.json`, "utf8")),
	base = new URL(process.env.CINENEST_COMMERCIAL_ORIGIN);
assert(["localhost", "127.0.0.1"].includes(base.hostname));
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const context = await chromium.launchPersistentContext(
		`${run}/browser-profile`,
		{ headless: true },
	),
	page = await context.newPage();
page.setDefaultTimeout(15000);
const draftTitle = `Browser synthetic policy ${Date.now()}`;
const checks = [],
	errors = [];
let external = 0;
let createPayload;
page.on("pageerror", (e) => errors.push(e.name));
await context.route("**/*", (r) => {
	const u = new URL(r.request().url());
	if (["http:", "https:"].includes(u.protocol) && u.origin !== base.origin) {
		external++;
		return r.abort();
	}
	return r.continue();
});
async function login(role) {
	await page.goto(new URL("/login", base).href);
	await page
		.getByLabel("Test email address", { exact: true })
		.fill(`${role}-commercial@example.test`);
	await page
		.getByLabel("Password", { exact: true })
		.fill(process.env.CINENEST_SYNTHETIC_PASSWORD);
	await page.getByRole("button", { name: "Log in", exact: true }).click();
	await page.getByText("Login confirmed.", { exact: false }).waitFor();
}
async function open() {
	await page.goto(new URL("/admin/settings/commercial-policies", base).href);
	await page
		.getByLabel("Exact authorized venue reference", { exact: true })
		.fill(m.fixtureIds.venue);
	await page
		.getByRole("button", { name: "Load policy drafts", exact: true })
		.click();
}
async function clickWrite(name, method, suffix, status) {
	const pending = page.waitForResponse(
		(r) => r.request().method() === method && r.url().endsWith(suffix),
	);
	await page.getByRole("button", { name, exact: true }).click();
	const response = await pending;
    if(method==="POST" && suffix.endsWith("/commercial-policies"))createPayload=response.request().postDataJSON();
	assert.equal(response.status(), status);
	return response.json();
}
async function layout(stage) {
  for (const width of [375, 768, 1280]) {
    await page.setViewportSize({width,height:900});
    const dimensions=await page.evaluate(()=>({viewport:innerWidth,page:document.documentElement.scrollWidth,overflow:[...document.querySelectorAll("body *")].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right,width:e.getBoundingClientRect().width})),fieldsets:[...document.querySelectorAll("fieldset")].map(e=>({width:e.getBoundingClientRect().width,right:e.getBoundingClientRect().right}))}));
    await writeFile(`${run}/${stage}-dimensions-${width}.json`,JSON.stringify(dimensions,null,2));
    assert(dimensions.page<=width+1);assert(dimensions.fieldsets.every(e=>e.right<=width+1));
    await page.screenshot({path:`${run}/${stage}-${width}.png`,fullPage:true});checks.push(`${stage}-responsive${width}`);
  }
}
async function protectedRequest(method,url,data,withCsrf=true,extra={}) {
  const status=await page.evaluate(async ({method,url,data,withCsrf,extra})=>{
    const headers={"content-type":"application/json",...extra};
    if(withCsrf){const csrf=await (await fetch("/api/auth/csrf",{credentials:"same-origin",cache:"no-store"})).json();headers["x-csrf-token"]=csrf.token;}
    const response=await fetch(url,{method,headers,credentials:"same-origin",...(data===undefined?{}:{body:JSON.stringify(data)})});return response.status;
  },{method,url,data,withCsrf,extra});
  return {status:()=>status};
}

try {
	await open();
	await page.getByText("Your session expired.", { exact: false }).waitFor();
	checks.push("anonymous401");
	await login("creator");
	checks.push("genuine-login");
	await open();
	await page.getByText("Draft list loaded.", { exact: false }).waitFor();
	await page.getByRole("button", { name: "New draft", exact: true }).click();
    await layout("editable");
    const optionValues=await page.locator("#policy-venue-select option").evaluateAll(es=>es.map(e=>e.value));
    assert(optionValues.includes(m.fixtureIds.venue));assert(!optionValues.includes(m.fixtureIds.otherVenue));checks.push("venue-selector-isolation");

	await page
		.getByLabel("Draft title", { exact: true })
		.fill(draftTitle);
	await page
		.getByLabel("Proposed effective start (UTC)", { exact: true })
		.fill("2030-01-01T00:00");
	for (const [id, v] of Object.entries({
		taxApplicability: "NOT_APPLICABLE",
		taxType: "NONE",
		taxTreatment: "NONE",
		taxBasis: "NONE",
		taxRounding: "NONE",
		cancellationEligibility: "NOT_ALLOWED",
		noShowTreatment: "NO_REFUND",
		exceptionHandling: "NONE",
		refundEligibility: "NONE",
		refundCalculation: "NONE",
		refundBasis: "NONE",
		refundDestination: "NONE",
	}))
		await page.locator(`#${id}`).selectOption(v);
	await page.locator("#method-BANK").check();
	await page.locator("#channel-ONLINE").check();
	await page.locator("#fee-0-type").selectOption("NONE");
	await page.locator("#fee-0-refundability").selectOption("REFUNDABLE");
	await page
		.getByRole("button", {
			name: "Explicitly select no non-refundable components",
			exact: true,
		})
		.click();
	await page.locator("#policy-submit").check();
	await page
		.locator("#policy-reason")
		.fill("Synthetic E2E, not commercial authority");
	const receipt = await clickWrite(
		"Save draft",
		"POST",
		`/venues/${m.fixtureIds.venue}/commercial-policies`,
		201,
	);
	await page
		.getByText("Policy revision 1 — PENDING_REVIEW", { exact: true })
		.waitFor();
	checks.push("create-submit");
	await page
		.getByRole("button", { name: "Reload current revision", exact: true })
		.click();
	await page
		.getByText("Revision 1 loaded — PENDING_REVIEW.", { exact: true })
		.waitFor();
	await page.locator("#policy-title").fill(`${draftTitle} revised`);
	await clickWrite("Save draft", "PUT", `/${receipt.policyId}`, 200);
	await page
		.getByText("Policy revision 2 — PENDING_REVIEW", { exact: true })
		.waitFor();
	checks.push("revise-reload");
    const policyPath=`/api/admin/venues/${m.fixtureIds.venue}/commercial-policies/${receipt.policyId}`;
    const detail=await page.evaluate(async path=>{const r=await fetch(path,{credentials:"same-origin",cache:"no-store"});if(r.status!==200)throw new Error(`Detail HTTP ${r.status}`);return r.json();},policyPath);
    assert.match(detail.contentHash,/^[0-9a-f]{64}$/);
    const selfReview={idempotencyKey:crypto.randomUUID(),expectedRevision:"2",expectedContentHash:detail.contentHash,domain:"FINANCE",outcome:"APPROVE",reason:"Synthetic negative test",evidenceReference:"SYNTHETIC-NOT-AUTHORITY"};
    assert.equal((await protectedRequest("POST",`${policyPath}/reviews`,selfReview)).status(),403);checks.push("self-review403");
    assert.equal((await protectedRequest("POST",`${policyPath}/reviews`,selfReview,false)).status(),403);checks.push("csrf403");
    assert.equal((await protectedRequest("POST",`/api/admin/venues/${m.fixtureIds.venue}/commercial-policies`,{grantId:m.fixtureIds.creator})).status(),400);checks.push("forged-authority400");

	await page
		.getByRole("button", { name: "View immutable history", exact: true })
		.click();
	await page.getByText("Immutable policy history", { exact: true }).waitFor();
	assert.match(
		await page.locator("pre").innerText(),
		new RegExp(`${draftTitle} revised`),
	);
	checks.push("history");
	for (const role of ["finance", "legal", "operations"]) {
		await login(role);
		await open();
        if(role==="finance") {assert.equal((await protectedRequest("POST",`/api/admin/venues/${m.fixtureIds.venue}/commercial-policies`,{...createPayload,idempotencyKey:crypto.randomUUID()})).status(),403);checks.push("missing-edit-permission403");}

		await page.getByText("Draft list loaded.", { exact: false }).waitFor();
		await page
			.getByRole("button", {
				name: `${draftTitle} revised · Revision 2`,
				exact: true,
			})
			.click();
		await page.locator("#policy-domain").selectOption(role.toUpperCase());
		await page
			.locator("#policy-reason")
			.fill("Synthetic reviewer, not genuine specialist approval");
		await page.locator("#policy-review-reference").fill("SYNTHETIC-ONLY");
		await clickWrite(
			"Record review",
			"POST",
			`/${receipt.policyId}/reviews`,
			200,
		);
		await page
			.getByText(
				`Revision 2 loaded — ${role === "operations" ? "APPROVED" : "PENDING_REVIEW"}.`,
				{ exact: true },
			)
			.waitFor();
		checks.push(`${role}-review`);
	}
	await page
		.getByText("Reviewed draft — not active; commercial issuance disabled.", {
			exact: true,
		})
		.waitFor();
	assert.equal(
		await page.getByRole("button", { name: /^(Publish|Activate)$/ }).count(),
		0,
	);
	checks.push("never-active");
	for (const width of [375, 768, 1280]) {
		await page.setViewportSize({ width, height: 900 });
        await writeFile(`${run}/overflow-${width}.json`, JSON.stringify(await page.evaluate(() => [...document.querySelectorAll("body *")].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>({tag:e.tagName,id:e.id,cls:e.className,width:e.getBoundingClientRect().width,right:e.getBoundingClientRect().right}))),null,2));
        await page.screenshot({path:`${run}/before-responsive-${width}.png`,fullPage:true});
		assert(
			await page.evaluate(
				() => document.documentElement.scrollWidth <= innerWidth + 1,
			),
		);
		await page.screenshot({
			path: `${run}/commercial-${width}.png`,
			fullPage: true,
		});
		checks.push(`responsive${width}`);
	}
	await page.locator("#policy-reason").focus();
	await page.keyboard.press("Tab");
	assert(await page.evaluate(() => document.activeElement !== document.body));
	checks.push("keyboard");
    assert(await page.evaluate(()=>{const e=document.activeElement;const r=e.getBoundingClientRect();const c=getComputedStyle(e);return r.width>0&&r.height>0&&(c.outlineStyle!=="none"||c.boxShadow!=="none");}));checks.push("visible-focus");
    await page.waitForTimeout(125000);
    await page.getByRole("button",{name:"Reload current revision",exact:true}).click();
    await page.getByText("Your session expired.",{exact:false}).waitFor();checks.push("frontend-natural-expiry-no-false-success");

	await login("outsider");
	await open();
	await page
		.getByText("You do not have the required permission", { exact: false })
		.waitFor();
	checks.push("venue403");
	assert.equal(external, 0);
	assert.deepEqual(errors, []);
	await writeFile(
		`${run}/browser-results.json`,
		JSON.stringify(
			{
				pass: checks.length,
				fail: 0,
				checks,
				external,
				errors,
				classification: "SYNTHETIC_E2E_NOT_OWNER_ACCEPTANCE",
			},
			null,
			2,
		),
	);
} catch (e) {
	await writeFile(
		`${run}/browser-results.json`,
		JSON.stringify(
			{
				pass: checks.length,
				fail: 1,
				checks,
				error: String(e.message).slice(0, 1500),
				external,
				errors,
			},
			null,
			2,
		),
	);
	throw e;
} finally {
	await context.close();
	await writeFile(
		`${run}/browser-closed.json`,
		JSON.stringify({ contextClosed: true }),
	);
}
