const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {candidatePath, planBranchTour} = require('./ptr-branch-tour.cjs');
const {selectedItems} = require('./render-ptr-highlights.cjs');

const candidate = (id, parent, x, selected = false) => ({id, parent, x, y: 0, z: 0, selected});
const tick = {tick: 5, multiTick: {candidates: [
  candidate(1, -1, 1), candidate(2, 1, 2), candidate(3, 1, 3, true),
  candidate(4, 1, 3), candidate(5, -1, -2)
]}};

test('default clips all have source recordings', () => {
  for (const item of selectedItems()) {
    const recording = path.resolve(__dirname, '../../src/test/resources/physics_test_runs', item.report.replace(/\.html$/, '.ptr'));
    assert.ok(fs.existsSync(recording), `Missing recording for ${item.id}: ${recording}`);
  }
  assert.ok(!selectedItems().some(item => item.enabled === false));
  assert.equal(selectedItems('11-cherry-grove-run').length, 1);
  assert.throws(() => selectedItems('does-not-exist'), /Unknown clip/);
});

test('tour is deterministic and finishes with the exact winner despite duplicate paths', () => {
  const tour = planBranchTour([tick], {id: 'example'});
  assert.deepEqual(tour, planBranchTour([tick], {id: 'example'}));
  assert.equal(tour.visitIds.at(-1), 3);
  assert.ok(!tour.visitIds.includes(4));
  assert.equal(tour.candidateCount, 5);
  assert.equal(tour.replaySeconds, 0);
  assert.ok(tour.durationSeconds > 0);
  assert.deepEqual(candidatePath(tick, 3).map(c => c.id), [1, 3]);
});

test('tour honors explicit sample and duration', () => {
  const second = {...tick, tick: 6};
  const tour = planBranchTour([tick, second], {id: 'example', tourTick: 6, replaySeconds: 9});
  assert.equal(tour.tick, 6);
  assert.equal(tour.replaySeconds, 9);
  assert.equal(tour.pauseAt, 9);
  assert.equal(tour.durationSeconds, 9 + tour.tourSeconds);
});

test('invalid tour evidence fails explicitly', () => {
  assert.throws(() => planBranchTour([tick], {id: 'missing', tourTick: 99}), /Missing tour tick/);
  assert.throws(() => planBranchTour([{tick: 1, multiTick: {candidates: []}}], {id: 'empty'}), /No branch candidates/);
  const cycle = {multiTick: {candidates: [candidate(1, 2, 1), candidate(2, 1, 2)]}};
  assert.throws(() => candidatePath(cycle, 1), /Cycle/);
});
