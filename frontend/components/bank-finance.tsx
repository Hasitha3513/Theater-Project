"use client";
import { useRef, useState } from "react";
import { roomRequest, RoomRequestError } from "../lib/rooms";
import { Button, Card, Field, SelectField } from "./primitives";
const operations = ["Inspect credits", "Inspect exceptions", "Verify fixture", "Match credit", "Allocate funds", "Propose correction", "Approve correction", "Assign exception", "Handover exception", "Escalate exception"] as const;
type Operation = typeof operations[number];
export function financialCommand(op: Operation, v: string, target: string, request: string, credit: string, amount: string, revision: string, reason: string, assigned: string, fixture: string, key: string) {
 const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
 if(!uuid.test(v) || !reason.trim())throw new Error("Valid venue and reason required");
 const root=`/api/admin/venues/${v}/bank`;
 if(op==="Inspect credits"||op==="Inspect exceptions") return {path:root+(op==="Inspect credits"?"/credits":"/exceptions"),init:undefined};
 if(!/^\d+$/.test(revision)||!Number.isSafeInteger(Number(revision)))throw new Error("Exact revision required");
 const body:Record<string,unknown>={expectedRevision:Number(revision),reason,commandKey:key};let suffix="";
 if(op==="Verify fixture"){body.fixtureKey=fixture;suffix="/credits";}
 else {
 if(!uuid.test(target))throw new Error("Valid target identifier required");
 switch(op){
 case "Match credit":if(!uuid.test(request))throw new Error("Valid request required");body.requestId=request;suffix=`/credits/${target}/matches`;break;
 case "Allocate funds":if(!uuid.test(credit)||!/^\d+$/.test(amount)||Number(amount)<=0||!Number.isSafeInteger(Number(amount)))throw new Error("Valid credit and positive exact minor units required");body.creditId=credit;body.amountMinor=Number(amount);suffix=`/requests/${target}/allocations`;break;
 case "Propose correction":body.sourceKey=`SYNTHETIC-REVERSAL-${target}`;suffix=`/credits/${target}/corrections`;break;
 case "Approve correction":suffix=`/corrections/${target}/decisions`;break;
 default:body.action=op==="Assign exception"?"ASSIGN":op==="Handover exception"?"HANDOVER":"ESCALATE";if(body.action!=="ESCALATE"){if(!uuid.test(assigned))throw new Error("Valid assignee required");body.assignedTo=assigned;}suffix=`/exceptions/${target}/actions`;
 }
 }
 return {path:root+suffix,init:{method:"POST",body:JSON.stringify(body)}};
}
export function BankFinance(){
 const [venue,setVenue]=useState(""),[op,setOp]=useState<Operation>("Inspect credits"),[target,setTarget]=useState(""),[request,setRequest]=useState(""),[credit,setCredit]=useState(""),[amount,setAmount]=useState(""),[revision,setRevision]=useState("0"),[reason,setReason]=useState(""),[assigned,setAssigned]=useState(""),[fixture,setFixture]=useState("SYNTHETIC-BANK-100"),[busy,setBusy]=useState(false),[error,setError]=useState(""),[result,setResult]=useState<unknown>(null);
 const context=useRef(0);const command=useRef<{fingerprint:string;key:string}|null>(null);
 function clear(){context.current++;setResult(null);setError("");setTarget("");setRequest("");setCredit("");setAmount("");setReason("");setAssigned("");setRevision("0");command.current=null;}
 async function execute(){const epoch=context.current;setBusy(true);setError("");setResult(null);try{const fingerprint=JSON.stringify([venue,op,target,request,credit,amount,revision,reason,assigned,fixture]);if(command.current?.fingerprint!==fingerprint)command.current={fingerprint,key:crypto.randomUUID()};const c=financialCommand(op,venue,target,request,credit,amount,revision,reason,assigned,fixture,command.current.key);const response=await roomRequest(c.path,c.init);if(context.current===epoch)setResult(response);}catch(e){if(context.current===epoch){setResult(null);if(e instanceof RoomRequestError && [401,403,404].includes(e.status)){clear();setVenue("");setBusy(false);}setError(e instanceof Error?e.message:"Financial operation unavailable");}}finally{if(context.current===epoch)setBusy(false);}}
 return <Card title="Synthetic credit and reconciliation"><p><strong>SYNTHETIC TEST — NO REAL MONEY</strong></p><p>Backend permissions apply independently to every operation. Verification, allocation and confirmation are separate. No refund or commercial payment.</p>
 <Field id="finance-venue" label="Finance venue ID" value={venue} onChange={e=>{clear();setVenue(e.target.value);setBusy(false);}}/>
 <SelectField id="finance-operation" label="Financial operation" value={op} onChange={e=>{clear();setOp(e.target.value as Operation);setBusy(false);}}>{operations.map(v=><option key={v}>{v}</option>)}</SelectField>
 {op==="Verify fixture"?<SelectField id="finance-fixture" label="Independent synthetic source fixture" value={fixture} onChange={e=>setFixture(e.target.value)}>{["SYNTHETIC-BANK-100","SYNTHETIC-BANK-200","SYNTHETIC-BANK-10000","SYNTHETIC-OVERLAP-100","SYNTHETIC-GATEWAY-10000","SYNTHETIC-BANK-50000","SYNTHETIC-GATEWAY-50000"].map(v=><option key={v}>{v}</option>)}</SelectField>:null}
 {!op.startsWith("Inspect")&&op!=="Verify fixture"?<Field id="finance-target" label="Target credit, request, exception or correction ID" value={target} onChange={e=>setTarget(e.target.value)}/>:null}
 {op==="Match credit"?<Field id="finance-request" label="Request to match" value={request} onChange={e=>setRequest(e.target.value)}/>:null}
 {op==="Allocate funds"?<><Field id="finance-credit" label="Verified credit ID" value={credit} onChange={e=>setCredit(e.target.value)}/><Field id="finance-amount" label="Allocation LKR minor units" inputMode="numeric" value={amount} onChange={e=>setAmount(e.target.value)}/></>:null}
 {op==="Assign exception"||op==="Handover exception"?<Field id="finance-assignee" label="Eligible assignee ID" value={assigned} onChange={e=>setAssigned(e.target.value)}/>:null}
 <Field id="finance-revision" label="Expected current revision" inputMode="numeric" value={revision} onChange={e=>setRevision(e.target.value)}/><Field id="finance-reason" label="Financial review reason" value={reason} onChange={e=>setReason(e.target.value)}/>
 <Button busy={busy} onClick={execute}>Run selected synthetic operation</Button><Button variant="secondary" onClick={()=>{clear();setTarget("");setRequest("");setCredit("");setReason("");setBusy(false);}}>Clear private context</Button>
 {error?<p role="alert">{error}</p>:null}<div aria-live="polite">{result===null?<p>No financial result loaded.</p>:Array.isArray(result)&&result.length===0?<p>No matching financial records.</p>:<FinancialResult value={result}/>}</div></Card>;
}

function FinancialResult({value}:{value:unknown}){const rows=Array.isArray(value)?value:[value];return <section aria-label="Authoritative synthetic financial result">{rows.map((row,index)=><dl className="account-summary" key={String((row as Record<string,unknown>).id ?? (row as Record<string,unknown>).exception_id ?? index)}>{Object.entries(row as Record<string,unknown>).map(([key,v])=><div key={key}><dt>{key.replace(/([A-Z])/g," $1").replaceAll("_"," ")}</dt><dd>{typeof v === "object" ? JSON.stringify(v) : String(v)}</dd></div>)}</dl>)}</section>;}
