// このファイルの責務: RGBバッファをMinecraft向け144色インデックスへ量子化する。

const buildPalette = (): Uint8Array => {
	const arr = new Uint8Array(144 * 3);
	let idx = 0;
	for (let r = 0; r < 6; r++) {
		for (let g = 0; g < 6; g++) {
			for (let b = 0; b < 4; b++) {
				if (idx >= 144) break;
				arr[idx * 3] = Math.min(255, r * 51);
				arr[idx * 3 + 1] = Math.min(255, g * 51);
				arr[idx * 3 + 2] = Math.min(255, b * 85);
				idx++;
			}
		}
	}
	return arr;
};

const PALETTE = buildPalette();

const nearestColorIndex = (r: number, g: number, b: number): number => {
	let best = 0;
	let bestDist = Number.MAX_SAFE_INTEGER;
	for (let i = 0; i < 144; i++) {
		const pr = PALETTE[i * 3];
		const pg = PALETTE[i * 3 + 1];
		const pb = PALETTE[i * 3 + 2];
		const dr = r - pr;
		const dg = g - pg;
		const db = b - pb;
		const dist = dr * dr + dg * dg + db * db;
		if (dist < bestDist) {
			bestDist = dist;
			best = i;
		}
	}
	return best;
};

type QuantizePayload = {
	rgbBuffer: Uint8Array;
	width: number;
	height: number;
};

const quantize = ({
	rgbBuffer,
	width,
	height,
}: QuantizePayload): Uint8Array => {
	const out = new Uint8Array(width * height);
	const work = new Float32Array(rgbBuffer.length);
	for (let i = 0; i < rgbBuffer.length; i++) {
		work[i] = rgbBuffer[i] ?? 0;
	}

	for (let y = 0; y < height; y++) {
		for (let x = 0; x < width; x++) {
			const p = (y * width + x) * 3;
			const r = Math.max(0, Math.min(255, Math.round(work[p] ?? 0)));
			const g = Math.max(0, Math.min(255, Math.round(work[p + 1] ?? 0)));
			const b = Math.max(0, Math.min(255, Math.round(work[p + 2] ?? 0)));

			const index = nearestColorIndex(r, g, b);
			out[y * width + x] = index;

			const pr = PALETTE[index * 3];
			const pg = PALETTE[index * 3 + 1];
			const pb = PALETTE[index * 3 + 2];

			const er = r - pr;
			const eg = g - pg;
			const eb = b - pb;

			const diffuse = (nx: number, ny: number, factor: number): void => {
				if (nx < 0 || ny < 0 || nx >= width || ny >= height) return;
				const np = (ny * width + nx) * 3;
				work[np] += er * factor;
				work[np + 1] += eg * factor;
				work[np + 2] += eb * factor;
			};

			diffuse(x + 1, y, 7 / 16);
			diffuse(x - 1, y + 1, 3 / 16);
			diffuse(x, y + 1, 5 / 16);
			diffuse(x + 1, y + 1, 1 / 16);
		}
	}

	return out;
};

export default quantize;
