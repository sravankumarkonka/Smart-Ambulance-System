import { getTrafficAwareRoute } from '../services/routingService.js';

export const getRoute = async (req, res) => {
  try {
    const { waypoints, useAstar } = req.body;

    if (!waypoints || !Array.isArray(waypoints) || waypoints.length < 2) {
      return res.status(400).json({ error: 'Waypoints list must contain at least 2 coordinate points [[lat, lng], ...]' });
    }

    const useAstarBool = useAstar !== false; // defaults to true
    const route = await getTrafficAwareRoute(waypoints, useAstarBool);

    return res.status(200).json(route);
  } catch (error) {
    console.error('Error generating route:', error);
    return res.status(500).json({ error: 'Failed to calculate optimal route: ' + error.message });
  }
};
