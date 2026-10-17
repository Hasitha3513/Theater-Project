import test from "node:test";
import assert from "node:assert/strict";
import { safeAdminRows } from "../components/admin-reads.tsx";
import { adminRoute, forwardAdmin } from "../lib/admin-backend.ts";
const id="00000000-0000-4000-8000-000000000001";
test("admin transport allows only four scoped read shapes",()=>{for(const kind of ["accounts","audit"]){assert.equal(adminRoute(`/api/admin/venues/${id}/${kind}`),true);assert.equal(adminRoute(`/api/admin/venues/${id}/${kind}/${id}`),true);}assert.equal(adminRoute(`/api/admin/venues/${id}/audit/export`),false);});
test("private unknown fields never reach admin rendering",()=>{assert.deepEqual(safeAdminRows([{accountId:id,eligibility:"SUSPENDED",issuer:"PRIVATE",sessionToken:"PRIVATE",amount:99,evidence:"PRIVATE"}]),[{accountId:id,eligibility:"SUSPENDED"}]);assert.throws(()=>safeAdminRows({}));});
test("admin forwarding cannot mutate and errors are not cached",async()=>{const response=await forwardAdmin(new Request(`https://local/api/admin/venues/${id}/accounts`,{method:"POST"}));assert.equal(response.status,404);assert.equal(response.headers.get("cache-control"),"no-store");});
