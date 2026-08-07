import api from './api';
import { getDirections, searchNearbyPlaces, reverseGeocode, calculateHaversineDistance as gCalcDist } from './googleMapsService';

/**
 * Pre-configured Hospitals in the Bangalore area (static fallback)
 */
export const HOSPITALS = [
  { id: 'manipal', name: 'Manipal Hospital (HAL Road)', latitude: 12.9592, longitude: 77.6443 },
  { id: 'fortis', name: 'Fortis Hospital (Bannerghatta Road)', latitude: 12.8943, longitude: 77.5979 },
  { id: 'apollo', name: 'Apollo Hospitals (Bannerghatta Road)', latitude: 12.8958, longitude: 77.5997 },
  { id: 'columbia', name: 'Columbia Asia Referral Hospital (Yeshwanthpur)', latitude: 13.0135, longitude: 77.5516 },
  { id: 'narayana', name: 'Narayana Health City (Bommasandra)', latitude: 12.8389, longitude: 77.6638 },
  { id: 'bgshospital', name: 'BGS Gleneagles Global Hospital', latitude: 12.9025, longitude: 77.5450 },
];

/**
 * Calculates Haversine distance between two coordinates in kilometers.
 */
export const calculateHaversineDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

/**
 * Finds the nearest hospital to patient coordinates (from static list).
 */
export const findNearestHospital = (lat, lng) => {
  let nearest = HOSPITALS[0];
  let minDistance = calculateHaversineDistance(lat, lng, nearest.latitude, nearest.longitude);

  for (let i = 1; i < HOSPITALS.length; i++) {
    const d = calculateHaversineDistance(lat, lng, HOSPITALS[i].latitude, HOSPITALS[i].longitude);
    if (d < minDistance) {
      minDistance = d;
      nearest = HOSPITALS[i];
    }
  }
  return { ...nearest, distance: minDistance };
};

/**
 * Fetches nearby hospitals using Google Places API.
 * Falls back to static HOSPITALS list if Places API is unavailable.
 * Returns array sorted by distance.
 */
export const fetchNearbyHospitals = async (lat, lng, radius = 5000) => {
  try {
    const { searchNearbyPlaces: searchNearby } = await import('./googleMapsService');
    const results = await searchNearby(lat, lng, 'hospital', radius);
    if (results && results.length > 0) {
      return results
        .map(h => ({
          ...h,
          id: h.placeId,
          distance: calculateHaversineDistance(lat, lng, h.latitude, h.longitude),
        }))
        .sort((a, b) => a.distance - b.distance);
    }
  } catch (err) {
    console.warn('[Routing] Google Places nearby hospitals failed:', err.message);
  }
  // Fallback to static list
  return HOSPITALS.map(h => ({
    ...h,
    distance: calculateHaversineDistance(lat, lng, h.latitude, h.longitude),
  })).sort((a, b) => a.distance - b.distance);
};

/**
 * Recommends the best hospital.
 * Priority: backend algorithm → Google Places nearby → static nearest
 */
export const recommendHospital = async (lat, lng, severityLevel = 'medium') => {
  try {
    const response = await api.get('/api/hospitals/recommend', {
      params: { latitude: lat, longitude: lng, severityLevel }
    });
    return response.data;
  } catch (err) {
    console.warn('[Routing] Backend hospital recommendation failed, trying Google Places:', err.message);
  }

  // Try Google Places
  try {
    const nearbyHospitals = await fetchNearbyHospitals(lat, lng);
    if (nearbyHospitals.length > 0) {
      return {
        recommended: nearbyHospitals[0],
        comparison: nearbyHospitals.slice(0, 5).map(h => ({
          ...h,
          distanceKm: parseFloat(h.distance.toFixed(2)),
          icuStatus: 'available',
          suitabilityScore: 100 - (h.distance * 10),
        })),
        source: 'Google Places API',
      };
    }
  } catch (placesErr) {
    console.warn('[Routing] Google Places hospital fetch failed:', placesErr.message);
  }

  // Final fallback: static list
  const nearest = findNearestHospital(lat, lng);
  return {
    recommended: nearest,
    comparison: HOSPITALS.map(h => {
      const dist = calculateHaversineDistance(lat, lng, h.latitude, h.longitude);
      return {
        ...h,
        distanceKm: parseFloat(dist.toFixed(2)),
        icuStatus: 'available',
        suitabilityScore: 0,
      };
    }),
    source: 'Static Fallback',
  };
};

/**
 * Traffic Congestion Simulator (used when real Directions data is unavailable):
 * Generates traffic weight, status, and custom messages based on coordinates and time of day.
 */
export const simulateTraffic = (coordinates) => {
  const hour = new Date().getHours();
  let baseFactor = 1.0;
  let status = 'Normal';
  let message = 'Clear roads ahead.';

  if ((hour >= 8 && hour <= 10.5) || (hour >= 17 && hour <= 19.5)) {
    baseFactor = 1.45;
    status = 'Heavy';
    message = 'Rush hour delays. Alternative paths suggested.';
  } else if (hour >= 12 && hour <= 14) {
    baseFactor = 1.2;
    status = 'Moderate';
    message = 'Moderate volume near commercial hubs.';
  }

  const isCityCenterCongested = coordinates.some(
    ([lng, lat]) => lat > 12.93 && lat < 12.98 && lng > 77.58 && lng < 77.63
  );

  if (isCityCenterCongested) {
    baseFactor += 0.15;
    if (status === 'Normal') {
      status = 'Moderate';
      message = 'Construction delays in central business district.';
    } else {
      status = 'Critical';
      message = 'Gridlock warnings in central zones. High latency.';
    }
  }

  return { factor: baseFactor, status, message };
};

/**
 * PRIMARY ROUTING FUNCTION
 * 
 * Priority chain:
 *  1. Google Directions API (most accurate, traffic-aware)
 *  2. Backend proxy → OpenRouteService / OSRM
 *  3. Local high-fidelity mock route generator
 * 
 * Coordinates: [[lat, lng], [lat, lng], ...]
 * Returns: { coordinates, distanceKm, durationSec, steps, traffic, source }
 */
export const fetchRoute = async (waypoints) => {
  if (!waypoints || waypoints.length < 2) return generateHighFidelityMockRoute(waypoints || []);

  // 1. Try Google Directions API
  try {
    const origin = waypoints[0];
    const destination = waypoints[waypoints.length - 1];
    const middleWaypoints = waypoints.slice(1, -1);

    const googleRoute = await getDirections(origin, destination, middleWaypoints);
    if (googleRoute && googleRoute.coordinates && googleRoute.coordinates.length > 0) {
      return googleRoute;
    }
  } catch (googleErr) {
    console.warn('[Routing] Google Directions API failed:', googleErr.message);
  }

  // 2. Try backend proxy
  try {
    const response = await api.post('/api/route', { waypoints });
    if (response.data && response.data.coordinates) {
      return { ...response.data, source: response.data.source || 'Backend Routing' };
    }
  } catch (backendErr) {
    console.warn('[Routing] Backend routing failed, using mock route:', backendErr.message);
  }

  // 3. Fallback mock
  return generateHighFidelityMockRoute(waypoints);
};

/**
 * Reverse geocode: lat/lng → human-readable address string.
 * Falls back to coordinate string if API fails.
 */
export const geocodeLocation = async (lat, lng) => {
  try {
    return await reverseGeocode(lat, lng);
  } catch (err) {
    return `${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}`;
  }
};

/**
 * Simulates a realistic path wrapping around street grid structures.
 * Used as last-resort fallback when all routing APIs fail.
 */
const generateHighFidelityMockRoute = (waypoints) => {
  if (!waypoints || waypoints.length < 2) {
    return {
      coordinates: [],
      distanceKm: 0,
      durationSec: 0,
      traffic: { status: 'Normal', message: 'N/A' },
      steps: [],
      source: 'Mock',
    };
  }

  const points = [];
  let totalDistance = 0;

  for (let i = 0; i < waypoints.length - 1; i++) {
    const start = waypoints[i];
    const end = waypoints[i + 1];
    const p1 = [start[0], end[1]];

    points.push(start);

    const steps1 = 15;
    for (let k = 1; k <= steps1; k++) {
      const ratio = k / steps1;
      points.push([
        start[0] + (p1[0] - start[0]) * ratio,
        start[1] + (p1[1] - start[1]) * ratio,
      ]);
    }

    const steps2 = 15;
    for (let k = 1; k <= steps2; k++) {
      const ratio = k / steps2;
      points.push([
        p1[0] + (end[0] - p1[0]) * ratio,
        p1[1] + (end[1] - p1[1]) * ratio,
      ]);
    }

    totalDistance += calculateHaversineDistance(start[0], start[1], end[0], end[1]);
  }

  const durationSec = (totalDistance / 40) * 3600;
  const traffic = simulateTraffic(waypoints.map(([lat, lng]) => [lng, lat]));

  const steps = waypoints.map((pt, idx) => {
    if (idx === 0) return { instruction: 'Depart origin location', distance: 0, duration: 0, distanceText: '0 m', durationText: '0 min' };
    return {
      instruction: `Head toward waypoint ${idx + 1}`,
      distance: (totalDistance / waypoints.length) * 1000,
      duration: durationSec / waypoints.length,
      distanceText: `${((totalDistance / waypoints.length)).toFixed(1)} km`,
      durationText: `${Math.ceil(durationSec / waypoints.length / 60)} min`,
    };
  });

  return {
    coordinates: points,
    distanceKm: totalDistance,
    durationSec: durationSec * traffic.factor,
    traffic,
    steps,
    source: 'Dynamic Sim Route',
  };
};
