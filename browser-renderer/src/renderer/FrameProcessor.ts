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
	private readonly pool: Piscina;
	private prevColorData: Uint8Array | null = null;
	private prevLumaData: Uint8Array | null = null;
	private static readonly TILE_SIZE = 16;
	private static readonly LUMA_THRESHOLD = 5;
	private static readonly TILE_CHANGED_THRESHOLD = 10;
	private static readonly FRAME_SKIP_CHANGED_PIXELS = 30;
	private static readonly MAX_DELTA_TILES = 24;
	private static readonly MAX_CHANGED_TILE_RATIO = 0.65;

	public constructor() {
		this.pool = new Piscina({
			filename: path.resolve(__dirname, "quantize.worker.js"),
			minThreads: 1,
			maxThreads: Math.max(2, Math.min(8, os.cpus().length)),
		});
	}

	public async process(
		pngBuffer: Buffer,
		screenWMaps: number,
		screenHMaps: number,
	): Promise<ProcessResult> {
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

		if (
			this.prevColorData === null ||
			this.prevColorData.length !== colorData.length ||
			this.prevLumaData === null ||
			this.prevLumaData.length !== lumaData.length
		) {
			this.prevColorData = colorData;
			this.prevLumaData = lumaData;
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
						const lumaDiff = Math.abs(lumaData[i] - prevLumaData[i]);
						if (lumaDiff < FrameProcessor.LUMA_THRESHOLD) {
							continue;
						}
						if (colorData[i] === prevColorData[i]) {
							continue;
						}
						tileChanged++;
						changedPixels++;
					}
				}

				if (tileChanged > FrameProcessor.TILE_CHANGED_THRESHOLD) {
					changedTiles.push({ x: tx, y: ty, changed: tileChanged });
				}
			}
		}

		if (changedTiles.length === 0) {
			return { type: "SKIP" };
		}

		if (changedPixels < FrameProcessor.FRAME_SKIP_CHANGED_PIXELS) {
			return { type: "SKIP" };
		}

		const totalPixels = width * height;
		const totalTileCount =
			Math.ceil(width / tileSize) * Math.ceil(height / tileSize);
		const changedTileRatio = changedTiles.length / totalTileCount;

		// Changed area is too broad. Use full frame for predictable cost.
		if (
			changedTiles.length > FrameProcessor.MAX_DELTA_TILES ||
			changedTileRatio >= FrameProcessor.MAX_CHANGED_TILE_RATIO
		) {
			this.prevColorData = colorData;
			this.prevLumaData = lumaData;
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
		const updates = selectedTiles.map((tile) => {
			const w = Math.min(tileSize, width - tile.x);
			const h = Math.min(tileSize, height - tile.y);
			const delta = new Uint8Array(w * h);
			let offset = 0;
			for (let y = tile.y; y < tile.y + h; y++) {
				const rowOffset = y * width;
				for (let x = tile.x; x < tile.x + w; x++) {
					delta[offset++] = colorData[rowOffset + x] ?? 0;
				}
			}
			return {
				data: delta,
				x: tile.x,
				y: tile.y,
				w,
				h,
			};
		});

		this.prevColorData = colorData;
		this.prevLumaData = lumaData;
		logger.debug(
			`Delta batch: tiles=${updates.length} changedPixels=${changedPixels}/${totalPixels}`,
		);

		if (updates.length === 1) {
			const update = updates[0];
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
			updates,
		};
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
}
