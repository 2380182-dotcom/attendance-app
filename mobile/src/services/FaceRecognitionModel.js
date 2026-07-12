import { loadTensorflowModel } from 'react-native-fast-tflite';
import { debugLog } from '../utils/debugLog';

let modelInstance = null;
let loadPromise = null;

// Fixed by our own crop/resize step in FaceEmbeddingService.js (MobileFaceNet's
// standard input resolution) — this is our preprocessing choice, not something
// the model declares, so it's a legitimate constant rather than a hardcoded
// assumption about the model itself.
const SINGLE_IMAGE_PIXEL_COUNT = 112 * 112 * 3;

// DIAG(2026-07-26): the on-device model diagnostic (modelDiagnostic.js)
// found this .tflite file's actual declared shapes are input [2,112,112,3]
// / output [2,192] — NOT the [1,112,112,3] / [1,384] this code originally
// assumed. Best-read root cause: MobileFaceNet is architecturally a
// single-image feature extractor, not a pairwise/Siamese network, and the
// output tensor is two independent 192-dim vectors concatenated (not one
// fused 384-dim vector or a single distance score) — the shape most likely
// wasn't authored intentionally. It's what you get when a TFLite conversion
// is traced/frozen from a concrete example tensor with batch size 2 (e.g. a
// 2-image sample batch used during conversion) instead of a dynamic batch
// axis, which silently locks the exported graph to that exact batch size.
// A real fix is re-exporting the model with batch=1; short of that, this
// derives every shape from the model itself at runtime (never hardcodes 384
// or 2) and duplicates the single real preprocessed image across every
// declared batch slot, so the tensor is always fully and validly populated
// — no zero/garbage-padded slot, which is what produced the original
// input-insensitive constant output (an underfilled input buffer).
let cachedInputTotalSize = null;
let cachedBatchSize = null;
let cachedEmbeddingSize = null;

/**
 * Load the MobileFaceNet model once on app start (or first usage).
 */
export function loadModel() {
  if (modelInstance) {
    return Promise.resolve(modelInstance);
  }
  if (loadPromise) {
    return loadPromise;
  }

  loadPromise = (async () => {
    try {
      debugLog('FaceRecognitionModel', 'Loading MobileFaceNet TFLite model...');
      const modelSource = require('../../assets/models/mobilefacenet.tflite');
      modelInstance = await loadTensorflowModel(modelSource);
      debugLog('FaceRecognitionModel', 'MobileFaceNet TFLite model loaded successfully.');
      return modelInstance;
    } catch (error) {
      console.error('Failed to load MobileFaceNet TFLite model:', error);
      loadPromise = null; // Allow retrying on next call
      throw error;
    }
  })();

  return loadPromise;
}

/**
 * Reads and caches the model's own declared input/output shapes — the
 * single source of truth for batch size and embedding dimensionality,
 * rather than assumptions baked into this code.
 */
async function getModelShapes() {
  if (cachedBatchSize != null && cachedEmbeddingSize != null) {
    return { inputTotalSize: cachedInputTotalSize, batchSize: cachedBatchSize, embeddingSize: cachedEmbeddingSize };
  }

  const model = await loadModel();
  const inputShape = model.inputs?.[0]?.shape;
  const outputShape = model.outputs?.[0]?.shape;
  if (!inputShape || !outputShape) {
    throw new Error('Face recognition model did not declare input/output tensor shapes.');
  }

  const inputTotalSize = inputShape.reduce((a, b) => a * b, 1);
  const batchSize = inputTotalSize / SINGLE_IMAGE_PIXEL_COUNT;
  if (!Number.isInteger(batchSize) || batchSize < 1) {
    throw new Error(
        `Model input shape ${JSON.stringify(inputShape)} (${inputTotalSize} values) is not a whole ` +
        `multiple of a single 112x112x3 image (${SINGLE_IMAGE_PIXEL_COUNT} values) — cannot marshal input.`);
  }

  const embeddingSize = outputShape[outputShape.length - 1];

  cachedInputTotalSize = inputTotalSize;
  cachedBatchSize = batchSize;
  cachedEmbeddingSize = embeddingSize;
  debugLog('FaceRecognitionModel', 'Model shapes — input:', JSON.stringify(inputShape),
      'output:', JSON.stringify(outputShape), 'batchSize:', batchSize, 'embeddingSize:', embeddingSize);

  return { inputTotalSize, batchSize, embeddingSize };
}

/**
 * The real per-image embedding dimensionality, read from the model itself.
 * Exposed so callers can validate a stored/fetched embedding's length
 * without duplicating this model's shape knowledge.
 */
export async function getExpectedEmbeddingSize() {
  const { embeddingSize } = await getModelShapes();
  return embeddingSize;
}

/**
 * Feed the aligned and normalized face image pixels into the TFLite model.
 * @param {Float32Array} inputPixels - Float32Array of size 112 * 112 * 3, one preprocessed image
 * @returns {Promise<number[]>} embedding vector (length = getExpectedEmbeddingSize())
 */
export async function getEmbedding(inputPixels) {
  const model = await loadModel();
  if (!model) {
    throw new Error('Face recognition model not loaded.');
  }
  if (inputPixels.length !== SINGLE_IMAGE_PIXEL_COUNT) {
    throw new Error(`Expected a single preprocessed image of ${SINGLE_IMAGE_PIXEL_COUNT} values, got ${inputPixels.length}.`);
  }

  const { inputTotalSize, batchSize, embeddingSize } = await getModelShapes();

  try {
    // Duplicate the one real image across every batch slot the model
    // declares, so no slot is left zero/garbage-filled.
    const batchedInput = new Float32Array(inputTotalSize);
    for (let b = 0; b < batchSize; b++) {
      batchedInput.set(inputPixels, b * SINGLE_IMAGE_PIXEL_COUNT);
    }

    const outputs = await model.run([batchedInput]);
    if (!outputs || outputs.length === 0) {
      throw new Error('Model inference returned no output.');
    }

    const raw = outputs[0];

    // Reinterpret the tensor's underlying bytes directly (buffer/byteOffset/byteLength)
    // instead of passing `raw` straight into `new Float32Array(raw)`. If the native
    // binding hands back the tensor wrapped in a different TypedArray subtype (e.g.
    // Uint16Array) than its true float32 data, `new Float32Array(raw)` would silently
    // do a value-by-value conversion at the WRONG length instead of a byte reinterpretation,
    // producing a corrupt embedding of the wrong size with garbage values.
    const floatArray = new Float32Array(
      raw.buffer,
      raw.byteOffset,
      raw.byteLength / Float32Array.BYTES_PER_ELEMENT
    );

    const expectedOutputSize = batchSize * embeddingSize;
    if (floatArray.length !== expectedOutputSize) {
      // TEMP DIAGNOSTIC: raw type/length/byteLength included directly in the thrown
      // message so it's readable in the on-screen alert without adb or a dev server.
      throw new Error(
        `Face embedding output has unexpected length ${floatArray.length} (expected ${expectedOutputSize} ` +
        `= batch ${batchSize} x embedding ${embeddingSize}), raw type: ${raw?.constructor?.name}. ` +
        'Refusing to save a potentially corrupt embedding.'
      );
    }

    // Every batch slot was fed the identical real image, so every slot's
    // output is the same real embedding (assuming the model doesn't mix
    // information across the batch axis, true for standard CNN feature
    // extractors) — the first slot is as good as any.
    return Array.from(floatArray.slice(0, embeddingSize));
  } catch (error) {
    console.error('Error during model inference:', error);
    throw new Error('Face embedding extraction failed: ' + error.message);
  }
}

export default {
  loadModel,
  getEmbedding,
  getExpectedEmbeddingSize,
};
