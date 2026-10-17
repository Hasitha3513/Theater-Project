import test from "node:test";
import assert from "node:assert/strict";
import { financialCommand } from "../components/bank-finance.tsx";
import { bankRoute } from "../lib/bank-backend.ts";
const id="00000000-0000-4000-8000-000000000001";
test("finance transport restricts reviewed routes and methods",()=>{assert.equal(bankRoute(`/api/admin/venues/${id}/bank/credits`,"POST"),true);assert.equal(bankRoute(`/api/admin/venues/${id}/bank/credits/${id}/matches`,"POST"),true);assert.equal(bankRoute(`/api/bookings/${id}`,"POST"),false);assert.equal(bankRoute(`/api/admin/venues/${id}/bank/refunds`,"POST"),false);});
test("allocation request carries exact revision and independent command key",()=>{const c=financialCommand("Allocate funds",id,id,"",id,"100","2","SYNTHETIC reason","","",id);assert.equal(c.path,`/api/admin/venues/${id}/bank/requests/${id}/allocations`);assert.deepEqual(JSON.parse(c.init.body),{expectedRevision:2,reason:"SYNTHETIC reason",commandKey:id,creditId:id,amountMinor:100});});
test("unsafe monetary input never becomes a financial command",()=>{for(const amount of ["-1","0","1.1","9007199254740992","Infinity"])assert.throws(()=>financialCommand("Allocate funds",id,id,"",id,amount,"2","SYNTHETIC reason","","",id));});
test("credit admission cannot supply amount currency or customer reference",()=>{const c=financialCommand("Verify fixture",id,"","","","","0","SYNTHETIC reason","","SYNTHETIC-BANK-100",id);assert.deepEqual(Object.keys(JSON.parse(c.init.body)).sort(),["commandKey","expectedRevision","fixtureKey","reason"]);});
