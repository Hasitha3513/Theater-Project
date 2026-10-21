// Intercepted synthetic UI tests; not backend authorization or real E2E evidence.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdir } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { venue, id, other, detailRecord } from "./staff-bookings-fixtures.mjs";
import { accessFixture } from "./staff-booking-access-fixtures.mjs";
import { rosterFixture } from "./staff-guest-roster-fixtures.mjs";
const { chromium } = await import(
	pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href
);
const socket = createServer();
socket.listen(0, "127.0.0.1");
await once(socket, "listening");
const port = socket.address().port;
await new Promise((r) => socket.close(r));
const base = `http://127.0.0.1:${port}`,
	mode = process.env.CINENEST_ACCESS_VISUAL === "before" ? "before" : "after",
	screenshots = `../.cache/staff-booking-access/${mode}`;
const child = spawn(
	process.execPath,
	[
		"node_modules/next/dist/bin/next",
		"start",
		"--hostname",
		"127.0.0.1",
		"--port",
		String(port),
	],
	{ stdio: "ignore" },
);
let browser,
	groups = 0;
try {
	for (let n = 0; n < 100; n++) {
		try {
			if ((await fetch(base)).ok) break;
		} catch {}
		await new Promise((r) => setTimeout(r, 100));
	}
	browser = await chromium.launch({ headless: true });
	await mkdir(screenshots, { recursive: true });

 async function harness(width=1280){
  const page=await browser.newPage({viewport:{width,height:1000}}),state={status:200,data:accessFixture(),delay:0,reads:[],writes:[],network:false};
  await page.route("**/api/**",async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;if(req.method()!=="GET")state.writes.push(path);
   if(path.endsWith("/access")){
    state.reads.push(path);const status=state.status,data=structuredClone(state.data),delay=state.delay;
    if(state.network)return route.abort("failed");if(delay)await new Promise(r=>setTimeout(r,delay));
    try{return await route.fulfill({status,json:status===200?data:{message:"PRIVATE_ERROR_NOT_FOR_UI"}});}catch{return;}
   }
   if(path.endsWith("/guest-roster"))return route.fulfill({json:rosterFixture()});
   if(path.includes("/bookings/"))return route.fulfill({json:detailRecord()});
   return route.fulfill({status:401,json:{message:"Session required"}});
  });return {page,state};
 }
 const detail=`/admin/bookings/venues/${venue}/${id}?page=0&size=10`,href=`/admin/access?venue=${venue}&booking=${id}`;
 const loaded=page=>page.getByRole("region",{name:"Protected booking access status"}).waitFor(),reload=page=>page.getByRole("button",{name:"Reload booking access",exact:true});
 for(const width of [375,768,1280]){
  const {page,state}=await harness(width);await page.goto(base+detail);await page.getByRole("table",{name:"Registered synthetic guests"}).waitFor();await page.evaluate(()=>scrollTo(0,0));console.log("capture",width,await page.locator("body").evaluate(e=>({width:e.scrollWidth,height:e.scrollHeight})));
  if(mode==="after"){
   const link=page.getByRole("link",{name:"Manage Cinema Access",exact:true});assert.equal(await link.getAttribute("href"),href);await link.focus();assert.equal(await link.evaluate(e=>getComputedStyle(e).color),await page.locator(":root").evaluate(e=>getComputedStyle(e).getPropertyValue("--cn-color-surface").trim()==="#FFFFFF"?"rgb(255, 255, 255)":getComputedStyle(document.querySelector(".card")).backgroundColor));await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:`${screenshots}/booking-${width}.png`,fullPage:true});await link.press("Enter");await loaded(page);assert.equal(new URL(page.url()).pathname+new URL(page.url()).search,href);
  }else{await page.screenshot({path:`${screenshots}/booking-${width}.png`,fullPage:true});await page.goto(base+href);await page.getByLabel("Venue ID",{exact:true}).waitFor();}
  if(mode==="after"){
   await reload(page).focus();await reload(page).press("Enter");await loaded(page);assert.equal(await reload(page).evaluate(e=>e===document.activeElement),true);
   assert.deepEqual(state.writes,[]);assert.equal(await page.locator("main").count(),1);assert.equal(await page.locator("body").evaluate(e=>e.scrollWidth<=innerWidth),true);
   assert.equal(await page.getByRole("button",{name:/issue|reveal|revoke|simulate|unlock/i}).count(),0);assert(state.reads.every(p=>p===`/api/admin/venues/${venue}/bookings/${id}/access`));
  }
  await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:`${screenshots}/access-${width}.png`,fullPage:true});await page.close();groups++;
 }
 if(mode==="after"){
  for(const name of ["NOT_ISSUED","ACTIVE","REVOKED","EXPIRED","CONSUMED","SUPERSEDED","UNKNOWN"]){
   const {page,state}=await harness();state.data=accessFixture(name!=="NOT_ISSUED");if(name==="UNKNOWN")state.data.credential.provisioning="UNKNOWN";else state.data.state=name;
   await page.goto(base+href);await loaded(page);assert((await page.locator("main").innerText()).includes(name));assert.deepEqual(state.writes,[]);await page.close();groups++;
  }
  for(const status of [401,403,404,409,429,503]){
   const {page,state}=await harness();await page.goto(base+href);await loaded(page);state.status=status;await reload(page).click();await page.locator("main").getByRole("alert").waitFor();assert.equal(await page.getByRole("region",{name:"Protected booking access status"}).count(),0);assert(!(await page.locator("main").innerText()).includes("PRIVATE_ERROR_NOT_FOR_UI"));state.status=200;await reload(page).click();await loaded(page);assert.deepEqual(state.writes,[]);await page.close();groups++;
  }
  {
   const {page,state}=await harness();state.data.secret="PRIVATE_SECRET";state.data.credential.token="PRIVATE_SECRET";await page.goto(base+href);await loaded(page);assert(!(await page.content()).includes("PRIVATE_SECRET"));state.delay=600;await reload(page).click();assert.equal(await page.getByRole("region",{name:"Protected booking access status"}).count(),0);await page.evaluate(()=>dispatchEvent(new Event("cinenest-session-change")));await page.locator("main").getByRole("alert").waitFor();await page.waitForTimeout(750);assert.equal(await page.getByRole("region",{name:"Protected booking access status"}).count(),0);assert.deepEqual(state.writes,[]);await page.close();groups++;
  }
  {
   const {page,state}=await harness();state.delay=600;await page.goto(base+href);await page.getByRole("status").first().waitFor();state.status=403;await page.goto(`${base}/admin/access?venue=${other}&booking=${id}`);await page.locator("main").getByRole("alert").waitFor();await page.waitForTimeout(750);assert.equal(await page.getByRole("region",{name:"Protected booking access status"}).count(),0);assert.deepEqual(state.writes,[]);await page.close();groups++;
  }
  {
   const {page,state}=await harness();await page.goto(base+"/admin/access?venue=invalid&booking="+id);await page.locator("main").getByRole("alert").waitFor();assert.deepEqual(state.reads,[]);assert.deepEqual(state.writes,[]);await page.close();groups++;
  }
  {
   const {page,state}=await harness();state.network=true;await page.goto(base+href);await page.locator("main").getByRole("alert").waitFor();state.network=false;await reload(page).click();await loaded(page);await page.reload();await loaded(page);await page.getByRole("link",{name:"Return to this booking",exact:true}).click();await page.getByRole("table",{name:"Registered synthetic guests"}).waitFor();await page.goBack();await loaded(page);await page.goForward();await page.getByRole("table",{name:"Registered synthetic guests"}).waitFor();assert.deepEqual(state.writes,[]);await page.close();groups++;
  }
 }
 console.log(`PASS ${groups} ${mode} intercepted booking-access browser/visual groups;not backend E2E`);
} finally {
	if (browser) await browser.close();
	if (child.exitCode === null) {
		child.kill("SIGTERM");
		await once(child, "exit");
	}
}
