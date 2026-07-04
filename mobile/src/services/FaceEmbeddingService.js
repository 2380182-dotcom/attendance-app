import * as ImageManipulator from 'expo-image-manipulator';
import * as FileSystem from 'expo-file-system';
import jpeg from 'jpeg-js';
import FaceRecognitionModel from './FaceRecognitionModel';

/**
 * On-device face embedding utilities using MobileFaceNet TFLite model.
 * Embeddings are 384-dim normalized vectors.
 */

const EMBEDDING_SIZE = 384;

function normalize(vec) {
  const norm = Math.sqrt(vec.reduce((s, v) => s + v * v, 0));
  if (norm === 0) return vec;
  return vec.map((v) => v / norm);
}

function averageEmbeddings(embeddings) {
  if (!embeddings.length) return null;
  const size = embeddings[0].length;
  const avg = new Array(size).fill(0);
  for (const emb of embeddings) {
    for (let i = 0; i < size; i++) avg[i] += emb[i];
  }
  return normalize(avg.map((v) => v / embeddings.length));
}

/**
 * Base64 string to Uint8Array bytes converter.
 * Highly robust fallback logic for React Native environments.
 */
function base64ToUint8Array(base64) {
  if (typeof globalThis.atob === 'function') {
    const binary = globalThis.atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }
  
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const lookup = new Uint8Array(256);
  for (let i = 0; i < chars.length; i++) {
    lookup[chars.charCodeAt(i)] = i;
  }
  const cleaned = base64.replace(/=+$/, '');
  const len = cleaned.length;
  const bytes = new Uint8Array(Math.floor((len * 3) / 4));
  let p = 0;
  for (let i = 0; i < len; i += 4) {
    const a = lookup[cleaned.charCodeAt(i)] ?? 0;
    const b = lookup[cleaned.charCodeAt(i + 1)] ?? 0;
    const c = lookup[cleaned.charCodeAt(i + 2)] ?? 0;
    const d = lookup[cleaned.charCodeAt(i + 3)] ?? 0;
    bytes[p++] = (a << 2) | (b >> 4);
    if (i + 2 < len) bytes[p++] = ((b & 15) << 4) | (c >> 2);
    if (i + 3 < len) bytes[p++] = ((c & 3) << 6) | d;
  }
  return bytes;
}

/**
 * Preprocess face from camera frame, align/resize, and extract the MobileFaceNet embedding.
 * @param {string} imageUri - Local URI of the camera frame
 * @param {object} face - Face detection result from ML Kit
 * @returns {Promise<number[]>} 384-dimensional embedding vector
 */
export async function getFaceEmbedding(imageUri, face) {
  if (!face || !face.frame) {
    throw new Error('Invalid face details provided for embedding extraction.');
  }

  try {
    // 1. Get original image size to safely calculate bounds
    const original = await ImageManipulator.manipulateAsync(imageUri, []);
    const imgWidth = original.width;
    const imgHeight = original.height;

    const { left, top, width, height } = face.frame;

    // 2. Crop face region with 15% padding margin to ensure full face features are preserved
    const marginX = width * 0.15;
    const marginY = height * 0.15;

    const originX = Math.max(0, Math.floor(left - marginX));
    const originY = Math.max(0, Math.floor(top - marginY));
    const cropWidth = Math.min(imgWidth - originX, Math.floor(width + 2 * marginX));
    const cropHeight = Math.min(imgHeight - originY, Math.floor(height + 2 * marginY));

    // 3. Crop face, rotate to align eyes horizontally (roll/rotationZ), and resize to 112x112
    const rollAngle = face.rotationZ || 0; // counter-rotate by roll to align face
    const manipResult = await ImageManipulator.manipulateAsync(
      imageUri,
      [
        { crop: { originX, originY, width: cropWidth, height: cropHeight } },
        { rotate: -rollAngle },
        { resize: { width: 112, height: 112 } },
      ],
      { format: ImageManipulator.SaveFormat.JPEG, compress: 0.9 }
    );

    // 4. Read the cropped face image as base64 string
    const base64Str = await FileSystem.readAsStringAsync(manipResult.uri, {
      encoding: FileSystem.EncodingType.Base64,
    });

    // 5. Decode JPEG bytes into raw RGBA pixel values
    const bytes = base64ToUint8Array(base64Str);
    const rawImageData = jpeg.decode(bytes, { useTArray: true });
    const { data } = rawImageData;

    // 6. Extract RGB channels and normalize values per MobileFaceNet: (pixel - 127.5) / 128.0
    const inputPixels = new Float32Array(112 * 112 * 3);
    let inputIdx = 0;
    for (let i = 0; i < data.length; i += 4) {
      inputPixels[inputIdx++] = (data[i] - 127.5) / 128.0;     // R
      inputPixels[inputIdx++] = (data[i + 1] - 127.5) / 128.0; // G
      inputPixels[inputIdx++] = (data[i + 2] - 127.5) / 128.0; // B
    }

    // 7. Feed normalized pixels to TFLite model and return embedding
    const rawEmbedding = await FaceRecognitionModel.getEmbedding(inputPixels);
    return normalize(rawEmbedding);
  } catch (error) {
    console.error('Error in face embedding pipeline:', error);
    throw new Error('On-device face recognition failed to extract embedding: ' + error.message);
  }
}

export function cosineSimilarity(a, b) {
  if (!a || !b || a.length !== b.length) return 0;
  let dot = 0;
  let na = 0;
  let nb = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    na += a[i] * a[i];
    nb += b[i] * b[i];
  }
  if (na === 0 || nb === 0) return 0;
  return dot / (Math.sqrt(na) * Math.sqrt(nb));
}

/** Encode float32 array to base64 (little-endian) for backend storage */
export function floatArrayToBase64(floats) {
  const buffer = new ArrayBuffer(floats.length * 4);
  const view = new DataView(buffer);
  floats.forEach((v, i) => view.setFloat32(i * 4, v, true));
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  if (typeof globalThis.btoa === 'function') {
    return globalThis.btoa(binary);
  }
  // Fallback manual base64
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let result = '';
  for (let i = 0; i < binary.length; i += 3) {
    const a = binary.charCodeAt(i);
    const b = i + 1 < binary.length ? binary.charCodeAt(i + 1) : 0;
    const c = i + 2 < binary.length ? binary.charCodeAt(i + 2) : 0;
    result += chars[a >> 2];
    result += chars[((a & 3) << 4) | (b >> 4)];
    result += i + 1 < binary.length ? chars[((b & 15) << 2) | (c >> 6)] : '=';
    result += i + 2 < binary.length ? chars[c & 63] : '=';
  }
  return result;
}

/** Decode base64 float32 array from backend */
export function base64ToFloatArray(base64) {
  let binary;
  if (typeof globalThis.atob === 'function') {
    binary = globalThis.atob(base64);
  } else {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    const lookup = {};
    for (let i = 0; i < chars.length; i++) lookup[chars[i]] = i;
    binary = '';
    const cleaned = base64.replace(/=+$/, '');
    for (let i = 0; i < cleaned.length; i += 4) {
      const a = lookup[cleaned[i]] ?? 0;
      const b = lookup[cleaned[i + 1]] ?? 0;
      const c = lookup[cleaned[i + 2]] ?? 0;
      const d = lookup[cleaned[i + 3]] ?? 0;
      binary += String.fromCharCode((a << 2) | (b >> 4));
      if (cleaned[i + 2]) binary += String.fromCharCode(((b & 15) << 4) | (c >> 2));
      if (cleaned[i + 3]) binary += String.fromCharCode(((c & 3) << 6) | d);
    }
  }
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const view = new DataView(bytes.buffer);
  const floats = [];
  for (let i = 0; i < bytes.length / 4; i++) {
    floats.push(view.getFloat32(i * 4, true));
  }
  return floats;
}

/**
 * Liveness check — reject static photos / spoofed faces.
 * Requires open eyes in both frames (if two frames are passed) and sufficient
 * head pose variation (yaw/pitch delta) between the two sequential captures.
 */
export function checkLiveness(face1, face2 = null) {
  if (!face1) {
    return { passed: false, reason: 'No face detected' };
  }

  // Check eyes open on the first frame
  const leftEye1 = face1.leftEyeOpenProbability ?? 1;
  const rightEye1 = face1.rightEyeOpenProbability ?? 1;
  if (leftEye1 < 0.4 || rightEye1 < 0.4) {
    return { passed: false, reason: 'Please keep your eyes open' };
  }

  // Ensure reasonable face size to reject tiny screens/photos
  const minSize = 0.12;
  const faceRatio = face1.frame.width / (face1.frame.height || 1);
  if (face1.frame.width < 80 || faceRatio < minSize) {
    return { passed: false, reason: 'Move closer to the camera' };
  }

  // If a second frame is provided, check head-angle variation
  if (face2) {
    const leftEye2 = face2.leftEyeOpenProbability ?? 1;
    const rightEye2 = face2.rightEyeOpenProbability ?? 1;
    if (leftEye2 < 0.4 || rightEye2 < 0.4) {
      return { passed: false, reason: 'Please keep your eyes open' };
    }

    const yaw1 = face1.headEulerAngleY ?? face1.rotationY ?? 0;
    const yaw2 = face2.headEulerAngleY ?? face2.rotationY ?? 0;
    const pitch1 = face1.headEulerAngleX ?? face1.rotationX ?? 0;
    const pitch2 = face2.headEulerAngleX ?? face2.rotationX ?? 0;

    const yawDelta = Math.abs(yaw1 - yaw2);
    const pitchDelta = Math.abs(pitch1 - pitch2);

    if (yawDelta < 10.0 && pitchDelta < 10.0) {
      return { passed: false, reason: 'Please turn your head slightly (liveness check)' };
    }
  }

  return { passed: true };
}

export { averageEmbeddings, EMBEDDING_SIZE };
