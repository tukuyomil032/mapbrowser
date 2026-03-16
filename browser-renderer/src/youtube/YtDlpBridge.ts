// このファイルの責務: YouTube URL を yt-dlp で実再生URLへ解決する。
import { spawn } from "node:child_process";

export const resolveYouTubeUrl = async (
	url: string,
	ytdlpPath = "yt-dlp",
): Promise<string> => {
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

		proc.stdout.on("data", (d: Buffer) => {
			out += d.toString("utf8");
		});
		proc.stderr.on("data", (d: Buffer) => {
			err += d.toString("utf8");
		});

		proc.on("error", (error) => reject(error));
		proc.on("close", (code) => {
			if (code !== 0) {
				reject(new Error(`yt-dlp exited with code ${code}: ${err}`));
				return;
			}
			const streamUrl = out.trim().split("\n")[0];
			if (!streamUrl) {
				reject(new Error("yt-dlp returned empty URL"));
				return;
			}
			resolve(streamUrl);
		});
	});
};
