// このファイルの責務: browser-renderer 全体で利用するログ出力を統一する。
export const logger = {
	info: (message: string, ...meta: unknown[]): void => {
		console.log(`[INFO] ${new Date().toISOString()} ${message}`, ...meta);
	},
	warn: (message: string, ...meta: unknown[]): void => {
		console.warn(`[WARN] ${new Date().toISOString()} ${message}`, ...meta);
	},
	error: (message: string, ...meta: unknown[]): void => {
		console.error(`[ERROR] ${new Date().toISOString()} ${message}`, ...meta);
	},
	debug: (message: string, ...meta: unknown[]): void => {
		if (process.env.MAPBROWSER_DEBUG === "1") {
			console.debug(`[DEBUG] ${new Date().toISOString()} ${message}`, ...meta);
		}
	},
};
