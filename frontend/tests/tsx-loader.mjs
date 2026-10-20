import { registerHooks } from "node:module";
import { readFileSync, existsSync } from "node:fs";
import ts from "typescript";
registerHooks({
	resolve(specifier, context, next) {
		if (specifier === "next/link" || specifier === "next/image")
			return next(`${specifier}.js`, context);
		if (specifier.startsWith(".") && context.parentURL) {
			const base = new URL(specifier, context.parentURL);
			for (const extension of [".ts", ".tsx"])
				if (existsSync(new URL(base.href + extension)))
					return next(base.href + extension, context);
		}
		return next(specifier, context);
	},
	load(url, context, next) {
		// Match Next's CommonJS default interop while testing the real Image component.
		if (url.endsWith("/node_modules/next/image.js"))
			return {
				format: "module",
				shortCircuit: true,
				source:
					'import imageModule from "./dist/shared/lib/image-external.js"; export default imageModule.default; export const getImageProps = imageModule.getImageProps;',
			};

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
