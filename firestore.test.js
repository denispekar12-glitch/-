const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_denis_uid";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("alert_regions").where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("alert_events").where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("community_messages").where("isPublic", "==", true).get());
});

test("User profile PII isolation & Shadow Update protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  const ts = pastDate();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Аліса",
      email: "alice@example.com",
      monitoredRegionIds: ["kryve_ozero"],
      soundVolume: 0.9,
      vibrationEnabled: true,
      autoPlayVoice: true,
      onlineChannelId: "omastrotryvoga-kryveozero-live-v1",
      dsnsAutoSyncEnabled: true,
      selectedTab: 0,
      selectedRegionId: "kryve_ozero",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read or write Alice's PII profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));

  // Shadow Update test: ghost field 'isAdmin' must be rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Аліса 2",
      isAdmin: true,
      updatedAt: pastDate(),
    })
  );
});

test("Alert regions & events: verified Admin Denis writes, regular user reads via query, spoofed email fails", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "denispekar12@gmail.com",
    email_verified: true,
  }).firestore();

  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: "denispekar12@gmail.com",
    email_verified: false,
  }).firestore();

  const userDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  const ts = pastDate();
  const regionPayload = {
    regionName: "Кривоозерська громада (Криве Озеро)",
    currentLevel: "STREET_FIGHTS",
    activeLevels: ["STREET_FIGHTS", "CIVIL_DEFENSE"],
    threatDetails: "Одночасно: Вуличні бої + Цивільний захист",
    adminName: "Денис",
    updatedByUid: ADMIN_UID,
    isPublic: true,
    alertStartTime: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };

  // Spoofed unverified email cannot write alert_regions
  await assertFails(
    spoofedAdminDb.collection("alert_regions").doc("kryve_ozero").set({
      ...regionPayload,
      updatedByUid: "spoof_uid",
    })
  );

  // Regular user cannot write alert_regions
  await assertFails(
    userDb.collection("alert_regions").doc("kryve_ozero").set({
      ...regionPayload,
      updatedByUid: BOB_UID,
    })
  );

  // Verified Admin Denis can check non-existent doc before create, then create & update alert_regions
  await assertSucceeds(adminDb.collection("alert_regions").doc("kryve_ozero").get());
  await assertSucceeds(adminDb.collection("alert_regions").doc("kryve_ozero").set(regionPayload));
  await assertSucceeds(
    adminDb.collection("alert_regions").doc("kryve_ozero").update({
      currentLevel: "SAFE",
      activeLevels: [],
      threatDetails: "Весь відбій!",
      updatedAt: pastDate(),
    })
  );

  // Regular user CAN read via matching where("isPublic", "==", true) query, CANNOT list unfiltered
  await assertSucceeds(userDb.collection("alert_regions").where("isPublic", "==", true).get());
  await assertFails(userDb.collection("alert_regions").get());

  // Verified Admin creates alert_event, regular user cannot create or modify
  const eventPayload = {
    regionId: "kryve_ozero",
    regionName: "Кривоозерська громада",
    level: "RED",
    activeLevels: ["RED"],
    threatDetails: "Повітряна тривога!",
    source: "Адміністратор Денис",
    adminName: "Денис",
    authorUid: ADMIN_UID,
    isBroadcastBanner: true,
    isTotalAllClear: false,
    isPublic: true,
    clientTimestampMs: Date.now(),
    createdAt: ts,
  };
  await assertSucceeds(adminDb.collection("alert_events").doc("evt_1").set(eventPayload));
  await assertFails(userDb.collection("alert_events").doc("evt_2").set({ ...eventPayload, authorUid: BOB_UID }));
  await assertSucceeds(userDb.collection("alert_events").where("isPublic", "==", true).get());
});

test("Community messages: user posts regular message, cannot spoof admin badge, cannot edit others", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "denispekar12@gmail.com",
    email_verified: true,
  }).firestore();

  const ts = pastDate();

  // Regular user cannot spoof isAdminVerified=true
  await assertFails(
    bobDb.collection("community_messages").doc("msg_spoof").set({
      authorUid: BOB_UID,
      authorName: "Fake Admin",
      authorRole: "ADMIN",
      text: "Фейкова тривога",
      regionTag: "Криве Озеро",
      isAdminVerified: true,
      isPublic: true,
      clientTimestampMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Regular user can post valid USER message
  await assertSucceeds(
    bobDb.collection("community_messages").doc("msg_bob_1").set({
      authorUid: BOB_UID,
      authorName: "Боб",
      authorRole: "USER",
      text: "У громаді спокійно",
      regionTag: "Криве Озеро",
      isAdminVerified: false,
      isPublic: true,
      clientTimestampMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice cannot edit or delete Bob's message
  await assertFails(
    aliceDb.collection("community_messages").doc("msg_bob_1").update({
      text: "Змінено чуже повідомлення",
      updatedAt: pastDate(),
    })
  );
  await assertFails(aliceDb.collection("community_messages").doc("msg_bob_1").delete());

  // Verified Admin Denis can post verified admin message and read feed
  await assertSucceeds(
    adminDb.collection("community_messages").doc("msg_admin_1").set({
      authorUid: ADMIN_UID,
      authorName: "Денис (Адміністратор)",
      authorRole: "ADMIN",
      text: "Увага! Перейдіть в укриття!",
      regionTag: "Кривоозерська громада",
      isAdminVerified: true,
      isPublic: true,
      clientTimestampMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("community_messages").where("isPublic", "==", true).get());
});
