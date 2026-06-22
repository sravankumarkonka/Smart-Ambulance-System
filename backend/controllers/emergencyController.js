import { db, bucket, hasAdmin } from '../config/firebaseAdmin.js';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Multer configuration for memory storage
const storage = multer.memoryStorage();

const fileFilter = (req, file, cb) => {
  const allowedMimeTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp'];
  if (allowedMimeTypes.includes(file.mimetype)) {
    cb(null, true);
  } else {
    cb(new Error('Invalid file type. Only JPEG, JPG, PNG, GIF, and WEBP images are allowed.'), false);
  }
};

export const upload = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 }, // limit file size to 5MB
  fileFilter
});

export const create = async (req, res) => {
  try {
    const {
      userId,
      patientName,
      emergencyType,
      description,
      latitude,
      longitude,
      severityLevel,
      hospitalName,
      hospitalLatitude,
      hospitalLongitude
    } = req.body;

    if (!userId || !patientName || !emergencyType || !description || latitude === undefined || longitude === undefined) {
      return res.status(400).json({ error: 'Missing required emergency fields.' });
    }

    // Strict validation and sanitization
    if (typeof patientName !== 'string' || !patientName.trim()) {
      return res.status(400).json({ error: 'Invalid or missing patient name.' });
    }
    const cleanPatientName = patientName.trim().substring(0, 100);

    const allowedTypes = ['accident', 'cardiac', 'respiratory', 'stroke', 'pregnancy', 'other'];
    if (typeof emergencyType !== 'string' || !allowedTypes.includes(emergencyType.toLowerCase())) {
      return res.status(400).json({ error: 'Invalid or missing emergency type.' });
    }
    const cleanEmergencyType = emergencyType.toLowerCase();

    if (typeof description !== 'string' || !description.trim()) {
      return res.status(400).json({ error: 'Invalid or missing description.' });
    }
    // Preserve values as plain text, while sanitizing length/whitespace
    const cleanDescription = description.trim().substring(0, 2000);

    const latVal = parseFloat(latitude);
    const lngVal = parseFloat(longitude);
    if (isNaN(latVal) || latVal < -90 || latVal > 90) {
      return res.status(400).json({ error: 'Latitude must be a valid number between -90 and 90.' });
    }
    if (isNaN(lngVal) || lngVal < -180 || lngVal > 180) {
      return res.status(400).json({ error: 'Longitude must be a valid number between -180 and 180.' });
    }

    const allowedSeverities = ['low', 'medium', 'high', 'critical'];
    let cleanSeverity = 'medium';
    if (severityLevel && allowedSeverities.includes(severityLevel.toLowerCase())) {
      cleanSeverity = severityLevel.toLowerCase();
    }

    const emergencyData = {
      userId,
      patientName: cleanPatientName,
      emergencyType: cleanEmergencyType,
      description: cleanDescription,
      latitude: latVal,
      longitude: lngVal,
      severityLevel: cleanSeverity,
      status: 'pending',
      createdAt: new Date().toISOString()
    };

    if (hospitalName) {
      emergencyData.hospitalName = hospitalName;
      emergencyData.hospitalLatitude = parseFloat(hospitalLatitude);
      emergencyData.hospitalLongitude = parseFloat(hospitalLongitude);
    }

    console.log('[Backend Emergency] Creating emergency request for patient:', cleanPatientName);
    const docRef = await db.collection('emergencies').add(emergencyData);
    console.log('[Backend Emergency] Emergency request created in Firestore with ID:', docRef.id);
    
    return res.status(201).json({ id: docRef.id });
  } catch (error) {
    console.error('Error creating emergency:', error);
    return res.status(500).json({ error: 'Failed to report emergency: ' + error.message });
  }
};

export const getById = async (req, res) => {
  try {
    const { id } = req.params;
    const docSnap = await db.collection('emergencies').doc(id).get();
    
    if (!docSnap.exists) {
      return res.status(404).json({ error: 'Emergency request not found.' });
    }

    // IDOR fix: non-admins may only read their own emergencies
    const data = docSnap.data();
    if (req.user.role !== 'admin' && data.userId !== req.user.uid) {
      return res.status(403).json({ error: 'Forbidden: You do not have permission to view this emergency.' });
    }

    return res.status(200).json({ id: docSnap.id, ...data });
  } catch (error) {
    console.error('Error fetching emergency:', error);
    return res.status(500).json({ error: 'Failed to retrieve emergency details.' });
  }
};


export const getHistory = async (req, res) => {
  try {
    const { userId } = req.params;

    // IDOR fix: non-admins may only retrieve their own emergency history
    if (req.user.role !== 'admin' && req.user.uid !== userId) {
      return res.status(403).json({ error: 'Forbidden: You can only view your own emergency history.' });
    }

    const snapshot = await db.collection('emergencies')
      .where('userId', '==', userId)
      .get();
    
    const list = [];
    snapshot.forEach(doc => {
      list.push({ id: doc.id, ...doc.data() });
    });

    // Sort in-memory to prevent Firebase query index requirement issues
    list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

    return res.status(200).json(list);
  } catch (error) {
    console.error('Error fetching emergency history:', error);
    return res.status(500).json({ error: 'Failed to retrieve emergency history.' });
  }
};


export const uploadImage = async (req, res) => {
  try {
    const { id } = req.params;
    if (!req.file) {
      return res.status(400).json({ error: 'No image file uploaded.' });
    }

    const file = req.file;

    // Use Firebase Storage bucket if authorized, else fall back to local disk storage
    if (bucket && hasAdmin) {
      console.log('[Backend Emergency] Uploading accident scene image to Firebase Storage for emergency:', id);
      const fileName = `accident-images/${id}`;
      const fileUpload = bucket.file(fileName);

      const blobStream = fileUpload.createWriteStream({
        metadata: {
          contentType: file.mimetype
        }
      });

      blobStream.on('error', (error) => {
        console.error('Error uploading file to Firebase Storage:', error);
        if (!res.headersSent) {
          return res.status(500).json({ error: 'Upload failed: ' + error.message });
        }
      });

      blobStream.on('finish', async () => {
        try {
          const imageUrl = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(fileName)}?alt=media`;
          await db.collection('emergencies').doc(id).update({ imageUrl });
          console.log('[Backend Emergency] Accident image uploaded to Firebase Storage. URL:', imageUrl);
          if (!res.headersSent) {
            return res.status(200).json({ imageUrl });
          }
        } catch (updateError) {
          console.error('[Backend Emergency] Failed to update document with image URL:', updateError);
          if (!res.headersSent) {
            return res.status(500).json({ error: 'Failed to update emergency document: ' + updateError.message });
          }
        }
      });

      blobStream.end(file.buffer);
    } else {
      console.log('[Backend Emergency] Uploading accident scene image to Local Disk for emergency:', id);
      const uploadDir = path.resolve(__dirname, '../uploads/accident-images');
      if (!fs.existsSync(uploadDir)) {
        fs.mkdirSync(uploadDir, { recursive: true });
      }
      
      const filePath = path.join(uploadDir, `${id}.png`);
      fs.writeFileSync(filePath, file.buffer);

      const imageUrl = `http://localhost:5000/uploads/accident-images/${id}.png`;
      await db.collection('emergencies').doc(id).update({ imageUrl });
      console.log('[Backend Emergency] Accident image uploaded to Local Disk. URL:', imageUrl);

      return res.status(200).json({ imageUrl });
    }
  } catch (error) {
    console.error('Error in uploadImage endpoint:', error);
    return res.status(500).json({ error: 'Upload failed: ' + error.message });
  }
};

export const cancel = async (req, res) => {
  try {
    const { id } = req.params;

    if (!req.user || !req.user.uid) {
      return res.status(401).json({ error: 'Unauthorized: Invalid authentication token.' });
    }

    const docRef = db.collection('emergencies').doc(id);
    const docSnap = await docRef.get();

    if (!docSnap.exists) {
      return res.status(404).json({ error: 'Emergency request not found.' });
    }

    const emergency = docSnap.data();

    // Verify ownership
    if (emergency.userId !== req.user.uid) {
      return res.status(403).json({ error: 'Forbidden: You do not own this emergency request.' });
    }

    // Verify status allows cancellation
    if (emergency.status !== 'pending' && emergency.status !== 'assigned') {
      return res.status(400).json({ error: 'Invalid state transition: Request cannot be cancelled in status ' + emergency.status });
    }

    const batch = db.batch();
    batch.update(docRef, {
      status: 'cancelled',
      updatedAt: new Date().toISOString()
    });

    if (emergency.driverId) {
      const ambulanceRef = db.collection('ambulances').doc(emergency.driverId);
      batch.set(ambulanceRef, {
        status: 'available',
        lastUpdated: new Date().toISOString()
      }, { merge: true });
    }

    await batch.commit();
    console.log(`[Backend Emergency] Request ${id} cancelled successfully by user ${req.user.uid}`);

    return res.status(200).json({ message: 'Emergency request cancelled successfully.' });
  } catch (error) {
    console.error('Error cancelling emergency request:', error);
    return res.status(500).json({ error: 'Failed to cancel emergency request: ' + error.message });
  }
};

