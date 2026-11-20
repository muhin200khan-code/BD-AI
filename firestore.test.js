const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    displayName: "Muhin Hasan Khan",
    email: "muhin@example.com",
    personality: "BALANCED",
    customInstructionsAbout: "Android developer in Bangladesh",
    customInstructionsStyle: "Clear and helpful",
    memoryEnabled: true,
    themeMode: "DARK",
    accentColor: "EMERALD",
    compactChat: false,
    showTimestamps: true,
    preferredLanguage: "Auto",
    voiceSpeed: 1.0,
    autoSpeakVoice: false,
    saveChatHistory: true,
    safeContentFilter: true,
    notificationsEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validChatPayload(ts) {
  return {
    title: "Bengali NLP & Coding Help",
    folder: "Coding",
    modelMode: "FLASH",
    archived: false,
    pinned: false,
    lastMessagePreview: "How can I help you today?",
    messageCount: 1,
    createdAt: ts,
    updatedAt: ts,
  };
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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scheduled_tasks").get());
});

test("UserProfile: owner create, partial update, and security boundaries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts))
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      personality: "TUTOR",
      accentColor: "CYAN",
      voiceSpeed: 1.25,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (Shadow Update test)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      personality: "FRIENDLY",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user isolation (PII protection)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(aliceDb.collection("users").get());
});

test("Chats & Messages: owner CRUD, list queries, and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Empty collection list succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("chats").get());

  // Create chat without @DocumentId "id"
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set(validChatPayload(ts))
  );

  // Reject extra @DocumentId "id" field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_bad").set({
      id: "chat_bad",
      ...validChatPayload(ts),
    })
  );

  // Rename chat & archive chat updates
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({
      title: "Kotlin Jetpack Compose Architecture",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").update({
      archived: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Create messages (with and without optional fields)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1")
      .collection("messages").doc("msg_1").set({
        role: "user",
        content: "তোমাকে কে বানাইছে?",
        createdAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1")
      .collection("messages").doc("msg_2").set({
        role: "model",
        content: "আমাকে তৈরি করেছেন মুহিন হাসান খান।",
        modelUsed: "gemini-3.8-flash",
        createdAt: new Date(Date.now() - 2000),
      })
  );

  // Reject message update (messages are append-only)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1")
      .collection("messages").doc("msg_2").update({ content: "Tampered" })
  );

  // List messages succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1")
      .collection("messages").orderBy("createdAt", "asc").get()
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").delete());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1")
      .collection("messages").get()
  );
});

test("Memories & ScheduledTasks: owner CRUD and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 4000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      content: "Prefers answers in Bengali and English",
      category: "Language",
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      content: "Prefers concise Bengali technical explanations",
      category: "Preference",
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduled_tasks").doc("task_1").set({
      title: "Daily Tech Digest",
      prompt: "Summarize major AI & Android engineering concepts",
      scheduleLabel: "Daily 9:00 AM",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduled_tasks").doc("task_1").update({
      lastOutput: "Digest generated successfully.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("scheduled_tasks").get());
});
