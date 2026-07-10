module.exports = {
  extends: ['expo'],
  globals: {
    // Standard JS timer globals available in the Hermes/JSC RN runtime —
    // eslint-config-expo's global list omits them outside its web override.
    setInterval: 'readonly',
    clearInterval: 'readonly',
    setTimeout: 'readonly',
    clearTimeout: 'readonly',
  },
  rules: {
    // Findings 05 exists because there was no build-time guardrail against
    // raw console.log — only console.warn/console.error are allowed directly;
    // anything else goes through src/utils/debugLog.js, which is __DEV__-gated
    // internally so it can never ship to a production console by omission.
    'no-console': ['error', { allow: ['warn', 'error'] }],
  },
  overrides: [
    {
      // The one sanctioned place a raw console.log is allowed to exist —
      // everything else must go through this wrapper, not around it.
      files: ['src/utils/debugLog.js'],
      rules: { 'no-console': 'off' },
    },
  ],
};
