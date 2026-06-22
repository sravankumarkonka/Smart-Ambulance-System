import { calculateHaversineDistance } from '../utils/haversine.js';

// Pre-configured Bangalore Hospitals matching coordinates in routingService.js
// Extended with simulated total and available ICU beds, ratings, and contact info
export const HOSPITALS = [
  {
    id: 'manipal',
    name: 'Manipal Hospital (HAL Road)',
    latitude: 12.9592,
    longitude: 77.6443,
    totalIcuBeds: 50,
    availableIcuBeds: 12,
    rating: 4.8,
    phone: '+91-80-2502-4444'
  },
  {
    id: 'fortis',
    name: 'Fortis Hospital (Bannerghatta Road)',
    latitude: 12.8943,
    longitude: 77.5979,
    totalIcuBeds: 40,
    availableIcuBeds: 5,
    rating: 4.6,
    phone: '+91-80-6621-4444'
  },
  {
    id: 'apollo',
    name: 'Apollo Hospitals (Bannerghatta Road)',
    latitude: 12.8958,
    longitude: 77.5997,
    totalIcuBeds: 45,
    availableIcuBeds: 0, // Mock 0 beds to test ICU check and filter warning
    rating: 4.7,
    phone: '+91-80-2630-4050'
  },
  {
    id: 'columbia',
    name: 'Columbia Asia Referral Hospital (Yeshwanthpur)',
    latitude: 13.0135,
    longitude: 77.5516,
    totalIcuBeds: 30,
    availableIcuBeds: 8,
    rating: 4.5,
    phone: '+91-80-3989-8969'
  }
];

/**
 * Finds the best hospital for a patient based on proximity, severity of request, and ICU availability.
 *
 * @param {number} patientLat Latitude of the patient
 * @param {number} patientLng Longitude of the patient
 * @param {string} severityLevel Severity level ('low', 'medium', 'high', 'critical')
 * @returns {object} The recommended hospital and full comparison statistics
 */
export function findBestHospital(patientLat, patientLng, severityLevel = 'medium') {
  const isCritical = severityLevel === 'critical' || severityLevel === 'high';

  const comparison = HOSPITALS.map(hospital => {
    const distanceKm = calculateHaversineDistance(
      patientLat,
      patientLng,
      hospital.latitude,
      hospital.longitude
    );

    // Calculate a suitability score
    // Baseline score starts at 100
    // Subtract points based on distance (closer is better)
    let suitabilityScore = 100 - (distanceKm * 2.5);

    // Add points based on hospital rating (premium care indicator)
    suitabilityScore += (hospital.rating * 3);

    let icuStatus = 'available';

    if (hospital.availableIcuBeds === 0) {
      icuStatus = 'unavailable';
      if (isCritical) {
        // Heavily penalize this hospital for critical emergencies if no ICU is available
        suitabilityScore -= 60;
      } else {
        // Moderately penalize for non-critical emergencies
        suitabilityScore -= 15;
      }
    } else if (hospital.availableIcuBeds < 3) {
      icuStatus = 'limited';
    }

    return {
      ...hospital,
      distanceKm: parseFloat(distanceKm.toFixed(2)),
      icuStatus,
      suitabilityScore: parseFloat(suitabilityScore.toFixed(1))
    };
  });

  // Sort comparison list by suitability score descending
  comparison.sort((a, b) => b.suitabilityScore - a.suitabilityScore);

  return {
    recommended: comparison[0],
    comparison
  };
}
