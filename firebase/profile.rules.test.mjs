import {readFile} from 'node:fs/promises';
import {test,before,after,beforeEach} from 'node:test';
import assert from 'node:assert/strict';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,getDoc,setDoc,updateDoc,deleteDoc,collection,getDocs,serverTimestamp,Timestamp,getFirestore,connectFirestoreEmulator} from 'firebase/firestore';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,connectAuthEmulator,createUserWithEmailAndPassword,signOut} from 'firebase/auth';
let env;const projectId='demo-firststep-h07';
before(async()=>{env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:18080,rules:await readFile(new URL('./firestore.rules',import.meta.url),'utf8')}});});
after(async()=>{await env.cleanup();});beforeEach(async()=>{await env.clearFirestore();});
const db=uid=>uid?env.authenticatedContext(uid).firestore():env.unauthenticatedContext().firestore();
const profile=(uid,extra={})=>({schemaVersion:1,uid,name:'테스트 이름',studentNumber:'00123',department:'직접 입력 예시 학과',createdAt:serverTimestamp(),updatedAt:serverTimestamp(),...extra});
test('owner only get and no cross-UID/anonymous/list/delete',async()=>{
 const a=db('fixture-a'),b=db('fixture-b'),anon=db();await assertSucceeds(setDoc(doc(a,'users/fixture-a'),profile('fixture-a')));await assertSucceeds(getDoc(doc(a,'users/fixture-a')));
 for(const other of [b,anon]){await assertFails(getDoc(doc(other,'users/fixture-a')));await assertFails(setDoc(doc(other,'users/fixture-a'),profile('fixture-a')));}
 await assertFails(getDocs(collection(a,'users')));await assertFails(setDoc(doc(a,'users/fixture-b'),profile('fixture-b')));await assertFails(deleteDoc(doc(a,'users/fixture-a')));
});
test('required exact fields/types/lengths and leading zero string',async()=>{
 const a=db('fixture-a'),ref=doc(a,'users/fixture-a');
 for(const bad of [{name:''},{name:' '},{name:' trim'},{name:'\f'},{name:'\u000BName'},{department:'Name\u001F'},{name:' trim\ninner'},{department:'inner\n '},{department:'tail '},{studentNumber:123},{schemaVersion:2},{schemaVersion:'1'},{name:'x'.repeat(81)},{studentNumber:'x'.repeat(41)},{department:'x'.repeat(121)},{admin:true},{complete:true},{uid:'fixture-b'},{createdAt:Timestamp.fromMillis(1)},{updatedAt:Timestamp.fromMillis(1)}])await assertFails(setDoc(ref,profile('fixture-a',bad)));
 const missing=profile('fixture-a');delete missing.department;await assertFails(setDoc(ref,missing));await assertSucceeds(setDoc(ref,profile('fixture-a')));assert.equal((await getDoc(ref)).data().studentNumber,'00123');
});
test('immutable ownership/creation/schema on update',async()=>{
 const a=db('fixture-a'),ref=doc(a,'users/fixture-a');await setDoc(ref,profile('fixture-a'));const created=(await getDoc(ref)).data().createdAt;
 await assertSucceeds(updateDoc(ref,{department:'수정 예시 학과',updatedAt:serverTimestamp()}));
 for(const bad of [{uid:'fixture-b'},{createdAt:serverTimestamp()},{schemaVersion:0},{role:'owner'},{name:null},{studentNumber:''}])await assertFails(updateDoc(ref,{...bad,updatedAt:serverTimestamp()}));
 assert.deepEqual((await getDoc(ref)).data().createdAt,created);
});
test('server-seeded incomplete profile can complete preserving metadata',async()=>{
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'users/fixture-a'),{schemaVersion:1,uid:'fixture-a',createdAt:Timestamp.fromMillis(1),updatedAt:Timestamp.fromMillis(1)});});
 const a=db('fixture-a'),ref=doc(a,'users/fixture-a');await assertSucceeds(updateDoc(ref,{name:'입력 예시',studentNumber:'0001',department:'기타 예시',updatedAt:serverTimestamp()}));assert.equal((await getDoc(ref)).data().createdAt.toMillis(),1);
});
test('deny unrelated/progress/subcollections even owner',async()=>{
 const a=db('fixture-a');for(const path of ['progress/fixture-a','users/fixture-a/progress/software','photos/fixture-a','profiles/fixture-a']){await assertFails(setDoc(doc(a,path),{uid:'fixture-a'}));await assertFails(getDoc(doc(a,path)));}
});
test('actual local Auth emulator identity reaches Firestore owner rules (not Google)',async()=>{
 const app=initializeApp({projectId,apiKey:'local-emulator-only'},'rules-auth-fixture');
 try{const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:19099',{disableWarnings:true});
 const account=await createUserWithEmailAndPassword(auth,`fixture-${Date.now()}@example.invalid`,'local-fixture-password'),uid=account.user.uid;
 const store=getFirestore(app);connectFirestoreEmulator(store,'127.0.0.1',18080);
 await assertSucceeds(setDoc(doc(store,'users',uid),profile(uid)));await assertFails(setDoc(doc(store,'users','different-fixture-owner'),profile('different-fixture-owner')));
 await signOut(auth);await assertFails(getDoc(doc(store,'users',uid)));
 }finally{await deleteApp(app);}
});

test('supplementary characters respect UTF16 field bounds',async()=>{for(const [field,limit] of [['name',80],['studentNumber',40],['department',120]]){for(const count of [limit/2,limit/2+1]){const uid='utf16-'+field+'-'+count,ref=doc(db(uid),'users',uid);const write=setDoc(ref,profile(uid,{[field]:'😀'.repeat(count)}));if(count*2<=limit)await assertSucceeds(write);else await assertFails(write);}}});
