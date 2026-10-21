import assert from 'node:assert/strict';
import test from 'node:test';
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {bookingAccessContext,bookingAccessHref,staffBookingAccessRecord,loadStaffBookingAccess} from '../lib/staff-booking-access.ts';
import {StaffBookingAccessSummary} from '../components/staff-booking-access.tsx';
import {bankRoute,forwardBank} from '../lib/bank-backend.ts';
import {accessFixture} from './staff-booking-access-fixtures.mjs';
import {venue,id,other} from './staff-bookings-fixtures.mjs';
const context={venue,booking:id};
test('exact opaque context navigation validates all route values',()=>{
 assert.equal(bookingAccessHref(venue,id),`/admin/access?venue=${venue}&booking=${id}`);assert.deepEqual(bookingAccessContext(context),context);assert.equal(bookingAccessContext({}),undefined);
 for(const query of [{venue},{booking:id},{venue,booking:'bad'},{venue:[venue],booking:id},{...context,token:'private'},{...context,returnTo:'https://evil.invalid'}])assert.throws(()=>bookingAccessContext(query));
 assert.throws(()=>bookingAccessHref(venue,'bad'));
});
test('exact protected booking and venue binding reject mismatches and false physical states',()=>{
 for(const patch of [{bookingId:other},{venueId:other},{synthetic:false},{doorControlled:true},{bookingRevision:0},{state:'ISSUED'},{permittedActions:['REVEAL']},{permittedActions:['REVOKE','REVOKE']},{sessionEnd:accessFixture().sessionStart},{observedAt:'bad'},{timezone:'not/a/timezone'}])assert.throws(()=>staffBookingAccessRecord({...accessFixture(),...patch},context));
});
test('no credential requires explicit NOT_ISSUED null metadata and zero actions',()=>{
 const r=staffBookingAccessRecord(accessFixture(false),context);assert.equal(r.state,'NOT_ISSUED');assert.equal(r.credential,null);
 for(const patch of [{state:'ACTIVE'},{credential:{}},{permittedActions:['REVOKE']}])assert.throws(()=>staffBookingAccessRecord({...accessFixture(false),...patch},context));
});
test('credential metadata is projected and strictly bound; provisioning is independent',()=>{
 const raw=accessFixture();raw.token='PRIVATE';raw.financial='PRIVATE';raw.credential.secret='PRIVATE';raw.credential.provisioning='UNKNOWN';
 const r=staffBookingAccessRecord(raw,context);assert.equal(r.credential.provisioning,'UNKNOWN');assert.doesNotMatch(JSON.stringify(r),/PRIVATE|token|secret|financial/);
 for(const patch of [{id:'bad'},{version:0},{version:Number.MAX_SAFE_INTEGER+1},{bookingRevision:2},{provisioning:'READY'},{operationId:'bad'}])assert.throws(()=>staffBookingAccessRecord({...accessFixture(),credential:{...accessFixture().credential,...patch}},context));
});
test('semantic presentation renders authoritative states without command controls',()=>{
 for(const state of ['SCHEDULED','ACTIVE','CONSUMED','EXPIRED','REVOKED','SUPERSEDED','NOT_ISSUED']){
  const raw=accessFixture(state!=='NOT_ISSUED');raw.state=state;const html=renderToStaticMarkup(React.createElement(StaffBookingAccessSummary,{value:staffBookingAccessRecord(raw,context)}));assert.match(html,new RegExp(state));assert.match(html,/SYNTHETIC ONLY/);assert.doesNotMatch(html,/<button|<form|<input/);
 }
 const raw=accessFixture();raw.credential.provisioning='UNKNOWN';assert.match(renderToStaticMarkup(React.createElement(StaffBookingAccessSummary,{value:staffBookingAccessRecord(raw,context)})),/Provisioning is uncertain/);
});
test('transport reloads exact protected GET with no mutation or CSRF request',async()=>{
 const previous=global.fetch,calls=[];try{global.fetch=async(path,init)=>{calls.push([path,init]);return Response.json(accessFixture());};await loadStaffBookingAccess(context);assert.equal(calls.length,1);assert.equal(calls[0][0],`/api/admin/venues/${venue}/bookings/${id}/access`);assert.equal(calls[0][1].cache,'no-store');assert.equal(calls[0][1].method,undefined);}finally{global.fetch=previous;}
});
test('denials errors and wrong-owner/scope responses never yield protected context',async()=>{
 const previous=global.fetch;try{
  for(const status of [401,403,404,409,429,503]){global.fetch=async()=>Response.json({message:'PRIVATE'},{status});await assert.rejects(loadStaffBookingAccess(context),e=>e.status===status);}
  global.fetch=async()=>Response.json({...accessFixture(),bookingId:other});await assert.rejects(loadStaffBookingAccess(context));
 }finally{global.fetch=previous;}
});
test('proxy allows only exact GET route and refuses business query or mutation bypass',async()=>{
 const path=`/api/admin/venues/${venue}/bookings/${id}/access`;
 assert.equal(bankRoute(path,'GET'),true);for(const m of ['POST','PUT','DELETE'])assert.equal(bankRoute(path,m),false);
 assert.equal((await forwardBank(new Request('http://localhost'+path+'?token=private'))).status,404);
 assert.equal((await forwardBank(new Request('http://localhost'+path,{method:'POST'}))).status,404);
});
