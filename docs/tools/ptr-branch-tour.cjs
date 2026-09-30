/* Deterministic branch tours over existing replay evidence. */
function candidatePath(tick, candidateId) {
  const byId = new Map(tick.multiTick.candidates.map(candidate => [candidate.id, candidate]));
  const path = [];
  const seen = new Set();
  let candidate = byId.get(candidateId);
  while (candidate) {
    if (seen.has(candidate.id)) throw new Error('Cycle in PTR candidate tree');
    seen.add(candidate.id);
    path.unshift(candidate);
    candidate = candidate.parent < 0 ? null : byId.get(candidate.parent);
  }
  return path;
}

function planBranchTour(trace, item) {
  const ranked = trace.map((tick, index) => {
    const candidates = tick.multiTick.candidates;
    const unique = new Set(candidates.map(c => [c.x, c.y, c.z].map(n => n.toFixed(6)).join(','))).size;
    const spread = candidates.length ? Math.hypot(...['x', 'y', 'z'].map(axis =>
      Math.max(...candidates.map(c => c[axis])) - Math.min(...candidates.map(c => c[axis]))
    )) : 0;
    const centrality = trace.length === 1 ? 1 : 1 - Math.abs(index / (trace.length - 1) - .5);
    return {index, score: Math.log1p(unique) * (.5 + spread) * centrality};
  }).sort((a, b) => b.score - a.score || a.index - b.index);
  const index = item.tourTick === undefined ? ranked[0].index : trace.findIndex(tick => tick.tick === item.tourTick);
  if (index < 0) throw new Error(`Missing tour tick for ${item.id}`);
  const tick = trace[index];
  const candidates = tick.multiTick.candidates;
  if (!candidates.length) throw new Error(`No branch candidates for ${item.id}`);
  const winner = candidates.find(candidate => candidate.selected);
  const parents = new Set(candidates.map(candidate => candidate.parent));
  const signature = candidate => candidatePath(tick, candidate.id).map(c =>
    [c.x, c.y, c.z].map(n => n.toFixed(6)).join(',')
  ).join('|');
  const paths = new Map();
  for (const candidate of candidates) {
    if (!parents.has(candidate.id) || candidate === winner) paths.set(signature(candidate), candidate);
  }
  // Preserve the exact selected candidate even if another input has the same trajectory.
  if (winner) paths.set(signature(winner), winner);
  const pool = [...paths.values()];
  const selected = winner ? [winner] : [pool[0]];
  const distance = (a, b) => {
    const aPath = candidatePath(tick, a.id), bPath = candidatePath(tick, b.id);
    let distance = 0;
    for (let i = 0; i < Math.max(aPath.length, bPath.length); i++) {
      const ac = aPath[Math.min(i, aPath.length - 1)], bc = bPath[Math.min(i, bPath.length - 1)];
      distance += (ac.x - bc.x) ** 2 + (ac.y - bc.y) ** 2 + (ac.z - bc.z) ** 2;
    }
    return distance;
  };
  // Spread the tour over visibly different paths; every candidate remains in the tree.
  while (selected.length < Math.min(7, pool.length)) {
    const remaining = pool.filter(candidate => !selected.includes(candidate));
    remaining.sort((a, b) =>
      Math.min(...selected.map(c => distance(b, c))) - Math.min(...selected.map(c => distance(a, c))) || a.id - b.id
    );
    selected.push(remaining[0]);
  }
  const visitIds = selected.filter(candidate => candidate !== winner).map(candidate => candidate.id);
  if (winner) visitIds.push(winner.id);
  const stepSeconds = 1.25;
  const introSeconds = .6, outroSeconds = .6;
  const replaySeconds = trace.length === 1 ? 0 : item.replaySeconds ?? Math.max(5, Math.min(9, trace.length * .075));
  const pauseAt = trace.length === 1 ? 0 : replaySeconds * (index + 1) / trace.length;
  const tourSeconds = introSeconds + visitIds.length * stepSeconds + outroSeconds;
  return {index, tick: tick.tick, visitIds, winnerId: winner?.id ?? null, candidateCount: candidates.length,
    uniquePathCount: paths.size, stepSeconds, introSeconds, outroSeconds, replaySeconds, pauseAt, tourSeconds,
    durationSeconds: replaySeconds + tourSeconds};
}

module.exports = {candidatePath, planBranchTour};
