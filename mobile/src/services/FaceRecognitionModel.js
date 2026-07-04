import { loadTensorflowModel } from 'react-native-fast-tflite';

let modelInstance = null;
let loadPromise = null;

// Must match EMBEDDING_SIZE in FaceEmbeddingService.js (duplicated here to avoid a circular import).
const EXPECTED_EMBEDDING_SIZE = 192;

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
      console.log('Loading MobileFaceNet TFLite model...');
      const modelSource = require('../../assets/models/mobilefacenet.tflite');
      modelInstance = await loadTensorflowModel(modelSource);
      console.log('MobileFaceNet TFLite model loaded successfully.');
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
 * Feed the aligned and normalized face image pixels into the TFLite model.
 * @param {Float32Array} inputPixels - Float32Array of size 112 * 112 * 3
 * @returns {Promise<number[]>} 192-dimensional face embedding vector
 */
export async function getEmbedding(inputPixels) {
  const model = await loadModel();
  if (!model) {
    throw new Error('Face recognition model not loaded.');
  }

  try {
    // Run the model with the input Float32Array
    const outputs = await model.run([inputPixels]);
    if (!outputs || outputs.length === 0) {
      throw new Error('Model inference returned no output.');
    }

    const raw = outputs[0];

    // Output shape is [1, 192] representing the face embedding.
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

    if (floatArray.length !== EXPECTED_EMBEDDING_SIZE) {
      // TEMP DIAGNOSTIC: raw type/length/byteLength included directly in the thrown
      // message so it's readable in the on-screen alert without adb or a dev server.
      throw new Error(
        `Face embedding has unexpected length ${floatArray.length} (expected ${EXPECTED_EMBEDDING_SIZE}), ` +
        `raw type: ${raw?.constructor?.name}, raw length: ${raw?.length}, raw byteLength: ${raw?.byteLength}. ` +
        'Refusing to save a potentially corrupt embedding.'
      );
    }

    return Array.from(floatArray);
  } catch (error) {
    console.error('Error during model inference:', error);
    throw new Error('Face embedding extraction failed: ' + error.message);
  }
}

export default {
  loadModel,
  getEmbedding,
};
