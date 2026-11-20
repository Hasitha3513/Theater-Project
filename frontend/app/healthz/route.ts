// Process liveness only. It never certifies database, financial or physical readiness.
export const dynamic = "force-dynamic";
export function GET() {
	return Response.json({ status: "UP" }, { headers: { "Cache-Control": "no-store" } });
}
