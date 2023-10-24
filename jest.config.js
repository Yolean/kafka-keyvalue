module.exports = {
  testMatch: [
    '**/?(*.)(spec).ts?(x)',
  ],
  // Fixes/avoids https://github.com/facebook/jest/issues/6766
  testURL: 'http://localhost',
  moduleFileExtensions: [
    'js',
    'json',
    'jsx',
    'ts',
    'tsx',
  ],
  preset: 'ts-jest',
}
