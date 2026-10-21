import test from 'node:test';
import assert from 'node:assert/strict';
import {forwardBank,bankRoute} from '../lib/bank-backend.ts';
const venue='00000000-0000-4000-8000-000000000001',booking='00000000-0000-4000-8000-000000000002',path=`/api/admin/venues/${venue}/operations/cases`;
async function upstream(run){const original=global.fetch,origin=process.env.CINENEST_AUTH_ORIGIN,calls=[];process.env.CINENEST_AUTH_ORIGIN='http://127.0.0.1:9876';try{await run(calls,fn=>{global.fetch=async(url,init)=>{calls.push([String(url),init]);return fn(url,init);};});}finally{global.fetch=original;if(origin===undefined)delete process.env.CINENEST_AUTH_ORIGIN;else process.env.CINENEST_AUTH_ORIGIN=origin;}}
const request=(q='',init)=>new Request('http://localhost'+path+q,init);
test('venue queue page0,later and boundary forward exactly,fixed25,no invented hasNext',()=>upstream(async(calls,set)=>{
 set(url=>Response.json({items:[],page:Number(new URL(url).searchParams.get('page')??0),size:25}));
 for(const q of ['','?page=0','?page=1','?page=100000']){const r=await forwardBank(request(q));assert.equal(r.status,200);assert.deepEqual(await r.json(),{items:[],page:Number(new URL('http://local'+q).searchParams.get('page')??0),size:25});assert.equal(calls.at(-1)[0],'http://127.0.0.1:9876'+path+q);assert.equal(calls.at(-1)[1].method,'GET');assert.equal(calls.at(-1)[1].cache,'no-store');assert.match(r.headers.get('cache-control'),/no-store/);}assert.equal(calls.length,4);
}));
test('invalid,negative,non-numeric,duplicate,unknown,size and sensitive queries reject before upstream',()=>upstream(async(calls,set)=>{
 set(()=>{throw Error('must not forward');});for(const q of ['?page=-1','?page=100001','?page=1.5','?page=abc','?page=','?page=0&page=1','?size=25','?page=0&venue=x','?token=private','?page=%2B1','?Page=0','?page=1000000'])assert.equal((await forwardBank(request(q))).status,404);assert.equal(calls.length,0);
}));
test('only exact queue GET gains page support,detail,POST and unrelated routes remain strict',()=>upstream(async(calls,set)=>{
 set(()=>Response.json({items:[],page:0,size:25}));for(const p of [path+'/'+booking+'?page=0',`/api/admin/venues/${venue}/bank/requests?page=0`])assert.equal((await forwardBank(new Request('http://localhost'+p))).status,404);assert.equal((await forwardBank(request('?page=0',{method:'POST'}))).status,404);assert.equal(calls.length,0);assert.equal(bankRoute(path,'GET'),true);assert.equal(bankRoute(path,'POST'),true);
}));
test('missing/expired session,missing/revoked/wrong venue authority and unavailable preserve sanitized statuses',()=>upstream(async(calls,set)=>{
 for(const status of [401,403,404,409,429,503]){set(()=>Response.json({message:'PRIVATE_DETAIL',actor:'PRIVATE'},{status}));const r=await forwardBank(request('?page=0'));assert.equal(r.status,status);assert.doesNotMatch(JSON.stringify(await r.json()),/PRIVATE/);assert.equal(calls.at(-1)[1].headers.get('Cookie'),null);}set(()=>{throw Error('PRIVATE_DEPENDENCY');});const r=await forwardBank(request('?page=0'));assert.equal(r.status,502);assert.equal(await r.text(),'');
}));
test('only session cookie forwards,client roles/scope headers confer no authority',()=>upstream(async(calls,set)=>{
 set(()=>Response.json({items:[],page:0,size:25}));assert.equal((await forwardBank(request('?page=0',{headers:{cookie:'CINENEST_SESSION=synthetic; OTHER=private','x-roles':'admin','x-venue':venue}}))).status,200);assert.deepEqual([...calls[0][1].headers],[['cookie','CINENEST_SESSION=synthetic']]);assert.equal(calls[0][1].redirect,'manual');
}));
test('booking-scoped operations fixed-page contract remains independent',()=>upstream(async(calls,set)=>{
 set(()=>Response.json({synthetic:true}));const p=`/api/admin/venues/${venue}/bookings/${booking}/operations/cases`;assert.equal((await forwardBank(new Request('http://localhost'+p+'?page=3'))).status,200);assert.equal(calls[0][0],'http://127.0.0.1:9876'+p+'?page=3');assert.equal((await forwardBank(new Request('http://localhost'+p+'?size=25'))).status,404);
}));
