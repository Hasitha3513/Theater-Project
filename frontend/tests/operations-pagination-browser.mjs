// Browser -> real Next proxy -> local synthetic HTTP fixture. Not Spring/PostgreSQL E2E.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {createServer} from 'node:http';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL(`${process.env.PLAYWRIGHT_MODULE_PATH}/index.mjs`).href);
const venue='00000000-0000-4000-8000-000000000001',room='00000000-0000-4000-8000-000000000002',path=`/api/admin/venues/${venue}/operations/cases`,calls=[];
let code=200,network=false;
const upstream=createServer((req,res)=>{calls.push({url:req.url,method:req.method,cookie:req.headers.cookie});if(network){req.socket.destroy();return;}res.setHeader('content-type','application/json');res.statusCode=code;if(code!==200){res.end(JSON.stringify({message:'PRIVATE_ERROR'}));return;}const page=Number(new URL(req.url,'http://local').searchParams.get('page')??0);res.end(JSON.stringify({items:page===0?Array.from({length:25},(_,i)=>({case_id:`00000000-0000-4000-8000-${String(10+i).padStart(12,'0')}`,venue_id:venue,room_id:room,state:'OPEN',revision:0,synthetic:true,checklist_version:'SYNTHETIC_V1',checklist:['SURFACES'],assignee_id:null,assignment:'UNASSIGNED',physicalOccupancy:'UNKNOWN',physicalReadiness:'UNVERIFIED',inventoryChanged:false,entries:[],permittedActions:['read']})):[],page,size:25}));});
upstream.listen(0,'127.0.0.1');await once(upstream,'listening');const origin=`http://127.0.0.1:${upstream.address().port}`;
const reserve=createServer();reserve.listen(0,'127.0.0.1');await once(reserve,'listening');const port=reserve.address().port;await new Promise(r=>reserve.close(r));const base=`http://127.0.0.1:${port}`;
const child=spawn(process.execPath,['node_modules/next/dist/bin/next','start','--hostname','127.0.0.1','--port',String(port)],{env:{...process.env,CINENEST_AUTH_ORIGIN:origin},stdio:'ignore'});let browser,groups=0;
try{
 for(let n=0;n<100;n++){try{if((await fetch(base)).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({headless:true});
 for(const width of [375,768,1280]){const page=await browser.newPage({viewport:{width,height:1000}});await page.goto(base+'/admin/operations');await page.getByLabel('Venue ID',{exact:true}).fill(venue);await page.getByRole('button',{name:'Load queue',exact:true}).click();await page.getByRole('button',{name:/OPEN — UNASSIGNED/}).first().waitFor();assert.equal(await page.getByRole('button',{name:/OPEN — UNASSIGNED/}).count(),25);await page.getByRole('button',{name:'Next',exact:true}).click();await page.getByText('No cases loaded.',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Next',exact:true}).isDisabled(),true);assert((await page.locator('main').innerText()).includes('Physical readiness not verified'));await page.getByRole('button',{name:'Previous',exact:true}).click();await page.getByRole('button',{name:/OPEN — UNASSIGNED/}).first().waitFor();assert.equal(await page.locator('main').count(),1);assert.equal(await page.locator('body').evaluate(e=>e.scrollWidth<=innerWidth),true);await page.close();groups++;}
 const page=await browser.newPage();await page.goto(base);
 for(const status of [401,403,404,409,429,503]){code=status;const r=await page.evaluate(async p=>{const r=await fetch(p,{cache:'no-store'});return {status:r.status,text:await r.text()};},path+'?page=0');assert.equal(r.status,status);assert(!r.text.includes('PRIVATE_ERROR'));groups++;}
 code=200;for(const query of ['?page=-1','?page=100001','?page=x','?page=0&page=1','?size=25','?secret=private']){const count=calls.length;assert.equal(await page.evaluate(async p=>(await fetch(p)).status,path+query),404);assert.equal(calls.length,count);}groups++;
 network=true;assert.equal(await page.evaluate(async p=>(await fetch(p)).status,path+'?page=0'),502);network=false;groups++;
 assert(calls.every(c=>c.method==='GET'));assert(calls.some(c=>c.url===path+'?page=1'));assert(calls.every(c=>c.cookie===undefined));console.log(`PASS ${groups} browser/real Next proxy/local synthetic upstream groups;zero mutation requests;NOT real-backend E2E`);
}finally{if(browser)await browser.close();if(child.exitCode===null){child.kill('SIGTERM');await once(child,'exit');}await new Promise(r=>upstream.close(r));}
