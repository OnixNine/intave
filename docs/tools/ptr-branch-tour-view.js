/* Injected into the PTR viewer's module scope by the GIF exporter. */
renderer.setAnimationLoop(null);
scene.background = new THREE.Color('#0a0a0a');
referencePlane.material.color.set('#0a0a0a');
referencePlane.material.toneMapped = false;
controls.enableDamping = false;
controls.maxDistance = 50;
const exportMaterials = new Set();
for (const tick of trace) for (const block of tick.environment.blocks) {
  const key = block[0] + '|' + (block[8] || '');
  if (!exportMaterials.has(key)) { exportMaterials.add(key); blockFaces(block[0], block[8] || ''); }
}
const exportOffset = new THREE.Vector3(...exportConfig.camera);
const focusOffset = new THREE.Vector3(...(exportConfig.focusCamera ?? exportConfig.camera));
const baseAzimuth = Math.atan2(exportOffset.x, exportOffset.z);
const focusAzimuth = Math.atan2(focusOffset.x, focusOffset.z);
const orbitAzimuth = Math.atan2(Math.sin(focusAzimuth - baseAzimuth), Math.cos(focusAzimuth - baseAzimuth));
const baseElevation = Math.asin(exportOffset.y / exportOffset.length());
const focusElevation = Math.asin(focusOffset.y / focusOffset.length());
// Sample the recording instead of integrating per rendered frame, so scrubbing,
// repeated captures, and playback at different frame rates use the same camera.
const cameraTrack = [new THREE.Vector3(...originOffset(0)).add(new THREE.Vector3(0, trace[0].environment.playerHeight / 2, 0)),
  ...trace.map((tick, index) => new THREE.Vector3(...originOffset(index)).add(
    new THREE.Vector3(tick.actualX, tick.actualY + tick.environment.playerHeight / 2, tick.actualZ)))];
const smoothTrack = cameraTrack.map((point, index) => {
  const result = new THREE.Vector3();
  let total = 0;
  for (let offset = -3; offset <= 3; offset++) {
    const weight = 4 - Math.abs(offset);
    result.addScaledVector(cameraTrack[Math.max(0, Math.min(cameraTrack.length - 1, index + offset))], weight);
    total += weight;
  }
  return result.divideScalar(total);
});
const followCurve = new THREE.CatmullRomCurve3(smoothTrack, false, 'catmullrom', .25);
const cameraAt = sample => followCurve.getPoint(THREE.MathUtils.clamp(sample / trace.length, 0, 1));
const cameraHeadings = [];
for (let index = 0; index < cameraTrack.length; index++) {
  const motion = smoothTrack[Math.min(smoothTrack.length - 1, index + 2)].clone()
    .sub(smoothTrack[Math.max(0, index - 2)]);
  const previous = cameraHeadings.at(-1) ?? Math.atan2(trace[0].actualX, trace[0].actualZ);
  const heading = Math.hypot(motion.x, motion.z) > .04 ? Math.atan2(motion.x, motion.z) : previous;
  cameraHeadings.push(previous + Math.atan2(Math.sin(heading - previous), Math.cos(heading - previous)));
}
const exportRecorded = new THREE.BufferGeometry().setFromPoints(trace.map((tick, index) => {
  const origin = originOffset(index);
  return new THREE.Vector3(origin[0] + tick.actualX, origin[1] + tick.actualY + .04, origin[2] + tick.actualZ);
}));
const exportTrail = new THREE.Line(exportRecorded, new THREE.LineBasicMaterial({color: 0x75c9ff, transparent: true, opacity: .65}));
scene.add(exportTrail);
const branchSvg = document.getElementById('export-tree');
const tourGroup = new THREE.Group();
scene.add(tourGroup);
const tourBox = addHitbox([0, 0, 0], 1, 1, 0xffd482, 1, tourGroup);
tourBox.material.depthTest = false;
tourBox.renderOrder = 110;
const tourVolume = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshBasicMaterial({
  color: 0xffd482, transparent: true, opacity: .09, depthTest: false, depthWrite: false
}));
tourVolume.renderOrder = 105;
tourGroup.add(tourVolume);
const tourLine = new THREE.Line(new THREE.BufferGeometry(), new THREE.LineBasicMaterial({
  color: 0xffd482, transparent: true, opacity: 1, depthTest: false, depthWrite: false
}));
tourLine.renderOrder = 109;
tourGroup.add(tourLine);
let treeTick = -1, activeKey = '', treeNodes = new Map(), treeEdges = [];
let cursor, rootDot;
let branchLabels;
let lastInspection = null;

function describeInputs(candidate) {
  const parts = candidate.config.split(' · ');
  const keys = parts.find(part => part.startsWith('keys='))?.slice(5) ?? 'none';
  const modifiers = parts.filter(part => !part.startsWith('keys='))
    .map(part => part.charAt(0).toUpperCase() + part.slice(1));
  return {keys: keys === 'none' ? 'No keys' : keys.split('').join(' + '),
    modifiers: modifiers.join(' ')};
}

function renderExportFrame(focused) {
  const volumes = possibilitiesGroup.children.find(object => object.isInstancedMesh);
  if (volumes) {
    volumes.material.userData.normalOpacity ??= volumes.material.opacity;
    volumes.material.opacity = focused
      ? Math.min(volumes.material.userData.normalOpacity, .18 / volumes.count)
      : volumes.material.userData.normalOpacity;
  }
  renderer.render(scene, camera);
}

function branchChain(tick, id) {
  const byId = new Map(tick.multiTick.candidates.map(candidate => [candidate.id, candidate]));
  const path = [], seen = new Set();
  let candidate = byId.get(id);
  while (candidate) {
    if (seen.has(candidate.id)) throw new Error('Cycle in PTR candidate tree');
    seen.add(candidate.id); path.unshift(candidate);
    candidate = candidate.parent < 0 ? null : byId.get(candidate.parent);
  }
  return path;
}

function drawBranchTree(tick) {
  if (treeTick === state.tickIndex) return;
  treeTick = state.tickIndex;
  branchSvg.replaceChildren(); treeNodes = new Map(); treeEdges = [];
  const candidates = tick.multiTick.candidates;
  const depthCount = Math.max(1, ...candidates.map(candidate => candidate.depth + 1));
  const treeTop = 76, treeBottom = branchSvg.viewBox.baseVal.height - 24;
  const root = {x: 24, y: (treeTop + treeBottom) / 2};
  for (let depth = 0; depth < depthCount; depth++) {
    const layer = candidates.filter(candidate => candidate.depth === depth);
    layer.forEach((candidate, index) => treeNodes.set(candidate.id, {
      candidate, x: 24 + (depth + 1) * 308 / depthCount,
      y: layer.length === 1 ? root.y : THREE.MathUtils.lerp(treeTop, treeBottom, index / (layer.length - 1)),
      labelAlways: layer.length <= 8,
      radius: layer.length <= 12 ? 3.4 : 1.65
    }));
  }
  for (const node of treeNodes.values()) {
    const parent = treeNodes.get(node.candidate.parent) ?? root;
    const bend = (node.x - parent.x) * .48;
    const el = element('path', {d: 'M ' + parent.x + ' ' + parent.y + ' C ' + (parent.x + bend) + ' ' + parent.y + ' ' + (node.x - bend) + ' ' + node.y + ' ' + node.x + ' ' + node.y,
      fill: 'none', stroke: '#53657c', 'stroke-width': .8, opacity: .22});
    branchSvg.append(el);
    treeEdges.push({el, id: node.candidate.id, parent, node, bend});
  }
  rootDot = element('circle', {cx: root.x, cy: root.y, r: 4, fill: '#93a7be'});
  branchSvg.append(rootDot);
  for (const node of treeNodes.values()) {
    node.el = element('circle', {cx: node.x, cy: node.y, r: node.radius,
      fill: node.candidate.loss <= .01 ? '#7194b1' : '#b67479'});
    branchSvg.append(node.el);
  }
  cursor = element('circle', {r: 3.5, fill: '#ffd482'});
  branchLabels = element('g', {'aria-label': 'Branch inputs'});
  for (let depth = 0; depth < depthCount; depth++) {
    const label = element('text', {x: 24 + (depth + 1) * 308 / depthCount, y: 38,
      'text-anchor': 'end', fill: '#a9b4c1', 'font-size': 18});
    label.textContent = `Tick ${depth + 1}`;
    branchLabels.append(label);
  }
  for (const node of treeNodes.values()) {
    const input = describeInputs(node.candidate);
    const group = element('g', {'data-branch-label': node.candidate.id});
    const keys = element('text', {x: node.x - 12, y: node.y - 3, 'text-anchor': 'end', 'font-size': 17, 'font-weight': 600});
    keys.textContent = input.keys;
    group.append(keys);
    if (input.modifiers) {
      const modifiers = element('text', {x: node.x - 12, y: node.y + 13, 'text-anchor': 'end', 'font-size': 12});
      modifiers.textContent = input.modifiers;
      group.append(modifiers);
    }
    node.label = group;
    branchLabels.append(group);
  }
  branchSvg.append(cursor, branchLabels);
}

function highlightBranch(path, segment, progress, color) {
  const ids = new Set(path.map(candidate => candidate.id));
  for (const edge of treeEdges) {
    const active = ids.has(edge.id);
    edge.el.setAttribute('stroke', active ? color : '#53657c');
    edge.el.setAttribute('stroke-width', active ? '2.3' : '.8');
    edge.el.setAttribute('opacity', active ? '1' : '.2');
  }
  for (const node of treeNodes.values()) {
    const active = ids.has(node.candidate.id);
    node.el.setAttribute('fill', active ? color : node.candidate.loss <= .01 ? '#7194b1' : '#b67479');
    node.el.setAttribute('opacity', active ? '1' : '.55');
    node.el.setAttribute('r', active ? Math.max(3.5, node.radius) : node.radius);
    node.label.style.display = active || node.labelAlways ? '' : 'none';
    node.label.setAttribute('fill', active ? color : '#8994a2');
  }
  // Put highlighted edges above the dense diagnostic fan, then its nodes above the edges.
  for (const edge of treeEdges) if (ids.has(edge.id)) branchSvg.append(edge.el);
  for (const node of treeNodes.values()) if (ids.has(node.candidate.id)) branchSvg.append(node.el);
  branchSvg.append(rootDot, cursor, branchLabels);
  rootDot.setAttribute('fill', color);
  const node = treeNodes.get(path.at(-1)?.id);
  cursor.style.display = node ? '' : 'none';
  if (!node) return;
  cursor.setAttribute('fill', color);
  const edge = treeEdges.find(edge => edge.id === path[segment].id);
  const t = progress, s = 1 - t;
  const x = s ** 3 * edge.parent.x + 3 * s * s * t * (edge.parent.x + edge.bend) + 3 * s * t * t * (edge.node.x - edge.bend) + t ** 3 * edge.node.x;
  const y = s ** 3 * edge.parent.y + 3 * s * s * t * edge.parent.y + 3 * s * t * t * edge.node.y + t ** 3 * edge.node.y;
  cursor.setAttribute('cx', x); cursor.setAttribute('cy', y);
}

window.ptrExport = {
  inspect: () => lastInspection,
  draw(frame, totalFrames) {
    const fraction = totalFrames <= 1 ? 1 : frame / (totalFrames - 1);
    const time = fraction * exportTour.durationSeconds;
    const touring = time >= exportTour.pauseAt && time <= exportTour.pauseAt + exportTour.tourSeconds;
    const tourTime = Math.max(0, time - exportTour.pauseAt);
    let candidateId, branchProgress = 1, zoom = 0;
    if (touring) {
      state.tickIndex = exportTour.index; state.progress = 1;
      const visitTime = Math.max(0, tourTime - exportTour.introSeconds);
      const visit = Math.min(exportTour.visitIds.length - 1, Math.floor(visitTime / exportTour.stepSeconds));
      candidateId = exportTour.visitIds[visit];
      const local = (visitTime - visit * exportTour.stepSeconds) / exportTour.stepSeconds;
      branchProgress = Math.max(0, Math.min(1, (local - .08) / .7));
      if (tourTime < exportTour.introSeconds) branchProgress = 0;
      const fadeIn = Math.min(1, tourTime / exportTour.introSeconds);
      const fadeOut = Math.min(1, (exportTour.tourSeconds - tourTime) / exportTour.outroSeconds);
      zoom = Math.max(0, Math.min(fadeIn, fadeOut));
      zoom = zoom * zoom * (3 - 2 * zoom);
    } else {
      const replayTime = time < exportTour.pauseAt ? time : time - exportTour.tourSeconds;
      const sample = exportTour.replaySeconds ? Math.min(trace.length, replayTime / exportTour.replaySeconds * trace.length) : 0;
      state.tickIndex = Math.min(trace.length - 1, Math.floor(sample));
      state.progress = sample >= trace.length ? 1 : sample - state.tickIndex;
      if (frame === totalFrames - 1) state.progress = 1;
    }
    const tick = trace[state.tickIndex];
    resetPossibilityFocus(tick);
    render(false);
    drawBranchTree(tick);
    candidateId ??= tick.multiTick.candidates.find(candidate => candidate.selected)?.id ?? tick.multiTick.candidates[0]?.id;
    const path = branchChain(tick, candidateId);
    const winner = path.at(-1)?.selected ?? false;
    const color = winner ? '#67e0a3' : '#ffd482';
    const scaled = branchProgress * path.length;
    const segment = Math.min(path.length - 1, Math.floor(scaled));
    const progress = branchProgress >= 1 ? 1 : scaled - segment;
    highlightBranch(path, segment, progress, color);
    tourGroup.visible = touring && path.length > 0;
    const origin = originOffset(state.tickIndex);
    const points = [[0, 0, 0], ...path.map(candidate => [candidate.x, candidate.y, candidate.z])];
    const a = points[Math.max(0, segment)], b = points[Math.max(0, segment + 1)];
    const hitboxPosition = a.map((value, axis) => origin[axis] + THREE.MathUtils.lerp(value, b[axis], progress));
    const height = tick.environment.playerHeight, width = tick.environment.playerWidth;
    tourBox.scale.set(width, height, width); tourVolume.scale.copy(tourBox.scale);
    setHitboxPosition(tourBox, hitboxPosition, height); tourVolume.position.copy(tourBox.position);
    tourBox.material.color.set(color); tourVolume.material.color.set(color); tourLine.material.color.set(color);
    const key = state.tickIndex + ':' + candidateId;
    if (activeKey !== key) {
      activeKey = key;
      tourLine.geometry.dispose();
      tourLine.geometry = new THREE.BufferGeometry().setFromPoints(points.map(point => new THREE.Vector3(
        origin[0] + point[0], origin[1] + point[1] + .045, origin[2] + point[2]
      )));
    }
    worldActors.recordedHitbox.material.color.setHex(0x75c9ff);
    worldActors.recordedArrow.setColor(new THREE.Color(0x75c9ff));
    for (const hitbox of [worldActors.player.hitbox, worldActors.recordedHitbox]) {
      hitbox.material.depthTest = false; hitbox.material.opacity = 1; hitbox.renderOrder = 100;
    }
    exportRecorded.setDrawRange(Math.max(0, state.tickIndex - 35), Math.min(state.tickIndex + 1, 36));
    // Fit the complete tour once, so the camera does not chase individual alternatives.
    const tourCandidates = trace[exportTour.index].multiTick.candidates;
    const tourBounds = new THREE.Box3();
    tourBounds.expandByPoint(new THREE.Vector3(0, height / 2, 0));
    for (const candidate of tourCandidates) tourBounds.expandByPoint(new THREE.Vector3(candidate.x, candidate.y + height / 2, candidate.z));
    const center = tourBounds.getCenter(new THREE.Vector3()).add(new THREE.Vector3(...origin));
    const sample = state.tickIndex + state.progress;
    const subject = worldActors.recordedHitbox.position.clone();
    const lag = cameraAt(sample - 1.5).sub(subject).clampLength(0, .65);
    const lead = cameraAt(sample + 1.5).sub(subject).clampLength(0, .45);
    const motionWeight = trace.length === 1 ? 0 : 1 - zoom;
    const cameraAnchor = subject.clone().lerp(center, zoom).addScaledVector(lag, motionWeight * .8);
    const cameraTarget = subject.clone().lerp(center, zoom).addScaledVector(lead, motionWeight * .55);
    const size = tourBounds.getSize(new THREE.Vector3());
    const halfFov = Math.min(THREE.MathUtils.degToRad(camera.fov / 2), Math.atan(Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * 558 / 600));
    const radius = Math.hypot(size.x + width, size.y + height, size.z + width) / 2;
    const fitDistance = Math.max(4.5, radius / Math.sin(halfFov) * 1.1);
    const replayFraction = sample / trace.length;
    const dolly = trace.length === 1 ? 0 : -.045 * Math.sin(Math.PI * replayFraction);
    const distance = THREE.MathUtils.lerp(exportOffset.length() * (1 + dolly), fitDistance, zoom);
    // Arrive at the unobstructed angle before the pause; hold it for every
    // branch, then smoothly orbit back as the replay resumes. The model keeps
    // ordinary terrain depth throughout, with no foreground compositing.
    const orbitSeconds = exportConfig.orbitSeconds ?? 1.1;
    const enterSeconds = Math.min(orbitSeconds, exportTour.pauseAt);
    const enter = Math.min(1, Math.max(0, (time - exportTour.pauseAt + enterSeconds) / Math.max(.001, enterSeconds)));
    const leave = Math.min(1, Math.max(0, (exportTour.pauseAt + exportTour.tourSeconds + orbitSeconds - time) / orbitSeconds));
    const orbit = trace.length === 1 ? 1 : Math.min(enter, leave);
    const easedOrbit = orbit * orbit * orbit * (orbit * (orbit * 6 - 15) + 10);
    const headingIndex = Math.min(cameraHeadings.length - 2, Math.floor(sample));
    const heading = THREE.MathUtils.lerp(cameraHeadings[headingIndex], cameraHeadings[headingIndex + 1], sample - headingIndex);
    const maxArc = THREE.MathUtils.degToRad(exportConfig.followArcDegrees ?? 18);
    const trackingArc = THREE.MathUtils.clamp((heading - cameraHeadings[0]) * (exportConfig.headingFollow ?? .3), -maxArc, maxArc);
    const drift = THREE.MathUtils.degToRad(3) * Math.sin(Math.PI * replayFraction);
    const azimuth = baseAzimuth + orbitAzimuth * easedOrbit + (trackingArc + drift) * (1 - easedOrbit);
    const elevation = THREE.MathUtils.lerp(baseElevation, focusElevation, easedOrbit)
      + THREE.MathUtils.degToRad(1.5) * Math.sin(Math.PI * replayFraction) * (1 - easedOrbit);
    const direction = new THREE.Vector3(Math.sin(azimuth) * Math.cos(elevation), Math.sin(elevation), Math.cos(azimuth) * Math.cos(elevation));
    camera.position.copy(cameraAnchor).add(direction.multiplyScalar(distance));
    controls.target.copy(cameraTarget);
    camera.lookAt(cameraTarget);
    updateCherryParticles(time);
    resizeWorld(); controls.update(); renderExportFrame(touring);
    lastInspection = {touring, tick: tick.tick, candidateId, pathIds: path.map(candidate => candidate.id),
      segment, segmentProgress: progress, hitboxPosition, branchProgress, winner,
      renderedCandidateCount: possibilitiesGroup.userData.candidateCount, treeCandidateCount: treeNodes.size,
      cameraPosition: camera.position.toArray(), cameraTarget: controls.target.toArray(), cameraMode: 'cinematic-follow'};
  }
};
resizeWorld();
window.ptrExport.draw(0, 2);
