/**
 * Dijkstra's Shortest Path Algorithm
 * Calculates the shortest path between startNode and endNode in a weighted graph.
 * 
 * Graph structure:
 * {
 *   nodeId: { neighborId: weight, ... },
 *   ...
 * }
 */
export function dijkstra(graph, startNode, endNode) {
  const distances = {};
  const prev = {};
  const queue = new Set();

  for (const node of Object.keys(graph)) {
    distances[node] = Infinity;
    prev[node] = null;
    queue.add(node);
  }

  distances[startNode] = 0;

  while (queue.size > 0) {
    // Find node with minimum distance in queue
    let minNode = null;
    let minDistance = Infinity;

    for (const node of queue) {
      if (distances[node] < minDistance) {
        minDistance = distances[node];
        minNode = node;
      }
    }

    if (minNode === null || minNode === endNode) {
      break;
    }

    queue.delete(minNode);

    const neighbors = graph[minNode] || {};
    for (const neighbor of Object.keys(neighbors)) {
      if (!queue.has(neighbor)) continue;

      const alt = distances[minNode] + neighbors[neighbor];
      if (alt < distances[neighbor]) {
        distances[neighbor] = alt;
        prev[neighbor] = minNode;
      }
    }
  }

  // Reconstruct path
  const path = [];
  let curr = endNode;
  if (prev[curr] !== undefined || curr === startNode) {
    while (curr !== null) {
      path.unshift(curr);
      curr = prev[curr];
    }
  }

  return {
    path: path.length > 1 || path[0] === startNode ? path : [],
    distance: distances[endNode] === Infinity ? -1 : distances[endNode]
  };
}
