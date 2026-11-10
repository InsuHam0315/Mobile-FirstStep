import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {after, before, beforeEach, test} from 'node:test';
import {assertFails, assertSucceeds, initializeTestEnvironment} from '@firebase/rules-unit-testing';
import {
  collection, collectionGroup, deleteDoc, doc, getDocFromServer, getDocsFromServer,
  runTransaction, serverTimestamp, setDoc, Timestamp, updateDoc,
} from 'firebase/firestore';

const projectId = 'demo-firststep-progress-sync';
const uid = 'rules-user-a';
const dataset = 'rules-authored-v1';
const building = 'rules-building-a';
const introAt = 10_000;
const wait = 900_000;
const maxMillis = 253402300799999;
const completion = {
  attemptId: 'attempt-a', contentVersion: 'content-v1', correct: 2,
  completedAtMillis: introAt + wait,
};
const intro = {schemaVersion: 1, introDoneAtMillis: introAt, completion: null};
const pass = {...intro, completion};
let env;

const progressPath = ({user = uid, data = dataset, mode = 'NORMAL', target = building} = {}) =>
  `users/${user}/progressDatasets/${data}/modes/${mode}/buildings/${target}`;
const targetPath = (data = dataset, target = building) =>
  `_firststepProgressAllowlist/${data}/buildings/${target}`;
const profile = (user, time) => ({
  schemaVersion: 1, uid: user, name: 'Example name', studentNumber: '00123',
  department: 'Example department', createdAt: time, updatedAt: time,
});
const signedIn = (user = uid, provider = 'google.com') =>
  env.authenticatedContext(user, {firebase: {sign_in_provider: provider}}).firestore();
const seed = (path, data) =>
  env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), path), data));
const seededProfile = user => seed(`users/${user}`, profile(user, Timestamp.fromMillis(1)));
const without = (object, field) => Object.fromEntries(
    Object.entries(object).filter(([key]) => key !== field));

before(async () => {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:18282');
  env = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: '127.0.0.1', port: 18282,
      rules: await readFile(new URL('./firestore.rules', import.meta.url), 'utf8'),
    },
  });
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seededProfile(uid);
  await seed(targetPath(), {enabled: true});
});

test('owner transaction reads a missing document then creates intro and records first pass', async () => {
  const db = signedIn();
  const ref = doc(db, progressPath());
  assert.equal((await assertSucceeds(getDocFromServer(ref))).exists(), false);
  await assertSucceeds(runTransaction(db, async transaction => {
    assert.equal((await transaction.get(ref)).exists(), false);
    transaction.set(ref, intro);
  }));
  assert.deepEqual((await getDocFromServer(ref)).data(), intro);
  await assertSucceeds(runTransaction(db, async transaction => {
    const previous = (await transaction.get(ref)).data();
    transaction.set(ref, {...previous, completion});
  }));
  assert.deepEqual((await getDocFromServer(ref)).data(), pass);
});

test('a merged pass can be the first write and the integer timestamp bounds match the serializer', async () => {
  const ref = doc(signedIn(), progressPath());
  const fromZero = {...pass, introDoneAtMillis: 0};
  await assertSucceeds(setDoc(ref, fromZero));
  assert.deepEqual((await getDocFromServer(ref)).data(), fromZero);
  await seed(targetPath(dataset, 'latest'), {enabled: true});
  const latest = {...intro, introDoneAtMillis: maxMillis};
  await assertSucceeds(setDoc(doc(signedIn(), progressPath({target: 'latest'})), latest));
});

test('duplicates and earlier introduction merges preserve the first accepted pass', async () => {
  const ref = doc(signedIn(), progressPath());
  await setDoc(ref, intro);
  await assertSucceeds(setDoc(ref, intro));
  const merged = {...pass, introDoneAtMillis: 8_000};
  await assertSucceeds(setDoc(ref, merged));
  await assertSucceeds(setDoc(ref, merged));
  await assertSucceeds(setDoc(ref, {...merged, introDoneAtMillis: 7_000}));
  assert.deepEqual((await getDocFromServer(ref)).data(), {...merged, introDoneAtMillis: 7_000});
});

test('wrong UID, signed-out, anonymous and other providers cannot read or write progress', async () => {
  await setDoc(doc(signedIn(), progressPath()), intro);
  await seededProfile('rules-user-b');
  const others = [
    signedIn('rules-user-b'), env.unauthenticatedContext().firestore(),
    signedIn(uid, 'anonymous'), signedIn(uid, 'password'),
    env.authenticatedContext(uid, {firebase: {}}).firestore(),
  ];
  for (const db of others) {
    const ref = doc(db, progressPath());
    await assertFails(getDocFromServer(ref));
    await assertFails(setDoc(ref, pass));
  }
  for (const user of ['Guest', 'anonymous', '-invalid-id']) {
    await seededProfile(user);
    await assertFails(setDoc(doc(signedIn(user), progressPath({user})), intro));
  }
});

test('Google owner needs an existing complete profile with matching UID and schema', async () => {
  const user = 'profile-check';
  const ref = doc(signedIn(user), progressPath({user}));
  const profilePath = `users/${user}`;
  const full = profile(user, Timestamp.fromMillis(1));
  for (const candidate of [
    null, without(full, 'department'), {...full, studentNumber: 123},
    {...full, name: ' Bad'}, {...full, uid}, {...full, schemaVersion: 2},
  ]) {
    if (candidate) await seed(profilePath, candidate);
    await assertFails(getDocFromServer(ref));
    await assertFails(setDoc(ref, intro));
  }
  await seed(profilePath, full);
  await assertSucceeds(setDoc(ref, intro));
});

test('unregistered, disabled, invalid and reserved targets fail closed', async () => {
  const db = signedIn();
  const reserved = ['software_episode_demo_v1', 'H04_Fixture_V1', 'legacy_v1',
    'practice-v1', 'synthetic-v1', 'software_guide_v1', 'software_narration_v1'];
  for (const data of reserved) await seed(targetPath(data), {enabled: true});
  await seed(targetPath('disabled'), {enabled: false});
  await seed(targetPath('malformed'), {enabled: 'true'});
  await seed(targetPath('extra-field'), {enabled: true, public: true});
  await seed(targetPath(dataset, '-invalid'), {enabled: true});
  for (const options of [
    {mode: 'DEMO'}, {mode: 'normal'}, {data: 'unknown'}, {target: 'unknown'},
    {data: 'disabled'}, {data: 'malformed'}, {data: 'extra-field'}, {target: '-invalid'},
    ...reserved.map(data => ({data})),
  ]) {
    const ref = doc(db, progressPath(options));
    await assertFails(getDocFromServer(ref));
    await assertFails(setDoc(ref, intro));
  }
  await assertFails(getDocFromServer(doc(db, 'users', uid, 'progress', building)));
  await assertFails(setDoc(doc(db, 'users', uid, 'progress', building), intro));
});

test('clients cannot grant or change registration, even with a valid Google profile', async () => {
  const db = signedIn();
  const unregistered = doc(db, targetPath('client-registration'));
  const registered = doc(db, targetPath());
  await assertFails(setDoc(unregistered, {enabled: true}));
  await assertFails(getDocFromServer(registered));
  await assertFails(updateDoc(registered, {enabled: false}));
  await assertFails(deleteDoc(registered));
  await assertFails(getDocsFromServer(collection(db, '_firststepProgressAllowlist', dataset, 'buildings')));
});

test('disabling a trusted registration also blocks reads of existing progress', async () => {
  const ref = doc(signedIn(), progressPath());
  await setDoc(ref, intro);
  await seed(targetPath(), {enabled: false});
  await assertFails(getDocFromServer(ref));
  await assertFails(setDoc(ref, pass));
});

test('exact schema rejects malformed milestones and incomplete or out-of-order completions', async () => {
  const ref = doc(signedIn(), progressPath());
  const malformed = [
    {...intro, uid}, without(intro, 'completion'), {...intro, schemaVersion: '1'},
    {...intro, schemaVersion: 2}, {...intro, introDoneAtMillis: -1},
    {...intro, introDoneAtMillis: maxMillis + 1}, {...intro, introDoneAtMillis: '10000'},
    {...intro, introDoneAtMillis: 0.5}, {...intro, introDoneAtMillis: Timestamp.fromMillis(1)},
    {...intro, completion: []},
    ...[
      {...completion, reward: true}, without(completion, 'attemptId'),
      {...completion, attemptId: 'invalid/id'}, {...completion, attemptId: 'x'.repeat(129)},
      {...completion, contentVersion: ''}, {...completion, contentVersion: 1},
      {...completion, correct: 1}, {...completion, correct: 4}, {...completion, correct: 2.5},
      {...completion, completedAtMillis: introAt + wait - 1},
      {...completion, completedAtMillis: -1}, {...completion, completedAtMillis: maxMillis + 1},
    ].map(value => ({...intro, completion: value})),
  ];
  for (const value of malformed) await assertFails(setDoc(ref, value));
  await assertSucceeds(setDoc(ref, pass));
});

test('late overwrite, later intro, completion replacement and deletion are denied', async () => {
  const ref = doc(signedIn(), progressPath());
  const earlierPass = {...pass, introDoneAtMillis: 8_000};
  await setDoc(ref, earlierPass);
  for (const bad of [
    pass, intro, {...earlierPass, completion: null},
    {...earlierPass, completion: {...completion, attemptId: 'attempt-b'}},
    {...earlierPass, completion: {...completion, correct: 3}},
    {...earlierPass, completion: {...completion, completedAtMillis: completion.completedAtMillis - 1}},
  ]) await assertFails(setDoc(ref, bad));
  await assertFails(deleteDoc(ref));
  assert.deepEqual((await getDocFromServer(ref)).data(), earlierPass);
});

test('owner listing, cross-user collections and collection-group reads are denied', async () => {
  const db = signedIn();
  await setDoc(doc(db, progressPath()), intro);
  for (const user of [uid, 'rules-user-b']) {
    await assertFails(getDocsFromServer(collection(
        db, 'users', user, 'progressDatasets', dataset, 'modes', 'NORMAL', 'buildings')));
  }
  await assertFails(getDocsFromServer(collectionGroup(db, 'buildings')));
});

test('server-seeded invalid records cannot be read or overwritten', async () => {
  await seed(progressPath(), {...pass, extra: true});
  const ref = doc(signedIn(), progressPath());
  await assertFails(getDocFromServer(ref));
  await assertFails(setDoc(ref, intro));
});

test('combined candidate retains owner profile rules and denies unrelated paths', async () => {
  const user = 'profile-owner';
  const db = signedIn(user, 'password');
  const ref = doc(db, 'users', user);
  await assertFails(setDoc(ref, {...profile(user, serverTimestamp()), name: ' trim'}));
  await assertSucceeds(setDoc(ref, profile(user, serverTimestamp())));
  const created = (await assertSucceeds(getDocFromServer(ref))).data().createdAt;
  await assertSucceeds(updateDoc(ref, {department: 'Updated', updatedAt: serverTimestamp()}));
  assert.deepEqual((await getDocFromServer(ref)).data().createdAt, created);
  await assertFails(updateDoc(ref, {schemaVersion: 2, updatedAt: serverTimestamp()}));
  await assertFails(getDocFromServer(doc(signedIn(), 'users', user)));
  await assertFails(getDocsFromServer(collection(db, 'users')));
  await assertFails(deleteDoc(ref));
  await assertFails(setDoc(doc(db, 'unrelated', user), {value: true}));
});

test('owner can complete a server-seeded partial profile without replacing creation time', async () => {
  const user = 'partial-profile';
  const db = signedIn(user);
  const ref = doc(db, 'users', user);
  const time = Timestamp.fromMillis(1);
  await seed(`users/${user}`, {schemaVersion: 1, uid: user, createdAt: time, updatedAt: time});
  await assertSucceeds(setDoc(ref, {...profile(user, serverTimestamp()), createdAt: time}));
  assert.deepEqual((await getDocFromServer(ref)).data().createdAt, time);
  await assertSucceeds(setDoc(doc(db, progressPath({user})), intro));
});
