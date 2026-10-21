// Intercepted synthetic browser evidence; does not prove backend authorization or real E2E.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {createServer} from 'node:net';
import {mkdir} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {venue,id,other,detailRecord} from './staff-bookings-fixtures.mjs';
import {rosterFixture} from './staff-guest-roster-fixtures.mjs';
import {refundFixture} from './staff-booking-refunds-fixtures.mjs';
const {chromium}=await import(pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href);
const socket=createServer();socket.listen(0,'127.0.0.1');await once(socket,'listening');const port=socket.address().port;await new Promise(r=>socket.close(r));
const base=`http://127.0.0.1:${port}`,path=`/admin/bookings/venues/${venue}/${id}`,api=`/api/admin/venues/${venue}/bookings/${id}/refunds`;
const child=spawn(process.execPath,['node_modules/next/dist/bin/next','start','--hostname','127.0.0.1','--port',String(port)],{stdio:'ignore'});
let browser,groups=0;
try{
 for(let n=0;n<100;n++){try{if((await fetch(base)).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({headless:true});await mkdir('../.cache/staff-booking-refunds/after',{recursive:true});
 async function harness(width=1280){
  const page=await browser.newPage({viewport:{width,height:1000}}),state={status:200,total:2,data:null,delay:0,network:false,reads:[],writes:[]};
  await page.route('**/api/**',async route=>{
   const req=route.request(),u=new URL(req.url());if(req.method()!=='GET')state.writes.push(u.pathname);
   if(u.pathname.endsWith('/refunds')){
    state.reads.push(u.pathname+u.search);const status=state.status,data=structuredClone(state.data??refundFixture(Number(u.searchParams.get('page')),Number(u.searchParams.get('size')),state.total)),delay=state.delay;
    if(state.network)return route.abort('failed');if(delay)await new Promise(r=>setTimeout(r,delay));try{return await route.fulfill({status,json:status===200?data:{message:'PRIVATE_PROVIDER_ERROR'}});}catch{return;}
   }
   if(u.pathname.endsWith('/guest-roster'))return route.fulfill({json:rosterFixture()});
   if(u.pathname.includes('/bookings/'))return route.fulfill({json:detailRecord()});
   return route.fulfill({status:401,json:{message:'Session unavailable'}});
  });return {page,state};
 }
 const result=p=>p.getByRole('region',{name:'Protected booking refund results'}),reload=p=>p.getByRole('button',{name:'Reload refund obligations',exact:true});
 async function open(page){await page.goto(base+path);await page.getByRole('button',{name:'View refund obligations',exact:true}).click();await result(page).waitFor();}
 for(const width of [375,768,1280]){
  const {page,state}=await harness(width);await page.goto(base+path);const view=page.getByRole('button',{name:'View refund obligations',exact:true});await view.waitFor();assert.equal(state.reads.length,0);
  await view.focus();await view.press('Enter');await result(page).waitFor();assert.equal(await page.getByRole('button',{name:'Hide refund obligations',exact:true}).evaluate(e=>e===document.activeElement),true);
  await reload(page).focus();await reload(page).press('Enter');await result(page).waitFor();assert.equal(await reload(page).evaluate(e=>e===document.activeElement),true);
  assert.equal(await page.locator('main').count(),1);assert.equal(await page.locator('body').evaluate(e=>e.scrollWidth<=innerWidth),true);assert.deepEqual(state.writes,[]);
  assert(state.reads.every(p=>p===api+'?page=0&size=10'));assert.equal(new URL(page.url()).search,'');
  const text=await page.locator('main').innerText();assert(text.includes('NO REAL REPAYMENT'));assert(text.includes('Approved does not mean repaid'));assert(text.includes('does not permit a retry or resend'));
  await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:`../.cache/staff-booking-refunds/after/booking-${width}.png`,fullPage:true});await page.close();groups++;
 }
 for(const status of [401,403,404,409,429,503]){
  const {page,state}=await harness();await open(page);state.status=status;await reload(page).click();await page.locator('#booking-refund-results').getByRole('alert').waitFor();assert.equal(await result(page).count(),0);assert(!(await page.content()).includes('PRIVATE_PROVIDER_ERROR'));
  if(status===403)assert((await page.locator('#booking-refund-results').innerText()).includes('booking-read authority alone is insufficient'));
  state.status=200;await reload(page).click();await result(page).waitFor();assert.deepEqual(state.writes,[]);await page.close();groups++;
 }
 {
  const {page,state}=await harness();state.total=106;await open(page);let count=10;
  for(let n=1;n<=10;n++){await page.getByRole('button',{name:'Next refund page',exact:true}).click();await result(page).getByText(`Page ${n+1}. Current records may change between reads.`,{exact:true}).waitFor();count+=await result(page).getByRole('article').count();}
  assert.equal(count,106);assert.equal(await page.getByRole('button',{name:'Next refund page',exact:true}).isDisabled(),true);await page.getByRole('button',{name:'Previous refund page',exact:true}).click();await result(page).getByText('Page 10. Current records may change between reads.',{exact:true}).waitFor();assert.deepEqual(state.writes,[]);await page.close();groups++;
 }
 {
  const {page,state}=await harness();state.total=0;await open(page);assert((await result(page).innerText()).includes('This does not establish eligibility'));assert.equal(await result(page).getByRole('article').count(),0);await page.close();groups++;
 }
 {
  const {page,state}=await harness();state.data=refundFixture();state.data.items[0].state='SIMULATED_CONFIRMED';state.data.items[0].resolution='COMPLETE';state.data.secret='PRIVATE_SECRET';state.data.items[0].destinationReference='PRIVATE_SECRET';state.data.items[0].attempts[0].hash='PRIVATE_SECRET';await open(page);
  assert(!(await page.content()).includes('PRIVATE_SECRET'));assert((await result(page).innerText()).includes('No real repayment is established'));await result(page).getByText('Recorded attempts (1)',{exact:true}).first().click();assert((await result(page).innerText()).includes('RECORDED, VERIFIED'));assert.equal(await result(page).getByRole('button').count(),0);assert.deepEqual(state.writes,[]);await page.close();groups++;
 }
 {
  const {page,state}=await harness();await open(page);state.delay=600;await reload(page).click();assert.equal(await result(page).count(),0);await page.evaluate(()=>dispatchEvent(new Event('cinenest-session-change')));await page.locator('main').getByRole('alert').first().waitFor();await page.waitForTimeout(750);assert.equal(await result(page).count(),0);assert.deepEqual(state.writes,[]);await page.close();groups++;
 }
 {
  const {page,state}=await harness();await open(page);state.delay=600;await reload(page).click();await page.goto(base+path.replace(venue,other));await page.waitForTimeout(750);assert.equal(await result(page).count(),0);assert.deepEqual(state.writes,[]);await page.close();groups++;
 }
 {
  const {page,state}=await harness();await open(page);state.network=true;await reload(page).click();await page.locator('#booking-refund-results').getByRole('alert').waitFor();assert.equal(await result(page).count(),0);state.network=false;await reload(page).click();await result(page).waitFor();await page.reload();await page.getByRole('button',{name:'View refund obligations',exact:true}).waitFor();assert.equal(await result(page).count(),0);assert.deepEqual(state.writes,[]);await page.close();groups++;
 }
 {
  const {page,state}=await harness();await open(page);state.data={...refundFixture(),bookingRevision:2};await reload(page).click();await page.locator('#booking-refund-results').getByRole('alert').waitFor();assert.equal(await result(page).count(),0);assert((await page.locator('#booking-refund-results').innerText()).includes('Reload booking detail'));await page.close();groups++;
 }
 console.log(`PASS ${groups} intercepted booking/refund browser and visual groups;not real backend E2E`);
}finally{if(browser)await browser.close();if(child.exitCode===null){child.kill('SIGTERM');await once(child,'exit');}}
