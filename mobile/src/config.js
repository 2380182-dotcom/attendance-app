// Central configuration for React Native App
// Override via EXPO_PUBLIC_API_URL environment variable at build time or in .env

const API_URL =
  process.env.EXPO_PUBLIC_API_URL || "http://localhost:8080/api";

const FACE_CONFIDENCE_THRESHOLD = parseFloat(
  process.env.EXPO_PUBLIC_FACE_CONFIDENCE_THRESHOLD || "0.65"
);

const FACE_MAX_ATTEMPTS = parseInt(
  process.env.EXPO_PUBLIC_FACE_MAX_ATTEMPTS || "3",
  10
);

export default {
  API_URL,
  FACE_CONFIDENCE_THRESHOLD,
  FACE_MAX_ATTEMPTS,
};
