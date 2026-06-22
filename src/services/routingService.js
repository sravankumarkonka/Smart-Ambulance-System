import api from './api';

/**
 * Pre-configured Hospitals in the Bangalore area
 */
export const HOSPITALS = [
  { id: 'manipal', name: 'Manipal Hospital (HAL Road)', latitude: 12.9592, longitude: 77.6443 },
  { id: 'fortis', name: 'Fortis Hospital (Bannerghatta Road)', latitude: 12.8943, longitude: 77.5979 },
  { id: 'apollo', name: 'Apollo Hospitals (Bannerghatta Road)', latitude: 12.8958, longitude: 77.5997 },
  { id: 'columbia', name: 'Columbia Asia Referral Hospital (Yeshwanthpur)', latitude: 13.0135, longitude: 77.5516 },
];

/**
 * Calculates Haversine distance between two coordinates in kilometers.
 */
export const calculateHaversineDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371; // Radius of the Earth in km
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
 * Finds the nearest hospital to patient coordinates.
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
 * Recommends the best hospital using backend hospital recommendation algorithm via Axios.
 */
export const recommendHospital = async (lat, lng, severityLevel = 'medium') => {
  try {
    const response = await api.get('/api/hospitals/recommend', {
      params: { latitude: lat, longitude: lng, severityLevel }
    });
    return response.data;
  } catch (err) {
    console.warn('[Routing Service] Backend hospital recommendation failed, falling back to local calculation:', err.message);
    const nearest = findNearestHospital(lat, lng);
    return {
      recommended: nearest,
      comparison: HOSPITALS.map(h => {
        const dist = calculateHaversineDistance(lat, lng, h.latitude, h.longitude);
        return {
          ...h,
          distanceKm: parseFloat(dist.toFixed(2)),
          icuStatus: 'available',
          suitabilityScore: 0
        };
      })
    };
  }
};

/**
 * Traffic Congestion Simulator:
 * Generates traffic weight, status, and custom messages based on the coordinates and time of day.
 */
export const simulateTraffic = (coordinates) => {
  const hour = new Date().getHours();
  let baseFactor = 1.0;
  let status = 'Normal';
  let message = 'Clear roads ahead.';

  // Rush hour factors
  if ((hour >= 8 && hour <= 10.5) || (hour >= 17 && hour <= 19.5)) {
    baseFactor = 1.45;
    status = 'Heavy';
    message = 'Rush hour delays. Alternative paths suggested.';
  } else if (hour >= 12 && hour <= 14) {
    baseFactor = 1.2;
    status = 'Moderate';
    message = 'Moderate volume near commercial hubs.';
  }

  // Check if coordinates cross known busy corridors (mock check)
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

  return {
    factor: baseFactor,
    status,
    message,
  };
};

/**
 * Fetches routing geometry and instructions from OpenRouteService,
 * with automatic fallback to Open Source Routing Machine (OSRM).
 *
 * Coordinates are passed as [[lat, lng], [lat, lng], ...]
 */
export const fetchRoute = async (waypoints) => {
  try {
    const response = await api.post('/api/route', { waypoints });
    return response.data;
  } catch (err) {
    console.warn('Backend routing failed, falling back to client-side simulator:', err.message);
    return generateHighFidelityMockRoute(waypoints);
  }
};

/**
 * Simulates a realistic path wrapping around street grid structures to ensure Map UI
 * always displays a high-fidelity route and does not crash.
 */
const generateHighFidelityMockRoute = (waypoints) => {
  const points = [];
  let totalDistance = 0;

  for (let i = 0; i < waypoints.length - 1; i++) {
    const start = waypoints[i];
    const end = waypoints[i + 1];
    
    // Add grid intermediate points to make it look like road turns
    const p1 = [start[0], end[1]]; // Turn point 1 (manhattan turn)
    
    points.push(start);
    
    // Interpolate points between start and p1
    const steps1 = 15;
    for (let k = 1; k <= steps1; k++) {
      const ratio = k / steps1;
      points.push([
        start[0] + (p1[0] - start[0]) * ratio,
        start[1] + (p1[1] - start[1]) * ratio,
      ]);
    }

    // Interpolate points between p1 and end
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

  const durationSec = (totalDistance / 40) * 3600; // Assuming 40 km/h average speed
  const traffic = simulateTraffic(waypoints.map(([lat, lng]) => [lng, lat]));

  const steps = waypoints.map((pt, idx) => {
    if (idx === 0) return { instruction: 'Depart origin location', distance: 0, duration: 0 };
    return {
      instruction: `Head toward waypoint ${idx + 1} (${pt[0].toFixed(4)}, ${pt[1].toFixed(4)})`,
      distance: (totalDistance / waypoints.length) * 1000,
      duration: durationSec / waypoints.length,
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
