import assert from "node:assert/strict";
import { test } from "node:test";
import {
	safeCustomerNavigation,
	safeLoopbackResource,
} from "./safe-navigation.mjs";
const origin = "http://127.0.0.1:3000";
const id = "11111111-1111-4111-8111-111111111111";
test("existing evidence navigation carries only an opaque request reference", () => {
	assert.equal(safeCustomerNavigation(`/bank?evidence=${id}`, origin), true);
});
test("malformed negative navigation stays invalid independently of following positive navigation", () => {
	assert.equal(
		safeCustomerNavigation("/bank?evidence=malformed", origin),
		false,
	);
	assert.equal(safeCustomerNavigation(`/bank?request=${id}`, origin), true);
});
test("supported customer routes admit only their existing reference contracts", () => {
	for (const path of [
		"/",
		"/account",
		"/rooms",
		`/rooms/${id}`,
		"/bookings",
		`/bookings/${id}`,
		`/verification?quote=${id}`,
		`/bank?quote=${id}`,
		`/bank?request=${id}`,
	])
		assert.equal(safeCustomerNavigation(path, origin), true);
});
test("sensitive and unknown query keys, including encoded variants, fail closed", () => {
	for (const key of [
		"hash",
		"contentHash",
		"token",
		"secret",
		"amount",
		"csrf",
		"%74oken",
		"identity",
		"receipt",
		"other",
	])
		assert.equal(safeCustomerNavigation(`/bank?${key}=${id}`, origin), false);
});
test("evidence payloads, hashes and non-reference values cannot masquerade as references", () => {
	for (const value of [
		"malformed",
		"a".repeat(64),
		"data%3Aimage%2Fpng%3Bbase64%2Ctest",
		"https%3A%2F%2Fexample.test%2Fprivate",
	])
		assert.equal(
			safeCustomerNavigation(`/bank?evidence=${value}`, origin),
			false,
		);
});
test("ambiguous duplicate/mixed keys and wrong-route context fail closed", () => {
	for (const path of [
		`/bank?request=${id}&request=${id}`,
		`/bank?request=${id}&evidence=${id}`,
		`/rooms?quote=${id}`,
		`/bookings/${id}?quote=${id}`,
		`/verification?request=${id}`,
	])
		assert.equal(safeCustomerNavigation(path, origin), false);
});
test("external redirects, URL credentials, fragments and unknown paths are rejected", () => {
	for (const path of [
		"https://example.test/bank",
		"http://user:pass@127.0.0.1:3000/bank",
		`/bank?request=${id}#private`,
		"/unknown",
		"/bookings/not-a-reference",
		"http://[",
	])
		assert.equal(safeCustomerNavigation(path, origin), false);
});

test("private preview permits only same-frontend opaque blob resources, preserving network restrictions", () => {
	assert.equal(safeLoopbackResource(`blob:${origin}/${id}`, origin), true);
	for (const value of [
		`${origin}/api/accounts/me`,
		`http://localhost:8080/api/session`,
	])
		assert.equal(safeLoopbackResource(value, origin), true);
	for (const value of [
		`blob:https://example.test/${id}`,
		`blob:http://127.0.0.1:9999/${id}`,
		`blob:${origin}/not-a-reference`,
		"data:image/png;base64,synthetic",
		"file:///tmp/synthetic",
		"https://example.test/api",
		"http://user:pass@localhost/api",
		"malformed",
	])
		assert.equal(safeLoopbackResource(value, origin), false);
});
