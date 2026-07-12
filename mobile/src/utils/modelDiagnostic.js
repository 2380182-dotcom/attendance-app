// DIAG(2026-07-26): temporary diagnostic for the face-verification
// input-insensitive-model investigation (security audit Finding 05 /
// the "everyone matches everyone" bug). Isolates the TFLite model from
// the camera/crop pipeline entirely by feeding it synthetic, maximally-
// different tensors directly, and logs the model's own declared
// input/output tensor metadata. Delete this file and its call site once
// the model bug is root-caused and fixed.
//
// Deliberately uses console.log directly, NOT debugLog: debugLog is
// internally gated on __DEV__, which is false in the preview-profile
// build this is meant to run on — routing through it would make every
// call below a silent no-op, defeating the only reason this file
// exists. The whole point here is unconditional output.
/* eslint-disable no-console -- see comment above; temporary, removed with this file */
import { loadModel } from '../services/FaceRecognitionModel';

export async function diagnoseModel() {
  console.log('[ModelDiag] Starting model diagnostic...');
  try {
    const model = await loadModel();

    console.log('[ModelDiag] INPUT TENSORS:', JSON.stringify(model.inputs));
    console.log('[ModelDiag] OUTPUT TENSORS:', JSON.stringify(model.outputs));

    const size = 112 * 112 * 3;
    const zeros = new Float32Array(size).fill(0);
    const ones = new Float32Array(size).fill(1);

    const outA = await model.run([zeros]);
    const outB = await model.run([ones]);
    console.log('[ModelDiag] run() zeros first10:', Array.from(outA[0].slice(0, 10)));
    console.log('[ModelDiag] run() ones  first10:', Array.from(outB[0].slice(0, 10)));
    console.log('[ModelDiag] run() identical?', outA[0].every((v, i) => v === outB[0][i]));

    const syncA = model.runSync([zeros]);
    const syncB = model.runSync([ones]);
    console.log('[ModelDiag] runSync() zeros first10:', Array.from(syncA[0].slice(0, 10)));
    console.log('[ModelDiag] runSync() ones  first10:', Array.from(syncB[0].slice(0, 10)));
    console.log('[ModelDiag] runSync() identical?', syncA[0].every((v, i) => v === syncB[0][i]));

    console.log('[ModelDiag] Diagnostic complete.');
  } catch (e) {
    console.log('[ModelDiag] ERROR during diagnostic:', e.message, e.stack);
  }
}
