import { findBestHospital, HOSPITALS } from '../services/hospitalService.js';

/**
 * Handles recommending the best hospital based on patient coordinates and severity level.
 * Exposes GET /api/hospitals/recommend?latitude=X&longitude=Y&severityLevel=Z
 */
export const recommendHospital = async (req, res) => {
  try {
    const { latitude, longitude, severityLevel } = req.query;

    if (latitude === undefined || longitude === undefined) {
      return res.status(400).json({ error: 'Patient latitude and longitude coordinates are required query params.' });
    }

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);

    if (isNaN(lat) || isNaN(lng)) {
      return res.status(400).json({ error: 'Invalid latitude or longitude coordinate values.' });
    }

    const recommendation = findBestHospital(lat, lng, severityLevel || 'medium');
    return res.status(200).json(recommendation);
  } catch (error) {
    console.error('[Hospital Controller] Error recommending hospital:', error);
    return res.status(500).json({ error: 'Failed to generate hospital recommendations: ' + error.message });
  }
};

/**
 * Retrieves the list of all hospitals with contact and ICU bed info.
 * Exposes GET /api/hospitals
 */
export const getHospitalsList = async (req, res) => {
  try {
    return res.status(200).json(HOSPITALS);
  } catch (error) {
    console.error('[Hospital Controller] Error retrieving hospital list:', error);
    return res.status(500).json({ error: 'Failed to retrieve hospitals: ' + error.message });
  }
};
