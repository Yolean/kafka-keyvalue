module.exports = {
  "globals": {
    "ts-jest": {
      "skipBabel": true
    }
  },
  "transform": {
    "^.+\\.tsx?$": "ts-jest"
  },
  "testMatch": [
    "**/?(*.)(spec).ts?(x)"
  ],
  // Fixes/avoids https://github.com/facebook/jest/issues/6766
  "testURL": "http://localhost",
  "moduleFileExtensions": [
    "ts",
    "tsx",
    "js",
    "jsx",
    "json"
  ]
}
