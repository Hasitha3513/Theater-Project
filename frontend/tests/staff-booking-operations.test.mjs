import assert from 'node:assert/strict';
import test from 'node:test';
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {staffBookingOperationsRecord,loadStaffBookingOperations} from '../lib/staff-booking-operations.ts';
import {StaffOperationsSummary} from '../components/staff-booking-operations.tsx';
import {bankRoute,forwardBank} from '../lib/bank-backend.ts';
import {item,venue,id,other} from './staff-bookings-fixtures.mjs';
import {operationsFixture} from './staff-booking-operations-fixtures.mjs';
const parse=(raw,b=item(),page=0)=>staffBookingOperationsRecord(raw,b,page);
test('exact current booking,venue,room,purchased window and revision required',()=>{
 assert.equal(parse(operationsFixture()).bookingId,id);
 for(const patch of [{bookingId:other},{venueId:other},{currentRoomId:id},{currentBookingRevision:2},{currentSessionStart:'2026-10-21T05:00:00Z'},{currentSessionEnd:'2026-10-21T08:00:00Z'},{timezone:'UTC'}])assert.throws(()=>parse({...operationsFixture(),...patch}),e=>e.status===409);
});
test('malformed DTOs,unsupported states and false readiness rejected',()=>{
 for(const patch of [{synthetic:false},{occupancy:'VACANT'},{readiness:'READY'},{inventoryChanged:true},{observedAt:'bad'},{hasNext:'false'},{page:1},{pageSize:10},{items:null}])assert.throws(()=>parse({...operationsFixture(),...patch}));
 for(const patch of [{caseId:'bad'},{roomId:'bad'},{capturedBookingRevision:0},{capturedBookingRevision:2},{caseRevision:-1},{existingCaseState:'READY'},{assignmentStatus:'STAFF'},{existingCaseState:'RESOLVED',assignmentStatus:'UNASSIGNED'},{createdAt:'bad'},{updatedAt:'2026-10-20T10:00:00Z'},{capturedSessionEnd:'2026-10-21T04:00:00Z'},{recordedExceptionCategories:['SECRET']},{recordedExceptionCategories:['CLEANING','CLEANING']}]){const f=operationsFixture();Object.assign(f.items[0],patch);assert.throws(()=>parse(f));}
});
test('project only allowlisted fields,no actors,private evidence,device credentials or command authority',()=>{
 const f=operationsFixture();f.secret='PRIVATE';f.items[0].assigneeId='PRIVATE';f.items[0].entries=[{actor:'PRIVATE',evidence:'PRIVATE'}];f.items[0].deviceCredential='PRIVATE';f.items[0].permittedActions=['resolve'];const r=parse(f);assert.doesNotMatch(JSON.stringify(r),/PRIVATE|assignee|entries|Credential|permittedActions/);assert.doesNotMatch(renderToStaticMarkup(React.createElement(StaffOperationsSummary,{value:r})),/PRIVATE|<button|<form|<input/);
});
test('older captured revision retains actual historical window and room,not substituted current fields',()=>{
 const b={...item(),revision:2},f={...operationsFixture(),currentBookingRevision:2};f.items[0].capturedSessionStart='2026-10-20T04:30:00Z';f.items[0].capturedSessionEnd='2026-10-20T07:30:00Z';f.items[0].roomId=id;
 const r=parse(f,b);assert.equal(r.items[0].roomId,id);assert.equal(r.currentRoomId,other);const html=renderToStaticMarkup(React.createElement(StaffOperationsSummary,{value:r}));assert.match(html,/Historical revision/);assert.match(html,/2026-10-20T04:30:00Z/);assert.match(html,/historical records, not current unresolved/);
});
test('no attribution by room or time: consume only exact endpoint,empty is not readiness',()=>{
 const html=renderToStaticMarkup(React.createElement(StaffOperationsSummary,{value:parse(operationsFixture(0,0))}));assert.match(html,/No explicitly booking-linked operations cases recorded/);assert.match(html,/does not mean the room is ready/);assert.match(html,/room-wide maintenance/);assert.doesNotMatch(html,/<article/);
});
test('fixed25 and hasNext complete pages,duplicate IDs and current-context contradictions rejected',()=>{
 assert.equal(parse(operationsFixture(4,106),item(),4).items.length,6);
 for(const f of [operationsFixture(0,26),operationsFixture()]){f.hasNext=true;if(f.items.length===25)f.items.pop();assert.throws(()=>parse(f));}
 const f=operationsFixture();f.items.push(f.items[0]);assert.throws(()=>parse(f));
 for(const patch of [{roomId:id},{capturedSessionStart:'2026-10-21T05:00:00Z'}]){const f=operationsFixture();Object.assign(f.items[0],patch);assert.throws(()=>parse(f));}
});
test('bounded exact authenticated GET,no-store,signal,no credentials/CSRF/mutations',async()=>{
 const old=global.fetch,calls=[],c=new AbortController();try{global.fetch=async(path,init)=>{calls.push([path,init]);return Response.json(operationsFixture(4,106));};const r=await loadStaffBookingOperations(item(),4,c.signal);assert.equal(r.hasNext,false);assert.equal(calls.length,1);assert.equal(calls[0][0],`/api/admin/venues/${venue}/bookings/${id}/operations/cases?page=4`);assert.equal(calls[0][1].cache,'no-store');assert.equal(calls[0][1].signal,c.signal);assert.equal(calls[0][1].method,undefined);for(const p of [-1,100001,1.5])await assert.rejects(loadStaffBookingOperations(item(),p));assert.equal(calls.length,1);}finally{global.fetch=old;}
});
test('session,grant revocation,wrong venue,missing,conflict,rate-limit,backend denial clear authority',async()=>{
 const old=global.fetch;try{for(const status of [401,403,404,409,429,503]){global.fetch=async()=>Response.json({message:'PRIVATE'},{status});await assert.rejects(loadStaffBookingOperations(item()),e=>e.status===status);}}finally{global.fetch=old;}
});
test('GET-only proxy rejects unknown,duplicate,size,sensitive and invalid page queries;old venue queue remains unchanged',async()=>{
 const path=`/api/admin/venues/${venue}/bookings/${id}/operations/cases`;assert.equal(bankRoute(path,'GET'),true);for(const m of ['POST','PUT','PATCH','DELETE'])assert.equal(bankRoute(path,m),false);
 for(const q of ['?page=0&page=1','?page=-1','?page=100001','?page=1.5','?size=25','?token=secret','?page=','?page=1&secret=private'])assert.equal((await forwardBank(new Request('http://localhost'+path+q))).status,404);
 assert.equal((await forwardBank(new Request(`http://localhost/api/admin/venues/${venue}/operations/cases?page=0`))).status,404);
});
test('proxy forwards only exact page and session cookie,no client authorities or other cookies',async()=>{
 const old=global.fetch,origin=process.env.CINENEST_AUTH_ORIGIN;process.env.CINENEST_AUTH_ORIGIN='http://127.0.0.1:9876';const calls=[];try{global.fetch=async(url,init)=>{calls.push([String(url),init]);return Response.json(operationsFixture());};const path=`/api/admin/venues/${venue}/bookings/${id}/operations/cases?page=0`;const r=await forwardBank(new Request('http://localhost'+path,{headers:{cookie:'other=private; CINENEST_SESSION=synthetic-only','x-roles':'admin'}}));assert.equal(r.status,200);assert.match(r.headers.get('cache-control'),/no-store/);assert.equal(calls[0][0],'http://127.0.0.1:9876'+path);assert.deepEqual([...calls[0][1].headers],[['cookie','CINENEST_SESSION=synthetic-only']]);assert.equal(calls[0][1].method,'GET');assert.equal(calls[0][1].redirect,'manual');}finally{global.fetch=old;if(origin===undefined)delete process.env.CINENEST_AUTH_ORIGIN;else process.env.CINENEST_AUTH_ORIGIN=origin;}
});
