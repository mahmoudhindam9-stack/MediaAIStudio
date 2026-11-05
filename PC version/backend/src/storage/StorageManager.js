const fs = require('fs');
const path = require('path');

const STORAGE_DIR = path.join(__dirname, '../../temp_media');

function initStorage() {
    if (!fs.existsSync(STORAGE_DIR)) {
        fs.mkdirSync(STORAGE_DIR, { recursive: true });
    }
}

function getFilePath(filename) {
    return path.join(STORAGE_DIR, filename);
}

module.exports = {
    initStorage,
    getFilePath,
    STORAGE_DIR
};
