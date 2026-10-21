import assert from 'node:assert/strict';
import test from 'node:test';
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {staffBookingRefundRecord,loadStaffBookingRefunds} from '../lib/staff-booking-refunds.ts';
import {StaffRefundSummary} from '../components/staff-booking-refunds.tsx';
import {bankRoute,forwardBank} from '../lib/bank-backend.ts';
import {item,venue,id,other} from './staff-bookings-fixtures.mjs';
import {refundFixture} from './staff-booking-refunds-fixtures.mjs';
const parse=raw=>staffBookingRefundRecord(raw,item(),0,10);
test('exact authoritative booking,venue,purchased revision and synthetic binding',()=>{
 assert.equal(parse(refundFixture()).bookingId,id);
 for(const patch of [{bookingId:other},{venueId:other},{bookingRevision:2},{bookingRevision:0},{synthetic:false},{realRepayment:true},{originatingRequestId:'bad'},{observedAt:'bad'},{hasNext:'false'},{page:1},{size:25}])assert.throws(()=>parse({...refundFixture(),...patch}));
});
test('exact large minor amounts avoid floating point and private fields are dropped recursively',()=>{
 const f=refundFixture();f.secret='PRIVATE';f.items[0].destinationReference='PRIVATE';f.items[0].actor='PRIVATE';f.items[0].availableMinor='PRIVATE';f.items[0].attempts[0].hash='PRIVATE';const r=parse(f);
 assert.equal(r.items[0].amountMinor,'123456789012345');assert.doesNotMatch(JSON.stringify(r),/PRIVATE|secret|destinationReference|actor|availableMinor|hash/);
 const html=renderToStaticMarkup(React.createElement(StaffRefundSummary,{value:r}));assert.match(html,/LKR 1234567890123.45/);assert.doesNotMatch(html,/PRIVATE/);
});
test('invalid amounts,unsupported states and fabricated success are refused',()=>{
 for(const patch of [{amountMinor:'0'},{amountMinor:'-1'},{amountMinor:'1.5'},{amountMinor:1},{amountMinor:'9223372036854775808'},{currency:'USD'},{state:'REPAID'},{resolution:'SUCCESS'},{destinationVerified:'true'},{revision:-1}]){const f=refundFixture();Object.assign(f.items[0],patch);assert.throws(()=>parse(f));}
});
test('attempt references,sequences,evidence phases and duplicate records are validated',()=>{
 for(const patch of [{id:'bad'},{sequence:0},{evidencePhases:['APPROVED']},{evidencePhases:['RECORDED','RECORDED']}]){const f=refundFixture();Object.assign(f.items[0].attempts[0],patch);assert.throws(()=>parse(f));}
 const f=refundFixture();f.items.push(f.items[0]);assert.throws(()=>parse(f));
});
test('empty means no recorded obligations,not zero eligibility or completed repayment',()=>{
 const html=renderToStaticMarkup(React.createElement(StaffRefundSummary,{value:parse(refundFixture(0,10,0))}));assert.match(html,/No recorded refund obligations/);assert.match(html,/does not establish eligibility/);assert.doesNotMatch(html,/<form|<input/);
});
test('approval,unknown and simulated completion remain distinct with zero command controls',()=>{
 for(const [state,copy] of [['APPROVED','Approved does not mean repaid'],['UNKNOWN_RECONCILIATION_REQUIRED','does not permit a retry or resend'],['SIMULATED_CONFIRMED','No real repayment is established']]){const f=refundFixture();f.items[0].state=state;const html=renderToStaticMarkup(React.createElement(StaffRefundSummary,{value:parse(f)}));assert.match(html,new RegExp(copy));assert.doesNotMatch(html,/<button|<form|<input/);assert.match(html,/Recorded attempts/);}
});
test('page validation and exact GET transport support records beyond100 without business commands',async()=>{
 const old=global.fetch,calls=[];try{global.fetch=async(path,init)=>{calls.push([path,init]);return Response.json(refundFixture(10,10,106));};const p=await loadStaffBookingRefunds(item(),10,10);assert.equal(p.items.length,6);assert.equal(p.hasNext,false);assert.equal(calls[0][0],`/api/admin/venues/${venue}/bookings/${id}/refunds?page=10&size=10`);assert.equal(calls[0][1].cache,'no-store');assert.equal(calls[0][1].method,undefined);assert.equal(calls.length,1);
 for(const [page,size] of [[-1,10],[10001,10],[0,0],[0,26]])await assert.rejects(loadStaffBookingRefunds(item(),page,size));assert.equal(calls.length,1);
 }finally{global.fetch=old;}
});
test('session,permission,wrong venue,missing,conflict,rate-limit and backend denial do not return context',async()=>{
 const old=global.fetch;try{for(const status of [401,403,404,409,429,503]){global.fetch=async()=>Response.json({message:'PRIVATE'},{status});await assert.rejects(loadStaffBookingRefunds(item()),e=>e.status===status);}}finally{global.fetch=old;}
});
test('proxy is exact GET only and rejects sensitive,duplicate or out-of-range query values',async()=>{
 const path=`/api/admin/venues/${venue}/bookings/${id}/refunds`;assert.equal(bankRoute(path,'GET'),true);for(const method of ['POST','PUT','DELETE'])assert.equal(bankRoute(path,method),false);
 for(const q of ['?token=private','?page=0&page=1','?size=26','?page=10001','?size=0','?page=-1','?secret=private'])assert.equal((await forwardBank(new Request('http://localhost'+path+q))).status,404);
});
