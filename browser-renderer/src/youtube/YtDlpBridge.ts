// このファイルの責務: YouTube URL を yt-dlp で実再生URLへ解決する。
import { spawn } from "node:child_process";

type CacheEntry = {
	streamUrl: string;
	expiresAt: number;
};

const CACHE_TTL_MS = 60_000;
const EXEC_TIMEOUT_MS = 12_000;
const cache = new Map<string, CacheEntry>();

export const resolveYouTubeUrl = async (
	url: string,
	ytdlpPath = "yt-dlp",
): Promise<string> => {
	const cached = cache.get(url);
	if (cached && cached.expiresAt > Date.now()) {
		return cached.streamUrl;
	}

	return new Promise<string>((resolve, reject) => {
		const proc = spawn(
			ytdlpPath,
			["--get-url", "-f", "best[height<=720]", url],
			{
				stdio: ["ignore", "pipe", "pipe"],
			},
		);

		let out = "";
		let err = "";
		const timeout = setTimeout(() => {
			proc.kill("SIGKILL");
			reject(new Error("yt-dlp timed out"));
		}, EXEC_TIMEOUT_MS);

		proc.stdout.on("data", (d: Buffer) => {
			out += d.toString("utf8");
		});
		proc.stderr.on("data", (d: Buffer) => {
			err += d.toString("utf8");
		});

		proc.on("error", (error) => {
			clearTimeout(timeout);
			reject(error);
		});
		proc.on("close", (code) => {
			clearTimeout(timeout);
			if (code !== 0) {
				reject(new Error(`yt-dlp exited with code ${code}: ${err}`));
				return;
			}
			const streamUrl = out.trim().split("\n")[0];
			if (!streamUrl) {
				reject(new Error("yt-dlp returned empty URL"));
				return;
			}
			cache.set(url, {
				streamUrl,
				expiresAt: Date.now() + CACHE_TTL_MS,
			});
			resolve(streamUrl);
		});
	});
};
