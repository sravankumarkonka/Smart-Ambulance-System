/**
 * Google Maps Platform API Service
 * 
 * Centralizes all calls to:
 *  - Directions API  (routes, ETA, turn-by-turn steps)
 *  - Geocoding API   (lat/lng ↔ address)
 *  - Places API      (nearby search, text search, place details)
 * 
 * The API key is loaded from VITE_GOOGLE_MAPS_API_KEY env variable.
 * ⚠️ Never hardcode API keys in component files.
 */

const GMAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
const DIRECTIONS_BASE = 'https://maps.googleapis.com/maps/api/directions/json';
const GEOCODING_BASE  = 'https://maps.googleapis.com/maps/api/geocode/json';
const PLACES_BASE     = 'https://maps.googleapis.com/maps/api/place';

// ─────────────────────────────────────────────────────────────────────────────
// Internal helper: decode Google Directions encoded polyline to [lat, lng] array
// ─────────────────────────────────────────────────────────────────────────────
function decodePolyline(encoded) {
  const points = [];
  let index = 0;
  let lat = 0;
  let lng = 0;

  while (index < encoded.length) {
    let b, shift = 0, result = 0;
    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20);
    const dlat = result & 1 ? ~(result >> 1) : result >> 1;
    lat += dlat;

    shift = 0;
    result = 0;
    do {
      b = encoded.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
    } while (b >= 0x20);
    const dlng = result & 1 ? ~(result >> 1) : result >> 1;
    lng += dlng;

    points.push([lat / 1e5, lng / 1e5]);
  }
  return points;
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal helper: calculate Haversine distance in km
// ─────────────────────────────────────────────────────────────────────────────
export function calculateHaversineDistance(lat1, lon1, lat2, lon2) {
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal helper: convert meters → human-readable distance string
// ─────────────────────────────────────────────────────────────────────────────
function metersToKm(meters) {
  return (meters / 1000).toFixed(1);
}

// ─────────────────────────────────────────────────────────────────────────────
// DIRECTIONS API
// Get route between origin and destination with optional waypoints.
// Waypoints format: [lat, lng] pairs.
// Returns: { coordinates, distanceKm, durationSec, steps, traffic, source }
// ─────────────────────────────────────────────────────────────────────────────
export const getDirections = async (origin, destination, waypoints = []) => {
  if (!GMAPS_API_KEY) {
    console.warn('[GoogleMaps] No API key configured. Set VITE_GOOGLE_MAPS_API_KEY in .env');
    return null;
  }

  const originStr = `${origin[0]},${origin[1]}`;
  const destStr   = `${destination[0]},${destination[1]}`;

  const waypointsStr = waypoints.length > 0
    ? waypoints.map(w => `${w[0]},${w[1]}`).join('|')
    : '';

  const params = new URLSearchParams({
    origin: originStr,
    destination: destStr,
    key: GMAPS_API_KEY,
    mode: 'driving',
    departure_time: 'now',   // enables traffic-aware ETA
    traffic_model: 'best_guess',
    alternatives: 'false',
    units: 'metric',
  });

  if (waypointsStr) {
    params.set('waypoints', `via:${waypointsStr.replace(/\|/g, '|via:')}`);
  }

  // Use CORS proxy for direct browser fetch (Directions API doesn't support CORS by default)
  // We proxy through our backend to avoid CORS errors
  let data;
  try {
    const res = await fetch(`https://maps.googleapis.com/maps/api/directions/json?${params.toString()}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    data = await res.json();
  } catch (corsErr) {
    // Fallback: try our backend proxy
    try {
      const backendRes = await fetch(`/api/directions?${params.toString()}`);
      if (!backendRes.ok) throw new Error(`Backend HTTP ${backendRes.status}`);
      data = await backendRes.json();
    } catch (backendErr) {
      console.warn('[GoogleMaps Directions] Both direct and backend requests failed:', backendErr.message);
      return null;
    }
  }

  if (data.status !== 'OK' || !data.routes || data.routes.length === 0) {
    console.warn('[GoogleMaps Directions] API error:', data.status, data.error_message);
    return null;
  }

  const route = data.routes[0];
  const leg = route.legs.reduce((acc, l) => ({
    distance: acc.distance + l.distance.value,
    duration: acc.duration + (l.duration_in_traffic?.value ?? l.duration.value),
    steps: [...acc.steps, ...l.steps],
  }), { distance: 0, duration: 0, steps: [] });

  // Decode the overview polyline
  const coordinates = decodePolyline(route.overview_polyline.points);

  // Parse turn-by-turn steps
  const steps = leg.steps.map(s => ({
    instruction: s.html_instructions.replace(/<[^>]*>/g, '').trim(),
    distance: s.distance.value,
    duration: s.duration.value,
    distanceText: s.distance.text,
    durationText: s.duration.text,
    maneuver: s.maneuver || '',
  }));

  // Traffic status derived from difference between normal and traffic-aware duration
  const durationRatio = leg.duration / (leg.duration || 1);
  let trafficStatus = 'Normal';
  let trafficMessage = 'Clear roads ahead.';
  if (durationRatio > 1.5) { trafficStatus = 'Heavy'; trafficMessage = 'Heavy traffic. Rerouting may help.'; }
  else if (durationRatio > 1.2) { trafficStatus = 'Moderate'; trafficMessage = 'Moderate traffic delays.'; }

  return {
    coordinates,
    distanceKm: parseFloat(metersToKm(leg.distance)),
    durationSec: leg.duration,
    steps,
    traffic: { status: trafficStatus, message: trafficMessage },
    summary: route.summary,
    source: 'Google Directions API',
    warnings: route.warnings || [],
  };
};

// ─────────────────────────────────────────────────────────────────────────────
// GEOCODING API — Lat/Lng → Address
// ─────────────────────────────────────────────────────────────────────────────
export const reverseGeocode = async (lat, lng) => {
  if (!GMAPS_API_KEY) return null;

  try {
    const params = new URLSearchParams({
      latlng: `${lat},${lng}`,
      key: GMAPS_API_KEY,
      result_type: 'street_address|sublocality|locality',
    });

    const res = await fetch(`${GEOCODING_BASE}?${params.toString()}`);
    const data = await res.json();

    if (data.status !== 'OK' || !data.results || data.results.length === 0) {
      return `${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}`;
    }

    return data.results[0].formatted_address;
  } catch (err) {
    console.warn('[GoogleMaps Geocoding] Reverse geocode failed:', err.message);
    return `${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}`;
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// GEOCODING API — Address → Lat/Lng
// ─────────────────────────────────────────────────────────────────────────────
export const geocodeAddress = async (address) => {
  if (!GMAPS_API_KEY || !address) return null;

  try {
    const params = new URLSearchParams({
      address,
      key: GMAPS_API_KEY,
    });

    const res = await fetch(`${GEOCODING_BASE}?${params.toString()}`);
    const data = await res.json();

    if (data.status !== 'OK' || !data.results || data.results.length === 0) return null;

    const loc = data.results[0].geometry.location;
    return {
      lat: loc.lat,
      lng: loc.lng,
      formattedAddress: data.results[0].formatted_address,
    };
  } catch (err) {
    console.warn('[GoogleMaps Geocoding] Forward geocode failed:', err.message);
    return null;
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// PLACES API — Nearby Search
// type: 'hospital' | 'police' | 'pharmacy' | 'blood_bank'
// Returns array of places with name, coords, rating, vicinity, open_now
// ─────────────────────────────────────────────────────────────────────────────
export const searchNearbyPlaces = async (lat, lng, type = 'hospital', radius = 5000) => {
  if (!GMAPS_API_KEY) return [];

  try {
    const params = new URLSearchParams({
      location: `${lat},${lng}`,
      radius,
      type,
      key: GMAPS_API_KEY,
    });

    const res = await fetch(`${PLACES_BASE}/nearbysearch/json?${params.toString()}`);
    const data = await res.json();

    if (data.status !== 'OK' && data.status !== 'ZERO_RESULTS') {
      console.warn('[GoogleMaps Places] Nearby search error:', data.status);
      return [];
    }

    return (data.results || []).map(place => ({
      placeId: place.place_id,
      name: place.name,
      vicinity: place.vicinity,
      latitude: place.geometry.location.lat,
      longitude: place.geometry.location.lng,
      rating: place.rating || null,
      userRatingsTotal: place.user_ratings_total || 0,
      openNow: place.opening_hours?.open_now ?? null,
      types: place.types || [],
      icon: place.icon,
    }));
  } catch (err) {
    console.warn('[GoogleMaps Places] Nearby search failed:', err.message);
    return [];
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// PLACES API — Text Search (for hospital name search)
// ─────────────────────────────────────────────────────────────────────────────
export const searchPlacesByText = async (query, lat, lng) => {
  if (!GMAPS_API_KEY || !query) return [];

  try {
    const params = new URLSearchParams({
      query,
      key: GMAPS_API_KEY,
    });
    if (lat && lng) {
      params.set('location', `${lat},${lng}`);
      params.set('radius', '20000');
    }

    const res = await fetch(`${PLACES_BASE}/textsearch/json?${params.toString()}`);
    const data = await res.json();

    if (data.status !== 'OK' && data.status !== 'ZERO_RESULTS') return [];

    return (data.results || []).slice(0, 10).map(place => ({
      placeId: place.place_id,
      name: place.name,
      address: place.formatted_address,
      latitude: place.geometry.location.lat,
      longitude: place.geometry.location.lng,
      rating: place.rating || null,
      userRatingsTotal: place.user_ratings_total || 0,
      openNow: place.opening_hours?.open_now ?? null,
      types: place.types || [],
    }));
  } catch (err) {
    console.warn('[GoogleMaps Places] Text search failed:', err.message);
    return [];
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// PLACES API — Place Details (phone, hours, website, rating)
// ─────────────────────────────────────────────────────────────────────────────
export const getPlaceDetails = async (placeId) => {
  if (!GMAPS_API_KEY || !placeId) return null;

  try {
    const params = new URLSearchParams({
      place_id: placeId,
      fields: 'name,formatted_phone_number,opening_hours,rating,website,formatted_address,geometry',
      key: GMAPS_API_KEY,
    });

    const res = await fetch(`${PLACES_BASE}/details/json?${params.toString()}`);
    const data = await res.json();

    if (data.status !== 'OK' || !data.result) return null;

    const r = data.result;
    return {
      name: r.name,
      phone: r.formatted_phone_number || null,
      address: r.formatted_address || null,
      website: r.website || null,
      rating: r.rating || null,
      openNow: r.opening_hours?.open_now ?? null,
      weekdayText: r.opening_hours?.weekday_text || [],
      latitude: r.geometry?.location?.lat,
      longitude: r.geometry?.location?.lng,
    };
  } catch (err) {
    console.warn('[GoogleMaps Places] Get details failed:', err.message);
    return null;
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// PLACES API — Autocomplete suggestions for address input
// Returns array of { description, placeId }
// ─────────────────────────────────────────────────────────────────────────────
export const getAutocompleteSuggestions = async (input, lat, lng) => {
  if (!GMAPS_API_KEY || !input || input.length < 3) return [];

  try {
    const params = new URLSearchParams({
      input,
      key: GMAPS_API_KEY,
      types: 'geocode|establishment',
    });
    if (lat && lng) {
      params.set('location', `${lat},${lng}`);
      params.set('radius', '50000');
    }

    const res = await fetch(`${PLACES_BASE}/autocomplete/json?${params.toString()}`);
    const data = await res.json();

    if (data.status !== 'OK' && data.status !== 'ZERO_RESULTS') return [];

    return (data.predictions || []).slice(0, 5).map(p => ({
      description: p.description,
      placeId: p.place_id,
      mainText: p.structured_formatting?.main_text || p.description,
      secondaryText: p.structured_formatting?.secondary_text || '',
    }));
  } catch (err) {
    console.warn('[GoogleMaps Places] Autocomplete failed:', err.message);
    return [];
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// Helper: Open Google Maps navigation in browser/app
// ─────────────────────────────────────────────────────────────────────────────
export const openGoogleMapsNavigation = (destLat, destLng, label = '') => {
  const url = `https://www.google.com/maps/dir/?api=1&destination=${destLat},${destLng}&travelmode=driving${label ? `&destination_place_id=${encodeURIComponent(label)}` : ''}`;
  window.open(url, '_blank');
};

// ─────────────────────────────────────────────────────────────────────────────
// Helper: Check if Google Maps API key is configured
// ─────────────────────────────────────────────────────────────────────────────
export const isGoogleMapsConfigured = () => {
  return !!GMAPS_API_KEY && GMAPS_API_KEY !== 'undefined';
};
