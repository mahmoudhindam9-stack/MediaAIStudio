require('dotenv').config();
const express = require('express');
const cors = require('cors');
const apiRoutes = require('./api/generate');
const { initStorage } = require('./storage/StorageManager');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Initialize temporary storage directory
initStorage();

// Basic API routes
app.use('/v1/ai', apiRoutes);

app.get('/health', (req, res) => {
    res.json({ status: 'ok', imageModel: process.env.IMAGE_MODEL, videoModel: process.env.VIDEO_MODEL });
});

app.listen(PORT, () => {
    console.log(`Backend listening on port ${PORT}`);
});
