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
	private static readonly MAX_DELTA_RECT_RATIO = 0.65;
	private static readonly MAX_CHANGED_PIXEL_RATIO = 0.4;

	public constructor() {
		this.pool = new Piscina({
			filename: path.resolve(__dirname, "quantize.worker.js"),
			minThreads: 1,
			maxThreads: Math.max(2, Math.min(4, os.cpus().length)),
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

		if (
			this.prevColorData === null ||
			this.prevColorData.length !== colorData.length
		) {
			this.prevColorData = colorData;
			return {
				type: "FRAME",
				data: colorData,
				width: screenWMaps,
				height: screenHMaps,
			};
		}

		let minX = width;
		let minY = height;
		let maxX = -1;
		let maxY = -1;
		let changedPixels = 0;

		for (let i = 0; i < colorData.length; i++) {
			if (colorData[i] === this.prevColorData[i]) {
				continue;
			}
			changedPixels++;
			const x = i % width;
			const y = Math.floor(i / width);
			if (x < minX) minX = x;
			if (y < minY) minY = y;
			if (x > maxX) maxX = x;
			if (y > maxY) maxY = y;
		}

		if (maxX < minX || maxY < minY) {
			return { type: "SKIP" };
		}

		const w = maxX - minX + 1;
		const h = maxY - minY + 1;
		const totalPixels = width * height;
		const rectRatio = (w * h) / totalPixels;
		const changedRatio = changedPixels / totalPixels;

		// If the delta is too large, full frame is cheaper and simpler to apply.
		if (
			rectRatio >= FrameProcessor.MAX_DELTA_RECT_RATIO ||
			changedRatio >= FrameProcessor.MAX_CHANGED_PIXEL_RATIO
		) {
			this.prevColorData = colorData;
			logger.debug(
				`Fallback to full frame: rectRatio=${rectRatio.toFixed(3)} changedRatio=${changedRatio.toFixed(3)}`,
			);
			return {
				type: "FRAME",
				data: colorData,
				width: screenWMaps,
				height: screenHMaps,
			};
		}

		const delta = new Uint8Array(w * h);
		let offset = 0;
		for (let y = minY; y <= maxY; y++) {
			for (let x = minX; x <= maxX; x++) {
				delta[offset++] = colorData[y * width + x] ?? 0;
			}
		}

		this.prevColorData = colorData;
		logger.debug(
			`Delta frame: x=${minX} y=${minY} w=${w} h=${h} changed=${changedPixels}/${totalPixels}`,
		);
		return {
			type: "DELTA_FRAME",
			data: delta,
			x: minX,
			y: minY,
			w,
			h,
		};
	}
}
