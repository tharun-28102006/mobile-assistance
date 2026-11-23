const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function futureDate(offsetMs = 60000) {
  return new Date(Date.now() + offsetMs);
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

// 1. User Profile Security & Parity Tests
test("UserProfile: unauthenticated denied; owner CRUD & action-based updates succeed; shadow fields & cross-user fail", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  const validProfile = {
    userId: ALICE_UID,
    displayName: "Alice Vance",
    wakeWord: "Hey Aura",
    offlineVoicePreferred: true,
    autoReadReplies: true,
    batteryMode: "ECO",
    ttsSpeechRate: 1.0,
    lastDeviceName: "Pixel 9 Pro",
    lastLocationLabel: "San Francisco, CA",
    recentTopics: ["commute", "coffee"],
    createdAt: created,
    updatedAt: created,
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());

  // Listing /users is denied for everyone
  await assertFails(aliceDb.collection("users").get());

  // Owner create succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));

  // Owner get succeeds; cross-user get fails
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Owner partial update (wake word & preferences) succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      wakeWord: "Wake Nova",
      batteryMode: "BALANCED",
      ttsSpeechRate: 1.15,
      updatedAt: pastDate(1000),
    })
  );

  // Owner telemetry update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      lastLocationLabel: "Seattle, WA",
      recentTopics: ["weather", "whatsapp"],
      updatedAt: pastDate(500),
    })
  );

  // Shadow update with ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      wakeWord: "Hey Aura",
      isAdmin: true,
      updatedAt: pastDate(200),
    })
  );

  // Tampered future timestamp fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      wakeWord: "Hey Future",
      updatedAt: futureDate(),
    })
  );

  // Immutable createdAt change fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Cross-user update & delete fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      wakeWord: "Hacked",
      updatedAt: pastDate(100),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
});

// 2. Conversations Subcollection Parity & Isolation Tests
test("Conversations: owner create/list/delete succeed; extra @DocumentId id key & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(8000);

  const validMsg = {
    userId: ALICE_UID,
    role: "ASSISTANT",
    content: "Identified: Ceramic pour-over coffee mug in matte sage green.",
    mode: "VISION",
    deviceName: "Pixel 9 Pro",
    locationContext: "San Francisco, CA",
    visualTag: "Ceramic Mug",
    createdAt: created,
    updatedAt: created,
  };

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("msg_001")
      .set(validMsg)
  );

  // Reject unexpected @DocumentId "id" key in create payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("msg_002")
      .set({ ...validMsg, id: "msg_002" })
  );

  // Owner list query matches Kotlin repository
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .orderBy("createdAt", "desc")
      .limit(50)
      .get()
  );

  // Cross-user list and read denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("msg_001")
      .get()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("msg_001")
      .delete()
  );
});

// 3. Automations Subcollection Parity & Action-Based Update Tests
test("Automations: owner create, toggle, runCount +1, and edit succeed; arbitrary runCount jump & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(8000);

  const autoRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("automations")
    .doc("auto_morning");

  await assertSucceeds(
    autoRef.set({
      userId: ALICE_UID,
      title: "Morning Commute Briefing",
      triggerPhrase: "start my morning",
      actionType: "BRIEFING",
      actionPayload: "Weather, traffic, and top 3 priorities for today",
      contextHint: "Morning • Home",
      enabled: true,
      runCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Toggle enabled
  await assertSucceeds(
    autoRef.update({
      enabled: false,
      updatedAt: pastDate(4000),
    })
  );

  // Increment runCount by 1
  await assertSucceeds(
    autoRef.update({
      runCount: 1,
      updatedAt: pastDate(3000),
    })
  );

  // Jumping runCount by +50 fails
  await assertFails(
    autoRef.update({
      runCount: 51,
      updatedAt: pastDate(2000),
    })
  );

  // Invalid actionType fails
  await assertFails(
    autoRef.update({
      actionType: "EXEC_SHELL",
      updatedAt: pastDate(1000),
    })
  );

  // Owner list query matches Kotlin repository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("automations").get()
  );

  // Cross-user list fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("automations").get()
  );
});

// 4. WhatsApp Message Drafts & Terminal State Locking Tests
test("MessageDrafts: owner create/edit/mark SENT succeeds; editing after SENT fails (terminal lock)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(8000);

  const draftRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("message_drafts")
    .doc("draft_wa_1");

  await assertSucceeds(
    draftRef.set({
      userId: ALICE_UID,
      recipientName: "Maya",
      recipientPhone: "+14155550199",
      body: "Hey Maya! Running 10 mins behind, see you at the cafe soon.",
      tone: "CASUAL",
      direction: "OUTGOING_DRAFT",
      status: "DRAFT",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Edit draft body while status is DRAFT
  await assertSucceeds(
    draftRef.update({
      body: "Hey Maya! Just parked, walking over to the cafe now.",
      tone: "WARM",
      updatedAt: pastDate(4000),
    })
  );

  // Mark as SENT
  await assertSucceeds(
    draftRef.update({
      status: "SENT",
      updatedAt: pastDate(2000),
    })
  );

  // Terminal state lock: editing after SENT must fail
  await assertFails(
    draftRef.update({
      body: "Tampered after sending",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user access fails
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("message_drafts")
      .doc("draft_wa_1")
      .get()
  );

  // Owner can still delete archived/sent item
  await assertSucceeds(draftRef.delete());
});

// 5. To-Do & Reminders Tests
test("Todos: owner create/toggle/list/delete succeed; invalid category & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(8000);

  const todoRef = aliceDb.collection("users").doc(ALICE_UID).collection("todos").doc("todo_1");
  await assertSucceeds(
    todoRef.set({
      userId: ALICE_UID,
      title: "Book flight to Tokyo",
      dueLabel: "Tomorrow 10 AM",
      category: "REMINDER",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    todoRef.update({ completed: true, updatedAt: pastDate(2000) })
  );

  await assertFails(
    todoRef.update({ category: "INVALID", updatedAt: pastDate(1000) })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("todos").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("todos").get());
  await assertSucceeds(todoRef.delete());
});

// 6. Smart Home Devices Tests
test("SmartDevices: owner create/control/list succeed; out-of-range level & deviceType mutation fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(8000);

  const devRef = aliceDb.collection("users").doc(ALICE_UID).collection("smart_devices").doc("dev_light");
  await assertSucceeds(
    devRef.set({
      userId: ALICE_UID,
      name: "Living Room Lights",
      room: "Living Room",
      deviceType: "LIGHT",
      poweredOn: true,
      level: 80,
      statusText: "On • 80%",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    devRef.update({ poweredOn: false, level: 0, statusText: "Off", updatedAt: pastDate(2000) })
  );

  // Out of bounds level > 100 fails
  await assertFails(
    devRef.update({ level: 150, updatedAt: pastDate(1000) })
  );

  // Mutating immutable deviceType fails
  await assertFails(
    devRef.update({ deviceType: "LOCK", updatedAt: pastDate(1000) })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("smart_devices").doc("dev_light").get());
});

