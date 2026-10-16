import { registerHooks } from "node:module";
import { readFileSync, existsSync } from "node:fs";
import ts from "typescript";
registerHooks({
	resolve(specifier, context, next) {
		if (specifier.startsWith(".") && context.parentURL) {
			const base = new URL(specifier, context.parentURL);
			for (const extension of [".ts", ".tsx"])
				if (existsSync(new URL(base.href + extension)))
					return next(base.href + extension, context);
		}
		return next(specifier, context);
	},
	load(url, context, next) {
		if (/\.tsx?$/.test(url))
			return {
				format: "module",
				shortCircuit: true,
				source: ts.transpileModule(readFileSync(new URL(url), "utf8"), {
					compilerOptions: {
						jsx: ts.JsxEmit.ReactJSX,
						module: ts.ModuleKind.ESNext,
						target: ts.ScriptTarget.ES2022,
					},
				}).outputText,
			};
		return next(url, context);
	},
});
