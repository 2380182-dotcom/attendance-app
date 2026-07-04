import { loadTensorflowModel } from 'react-native-fast-tflite';

let modelInstance = null;
let loadPromise = null;

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

    // Output shape is [1, 192] representing the face embedding
    const floatArray = new Float32Array(outputs[0]);
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
