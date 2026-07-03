import FaceDetection from '@react-native-ml-kit/face-detection';
import config from '../config';
import { apiService } from './api';
import {
  getFaceEmbedding,
  cosineSimilarity,
  base64ToFloatArray,
  checkLiveness,
} from './FaceEmbeddingService';

const DETECT_OPTIONS = {
  performanceMode: 'accurate',
  landmarkMode: 'all',
  classificationMode: 'all',
  minFaceSize: 0.12,
  trackingEnabled: true,
};

/**
 * Detect faces in a captured photo URI using ML Kit (on-device).
 */
export async function detectFacesInImage(imageUri) {
  return FaceDetection.detect(imageUri, DETECT_OPTIONS);
}

/**
 * Extract embedding from a photo URI.
 */
export async function extractEmbeddingFromImage(imageUri) {
  const faces = await detectFacesInImage(imageUri);
  if (!faces || faces.length === 0) {
    throw new Error('No face detected. Center your face in the frame.');
  }
  if (faces.length > 1) {
    throw new Error('Multiple faces detected. Only one person should be in frame.');
  }
  
  // Extract real embedding using MobileFaceNet model
  const embedding = await getFaceEmbedding(imageUri, faces[0]);
  return { embedding, face: faces[0] };
}

/**
 * Verify live capture against stored reference embedding (all on-device).
 */
export async function verifyAgainstReference(imageUri, referenceBase64, threshold) {
  const confidenceThreshold = threshold ?? config.FACE_CONFIDENCE_THRESHOLD;
  const reference = base64ToFloatArray(referenceBase64);
  
  // Check for face model profile backward compatibility (dimension mismatch)
  if (reference.length !== 192) {
    throw new Error('Face profile update required. Please re-enroll your face under Settings.');
  }

  const faces = await detectFacesInImage(imageUri);
  if (!faces || faces.length === 0) {
    throw new Error('No face detected. Center your face in the frame.');
  }
  if (faces.length > 1) {
    throw new Error('Multiple faces detected. Only one person should be in frame.');
  }

  const face = faces[0];
  const liveness = checkLiveness(face);
  if (!liveness.passed) {
    return { verified: false, confidence: 0, reason: liveness.reason };
  }

  const embedding = await getFaceEmbedding(imageUri, face);
  const confidence = cosineSimilarity(embedding, reference);

  if (confidence < confidenceThreshold) {
    return {
      verified: false,
      confidence,
      reason: `Face match confidence ${(confidence * 100).toFixed(0)}% is below required ${(confidenceThreshold * 100).toFixed(0)}%`,
    };
  }

  return { verified: true, confidence };
}

/**
 * Liveness check across two sequential captures.
 */
export async function verifyWithLiveness(imageUri1, imageUri2, referenceBase64, threshold) {
  const faces1 = await detectFacesInImage(imageUri1);
  const faces2 = await detectFacesInImage(imageUri2);

  if (!faces1 || faces1.length === 0) {
    return { verified: false, confidence: 0, reason: 'No face detected in the first frame' };
  }
  if (!faces2 || faces2.length === 0) {
    return { verified: false, confidence: 0, reason: 'No face detected in the second frame' };
  }

  const face1 = faces1[0];
  const face2 = faces2[0];

  const liveness = checkLiveness(face1, face2);
  if (!liveness.passed) {
    return { verified: false, confidence: 0, reason: liveness.reason };
  }

  return verifyAgainstReference(imageUri2, referenceBase64, threshold);
}

/**
 * Fetch reference embedding from backend for on-device comparison.
 */
export async function fetchReferenceEmbedding(agentId) {
  const data = await apiService.face.getEmbedding(agentId);
  if (!data?.registered || !data?.embedding) {
    throw new Error('Face not enrolled. Please complete face enrollment first.');
  }
  return data.embedding;
}

/**
 * Submit verification result to backend (no image transmitted).
 */
export async function submitVerificationResult(agentId, verified, confidence, checkpointType) {
  return apiService.face.submitResult({
    agentId,
    verificationResult: verified ? 'PASS' : 'FAIL',
    confidenceScore: confidence,
    checkpointType,
    timestamp: new Date().toISOString(),
  });
}

export default {
  detectFacesInImage,
  extractEmbeddingFromImage,
  verifyAgainstReference,
  verifyWithLiveness,
  fetchReferenceEmbedding,
  submitVerificationResult,
};
