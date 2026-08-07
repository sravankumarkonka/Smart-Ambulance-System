/**
 * Dynamic A* (A-star) Pathfinding Algorithm
 * Finds the shortest path in a graph while taking traffic factors and a heuristic (Haversine distance) into account.
 * 
 * Graph structure:
 * {
 *   nodeId: { neighborId: baseWeight, ... },
 *   ...
 * }
 * 
 * Nodes coordinates structure:
 * {
 *   nodeId: { latitude, longitude },
 *   ...
 * }
 */

import { calculateHaversineDistance } from '../utils/haversine.js';


export function dynamicAstar(graph, nodes, startNode, endNode, trafficFactors = {}) {
  // Heuristic function: straight-line distance to endNode
  const h = (nodeId) => {
    const n1 = nodes[nodeId];
    const n2 = nodes[endNode];
    if (!n1 || !n2) return 0;
    return calculateHaversineDistance(n1.latitude, n1.longitude, n2.latitude, n2.longitude);
  };

  const openSet = new Set([startNode]);
  const cameFrom = {};

  const gScore = {};
  const fScore = {};

  for (const node of Object.keys(graph)) {
    gScore[node] = Infinity;
    fScore[node] = Infinity;
  }

  gScore[startNode] = 0;
  fScore[startNode] = h(startNode);

  while (openSet.size > 0) {
    // Find node in openSet with lowest fScore
    let current = null;
    let lowestF = Infinity;

    for (const node of openSet) {
      if (fScore[node] < lowestF) {
        lowestF = fScore[node];
        current = node;
      }
    }

    if (current === endNode) {
      // Reconstruct path
      const path = [];
      let temp = current;
      while (temp !== undefined) {
        path.unshift(temp);
        temp = cameFrom[temp];
      }
      return {
        path,
        distance: gScore[endNode]
      };
    }

    openSet.delete(current);

    const neighbors = graph[current] || {};
    for (const neighbor of Object.keys(neighbors)) {
      // Edge key for traffic factor (e.g. "node1-node2" or "node2-node1")
      const edgeKey1 = `${current}-${neighbor}`;
      const edgeKey2 = `${neighbor}-${current}`;
      const trafficFactor = trafficFactors[edgeKey1] || trafficFactors[edgeKey2] || 1.0;

      // Dynamic weight = base weight * traffic factor
      const dynamicWeight = neighbors[neighbor] * trafficFactor;
      const tentativeGScore = gScore[current] + dynamicWeight;

      if (tentativeGScore < gScore[neighbor]) {
        cameFrom[neighbor] = current;
        gScore[neighbor] = tentativeGScore;
        fScore[neighbor] = tentativeGScore + h(neighbor);

        if (!openSet.has(neighbor)) {
          openSet.add(neighbor);
        }
      }
    }
  }

  return {
    path: [],
    distance: -1
  };
}
