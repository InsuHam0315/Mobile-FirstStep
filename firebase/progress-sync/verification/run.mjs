import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {initializeTestEnvironment} from '@firebase/rules-unit-testing';
import {deleteApp, initializeApp} from 'firebase/app';
import {
  connectAuthEmulator, getAuth, GoogleAuthProvider, signInWithCredential, signOut,
} from 'firebase/auth';
import {doc, setDoc, Timestamp} from 'firebase/firestore';

const projectId = 'demo-firststep-progress-sdk';
const authHost = '127.0.0.1:19383';
const storeHost = '127.0.0.1';
const storePort = 18383;
const testClass = 'com.example.firststep.game.sync.FirestoreSyncBackendEmulatorTest';
const testMethod = 'googleOwnerWritesAndSecondSdkInstanceReadsUnderCandidateRules';
const runner = 'com.example.firststep.test/androidx.test.runner.AndroidJUnitRunner';
const run = process.env.FIRSTSTEP_PROGRESS_SDK_RUN;
const adb = process.env.FIRSTSTEP_PROGRESS_SDK_ADB;
const serial = process.env.FIRSTSTEP_PROGRESS_SDK_SERIAL;
const exec = promisify(execFile);

if (process.env.GCLOUD_PROJECT !== projectId
    || process.env.FIREBASE_AUTH_EMULATOR_HOST !== authHost
    || process.env.FIRESTORE_EMULATOR_HOST !== `${storeHost}:${storePort}`
    || !/^[a-f0-9]{32}$/.test(run ?? '')
    || !/^emulator-[0-9]{4,5}$/.test(serial ?? '')
    || ['emulator-5564', 'emulator-5572', 'emulator-5580'].includes(serial)
    || !adb) {
  throw new Error('Refusing to run outside the isolated progress SDK harness');
}

const adbTarget = ['-H', '127.0.0.1', '-P', '5037', '-s', serial];

function passedSelectedTest(output) {
  const statuses = [...output.matchAll(/^INSTRUMENTATION_STATUS_CODE: (-?\d+)\r?$/gm)];
  const terminal = [...output.matchAll(/^INSTRUMENTATION_CODE: (-?\d+)\r?$/gm)];
  if (statuses.map(status => status[1]).join(',') !== '1,0'
      || terminal.length !== 1 || terminal[0][1] !== '-1'
      || terminal[0].index <= statuses[1].index
      || !/^OK \(1 test\)\s*$/m.test(output)
      || /FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=/.test(output)) {
    return false;
  }
  let start = 0;
  return statuses.every(status => {
    const lines = output.slice(start, status.index).split(/\r?\n/);
    start = status.index + status[0].length;
    return lines.includes(`INSTRUMENTATION_STATUS: class=${testClass}`)
        && lines.includes(`INSTRUMENTATION_STATUS: test=${testMethod}`)
        && lines.includes('INSTRUMENTATION_STATUS: numtests=1');
  });
}

async function seedUser(auth, label) {
  const subject = `progress-sdk-${run}-${label}`;
  const credential = GoogleAuthProvider.credential(JSON.stringify({
    sub: subject, email: `${subject}@example.invalid`, email_verified: true,
  }));
  try {
    const user = (await signInWithCredential(auth, credential)).user;
    const token = await user.getIdTokenResult();
    if (token.signInProvider !== 'google.com') throw new Error('Expected Google provider');
    return user.uid;
  } finally {
    await signOut(auth);
  }
}

async function seedFixtures() {
  const app = initializeApp({apiKey: 'progress-sdk-local-only', projectId}, 'progress-sdk-seed');
  let testEnvironment;
  try {
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${authHost}`, {disableWarnings: true});
    const owner = await seedUser(auth, 'owner');
    const other = await seedUser(auth, 'other');
    if (owner === other) throw new Error('Expected distinct local accounts');
    testEnvironment = await initializeTestEnvironment({
      projectId, firestore: {host: storeHost, port: storePort},
    });
    await testEnvironment.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      const time = Timestamp.fromMillis(1);
      for (const uid of [owner, other]) {
        await setDoc(doc(db, 'users', uid), {
          schemaVersion: 1, uid, name: 'Local test profile', studentNumber: '00123',
          department: 'Local test department', createdAt: time, updatedAt: time,
        });
      }
      await setDoc(doc(db, '_firststepProgressAllowlist', `sdk-authored-${run}`,
          'buildings', 'sdk-building'), {enabled: true});
    });
  } finally {
    try {
      if (testEnvironment) await testEnvironment.cleanup();
    } finally {
      await deleteApp(app);
    }
  }
}

async function requireIdleTestUser() {
  const {stdout: user} = await exec(adb, [...adbTarget, 'shell', 'am', 'get-current-user'],
      {timeout: 5000});
  if (user.trim() !== '0') throw new Error('Wrong Android user');
  try {
    const {stdout} = await exec(adb, [...adbTarget, 'shell', 'pidof', 'com.example.firststep',
      'com.example.firststep.test'], {timeout: 5000});
    if (stdout.trim()) throw new Error('FirstStep is already running');
    throw new Error('Unexpected pidof output');
  } catch (failure) {
    // pidof exits 1 with no output when none of the named processes are running.
    if (failure.code !== 1 || failure.stdout?.trim() !== '' || failure.stderr?.trim() !== '') {
      throw failure;
    }
  }
}

let stage = 'verify disposable AVD';
try {
  const {stdout: avd} = await exec(adb, [...adbTarget, 'emu', 'avd', 'name'], {timeout: 5000});
  const avdName = avd.split(/\r?\n/, 1)[0].trim();
  if (!/^FirstStep_ProgressSdk_[A-Za-z0-9_]+$/.test(avdName)) {
    throw new Error('AVD does not match harness');
  }
  await requireIdleTestUser();
  stage = 'provision synthetic Google users and Firestore fixtures';
  await seedFixtures();
  console.log('Local Google fixtures and private registration provisioned.');
  stage = 'verify idle Android user before instrumentation';
  await requireIdleTestUser();
  stage = 'execute Android instrumentation';
  const {stdout} = await exec(adb, [...adbTarget, 'shell', 'am', 'instrument', '-w', '-r',
    '--user', '0', '-e', 'class', `${testClass}#${testMethod}`, '-e', 'firststepProgressSdkRun',
    run, '-e', 'firststepProgressSdkAvd', avdName, runner], {timeout: 180_000});
  if (!passedSelectedTest(stdout)) {
    throw new Error('Instrumentation did not report one executed passing test');
  }
  console.log('Android instrumentation passed one selected test.');
} catch (failure) {
  // SDK or runner exception bodies can contain account or credential material.
  console.error(`Progress SDK verification failed during: ${stage}.`);
  if (stage === 'execute Android instrumentation' && failure.killed) {
    console.error('The Android test may still be running on the disposable AVD.');
  }
  process.exitCode = 1;
}
