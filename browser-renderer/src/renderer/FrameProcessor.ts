// このファイルの責務: PNGフレームを量子化し、FRAME/DELTA_FRAMEとして送信する。
import path from "node:path";
import os from "node:os";
import sharp from "sharp";
import Piscina from "piscina";

import { logger } from "../util/logger.js";

export type ProcessResult =
	| {
			type: "FRAME";
			data: Uint8Array;
			width: number;
			height: number;
	  }
	| {
			type: "DELTA_BATCH";
			updates: Array<{
				data: Uint8Array;
				x: number;
				y: number;
				w: number;
				h: number;
			}>;
	  }
	| {
			type: "DELTA_FRAME";
			data: Uint8Array;
			x: number;
			y: number;
			w: number;
			h: number;
	  }
	| {
			type: "SKIP";
	  };

export class FrameProcessor {
	private static readonly STILL_DIFF_THRESHOLD = FrameProcessor.envInt(
		"MAPBROWSER_STILL_DIFF_THRESHOLD",
		5,
		1,
		30,
	);
	private static readonly VIDEO_DIFF_THRESHOLD = FrameProcessor.envInt(
		"MAPBROWSER_VIDEO_DIFF_THRESHOLD",
		8,
		1,
		30,
	);
	private static readonly STILL_TILE_CHANGED_THRESHOLD = FrameProcessor.envInt(
		"MAPBROWSER_STILL_TILE_CHANGED_THRESHOLD",
		5,
		1,
		256,
	);
	private static readonly VIDEO_TILE_CHANGED_THRESHOLD = FrameProcessor.envInt(
		"MAPBROWSER_VIDEO_TILE_CHANGED_THRESHOLD",
		10,
		1,
		256,
	);
	private static readonly STILL_SKIP_RATIO = FrameProcessor.envFloat(
		"MAPBROWSER_STILL_SKIP_RATIO",
		0.005,
		0,
		1,
	);
	private static readonly VIDEO_SKIP_RATIO = FrameProcessor.envFloat(
		"MAPBROWSER_VIDEO_SKIP_RATIO",
		0.02,
		0,
		1,
	);
	private readonly pool: Piscina;
	private prevColorData: Uint8Array | null = null;
	private prevLumaData: Uint8Array | null = null;
	private videoMode = false;
	private static readonly TILE_SIZE = FrameProcessor.envInt(
		"MAPBROWSER_TILE_SIZE",
		16,
		4,
		64,
	);
	private static readonly MAX_DELTA_TILES = FrameProcessor.envInt(
		"MAPBROWSER_MAX_DELTA_TILES",
		24,
		1,
		256,
	);
	private static readonly MAX_CHANGED_TILE_RATIO = FrameProcessor.envFloat(
		"MAPBROWSER_MAX_CHANGED_TILE_RATIO",
		0.65,
		0.05,
		1,
	);

	public constructor() {
		this.pool = new Piscina({
			filename: path.resolve(__dirname, "quantize.worker.js"),
			minThreads: 1,
			maxThreads: Math.max(2, Math.min(8, os.cpus().length)),
		});
	}

	public setVideoMode(videoMode: boolean): void {
		this.videoMode = videoMode;
	}

	public async process(
		pngBuffer: Buffer,
		screenWMaps: number,
		screenHMaps: number,
	): Promise<ProcessResult> {
		const config = this.currentDiffConfig();
		const width = screenWMaps * 128;
		const height = screenHMaps * 128;

		const rgbBuffer = await sharp(pngBuffer)
			.resize(width, height, { fit: "fill" })
			.removeAlpha()
			.raw()
			.toBuffer();

		const colorData = await this.pool.run({
			rgbBuffer: new Uint8Array(rgbBuffer),
			width,
			height,
		});

		if (!(colorData instanceof Uint8Array)) {
			throw new Error("Worker returned invalid color data");
		}

		const lumaData = this.buildLumaData(rgbBuffer, width, height);
		const filteredLuma = this.applyBoxBlur3x3(lumaData, width, height);

		if (
			this.prevColorData === null ||
			this.prevColorData.length !== colorData.length ||
			this.prevLumaData === null ||
			this.prevLumaData.length !== filteredLuma.length
		) {
			this.prevColorData = colorData;
			this.prevLumaData = filteredLuma;
			return {
				type: "FRAME",
				data: colorData,
				width: screenWMaps,
				height: screenHMaps,
			};
		}

		const prevColorData = this.prevColorData;
		const prevLumaData = this.prevLumaData;
		const tileSize = FrameProcessor.TILE_SIZE;
		const changedTiles: Array<{ x: number; y: number; changed: number }> = [];
		let changedPixels = 0;

		for (let ty = 0; ty < height; ty += tileSize) {
			for (let tx = 0; tx < width; tx += tileSize) {
				let tileChanged = 0;
				const maxY = Math.min(height, ty + tileSize);
				const maxX = Math.min(width, tx + tileSize);
				for (let y = ty; y < maxY; y++) {
					const rowOffset = y * width;
					for (let x = tx; x < maxX; x++) {
						const i = rowOffset + x;
						const lumaDiff = Math.abs(filteredLuma[i] - prevLumaData[i]);
						if (lumaDiff < config.diffThreshold) {
							continue;
						}
						if (colorData[i] === prevColorData[i]) {
							continue;
						}
						tileChanged++;
						changedPixels++;
					}
				}

				if (tileChanged > config.tileChangedThreshold) {
					changedTiles.push({ x: tx, y: ty, changed: tileChanged });
				}
			}
		}

		if (changedTiles.length === 0) {
			return { type: "SKIP" };
		}

		const totalPixels = width * height;
		const changeRatio = changedPixels / Math.max(1, totalPixels);
		if (changeRatio < config.skipRatio) {
			return { type: "SKIP" };
		}

		const totalTileCount =
			Math.ceil(width / tileSize) * Math.ceil(height / tileSize);
		const changedTileRatio = changedTiles.length / totalTileCount;

		// Changed area is too broad. Use full frame for predictable cost.
		if (
			changedTiles.length > FrameProcessor.MAX_DELTA_TILES ||
			changedTileRatio >= FrameProcessor.MAX_CHANGED_TILE_RATIO
		) {
			this.prevColorData = colorData;
			this.prevLumaData = filteredLuma;
			logger.debug(
				`Fallback to full frame: changedTiles=${changedTiles.length}/${totalTileCount} changedPixels=${changedPixels}/${totalPixels}`,
			);
			return {
				type: "FRAME",
				data: colorData,
				width: screenWMaps,
				height: screenHMaps,
			};
		}

		const centerX = width / 2;
		const centerY = height / 2;
		changedTiles.sort((a, b) => {
			const acx = a.x + tileSize / 2;
			const acy = a.y + tileSize / 2;
			const bcx = b.x + tileSize / 2;
			const bcy = b.y + tileSize / 2;
			const ap = Math.hypot(acx - centerX, acy - centerY);
			const bp = Math.hypot(bcx - centerX, bcy - centerY);
			if (ap !== bp) {
				return ap - bp;
			}
			return b.changed - a.changed;
		});

		const selectedTiles = changedTiles.slice(0, FrameProcessor.MAX_DELTA_TILES);
		const mergedTiles = this.mergeAdjacentTiles(selectedTiles, width, height);
		const mergedUpdates = mergedTiles.map((tile) => {
			const delta = new Uint8Array(tile.w * tile.h);
			let offset = 0;
			for (let y = tile.y; y < tile.y + tile.h; y++) {
				const rowOffset = y * width;
				for (let x = tile.x; x < tile.x + tile.w; x++) {
					delta[offset++] = colorData[rowOffset + x] ?? 0;
				}
			}
			return {
				data: delta,
				x: tile.x,
				y: tile.y,
				w: tile.w,
				h: tile.h,
			};
		});

		this.prevColorData = colorData;
		this.prevLumaData = filteredLuma;
		logger.debug(
			`Delta batch: tiles=${mergedUpdates.length} changedPixels=${changedPixels}/${totalPixels} ratio=${changeRatio.toFixed(4)} mode=${this.videoMode ? "video" : "still"}`,
		);

		if (mergedUpdates.length === 1) {
			const update = mergedUpdates[0];
			return {
				type: "DELTA_FRAME",
				data: update.data,
				x: update.x,
				y: update.y,
				w: update.w,
				h: update.h,
			};
		}

		return {
			type: "DELTA_BATCH",
			updates: mergedUpdates,
		};
	}

	private currentDiffConfig(): {
		diffThreshold: number;
		tileChangedThreshold: number;
		skipRatio: number;
	} {
		if (this.videoMode) {
			return {
				diffThreshold: FrameProcessor.VIDEO_DIFF_THRESHOLD,
				tileChangedThreshold: FrameProcessor.VIDEO_TILE_CHANGED_THRESHOLD,
				skipRatio: FrameProcessor.VIDEO_SKIP_RATIO,
			};
		}
		return {
			diffThreshold: FrameProcessor.STILL_DIFF_THRESHOLD,
			tileChangedThreshold: FrameProcessor.STILL_TILE_CHANGED_THRESHOLD,
			skipRatio: FrameProcessor.STILL_SKIP_RATIO,
		};
	}

	private mergeAdjacentTiles(
		tiles: Array<{ x: number; y: number; changed: number }>,
		width: number,
		height: number,
	): Array<{ x: number; y: number; w: number; h: number }> {
		if (tiles.length <= 1) {
			return tiles.map((tile) => ({
				x: tile.x,
				y: tile.y,
				w: Math.min(FrameProcessor.TILE_SIZE, width - tile.x),
				h: Math.min(FrameProcessor.TILE_SIZE, height - tile.y),
			}));
		}

		const sorted = [...tiles].sort((a, b) =>
			a.y === b.y ? a.x - b.x : a.y - b.y,
		);
		const merged: Array<{ x: number; y: number; w: number; h: number }> = [];

		for (const tile of sorted) {
			const tw = Math.min(FrameProcessor.TILE_SIZE, width - tile.x);
			const th = Math.min(FrameProcessor.TILE_SIZE, height - tile.y);
			const last = merged[merged.length - 1];
			if (
				last &&
				last.y === tile.y &&
				last.h === th &&
				last.x + last.w === tile.x &&
				last.w + tw <= FrameProcessor.TILE_SIZE * 4
			) {
				last.w += tw;
				continue;
			}
			merged.push({ x: tile.x, y: tile.y, w: tw, h: th });
		}

		return merged;
	}

	private buildLumaData(
		rgbBuffer: Buffer,
		width: number,
		height: number,
	): Uint8Array {
		const out = new Uint8Array(width * height);
		for (let i = 0, p = 0; i < out.length; i++, p += 3) {
			const r = rgbBuffer[p] ?? 0;
			const g = rgbBuffer[p + 1] ?? 0;
			const b = rgbBuffer[p + 2] ?? 0;
			out[i] = (77 * r + 150 * g + 29 * b) >> 8;
		}
		return out;
	}

	private applyBoxBlur3x3(
		luma: Uint8Array,
		width: number,
		height: number,
	): Uint8Array {
		if (width < 3 || height < 3) {
			return luma;
		}

		const out = new Uint8Array(luma);
		for (let y = 1; y < height - 1; y++) {
			for (let x = 1; x < width - 1; x++) {
				let sum = 0;
				for (let dy = -1; dy <= 1; dy++) {
					const rowOffset = (y + dy) * width;
					for (let dx = -1; dx <= 1; dx++) {
						sum += luma[rowOffset + x + dx] ?? 0;
					}
				}
				out[y * width + x] = Math.floor(sum / 9);
			}
		}
		return out;
	}

	private static envInt(
		name: string,
		fallback: number,
		min: number,
		max: number,
	): number {
		const raw = process.env[name];
		if (!raw) {
			return fallback;
		}
		const parsed = Number.parseInt(raw, 10);
		if (!Number.isFinite(parsed)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, parsed));
	}

	private static envFloat(
		name: string,
		fallback: number,
		min: number,
		max: number,
	): number {
		const raw = process.env[name];
		if (!raw) {
			return fallback;
		}
		const parsed = Number.parseFloat(raw);
		if (!Number.isFinite(parsed)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, parsed));
	}
}
