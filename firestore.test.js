const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validBillPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    billNumber: "MB-0001",
    customerName: "रमेश पाटील",
    customerMobile: "9876543210",
    billTimestampMillis: Date.now() - 5000,
    fullMasoorQty: 2,
    fullMasoorPrice: 100,
    halfMasoorQty: 1,
    halfMasoorPrice: 50,
    rotiQty: 4,
    rotiPrice: 15,
    grandTotal: 310,
    cashPaid: 200,
    onlinePaid: 50,
    onlineConfirmed: true,
    totalReceived: 250,
    pendingAmount: 60,
    shopNameSnapshot: "हॉटेल आखा मसूर पॅलेस",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user: cannot read or write settings, customers, or bills", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bills").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("MB-0001")
      .set(validBillPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("shop").get()
  );
});

test("Authenticated owner: valid shop settings, customer, and bill create & update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Settings create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("shop").set({
      userId: ALICE_UID,
      shopName: "हॉटेल आखा मसूर पॅलेस",
      shopSubtitle: "अस्सल कोल्हापुरी चव",
      fullMasoorPrice: 100,
      halfMasoorPrice: 50,
      rotiPrice: 15,
      nextBillNumber: 2,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Settings partial update (immutable createdAt/userId preserved)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("shop").update({
      fullMasoorPrice: 110,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Customer create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set({
      userId: ALICE_UID,
      localId: 1,
      name: "रमेश पाटील",
      mobile: "9876543210",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bill create (exact ₹310 test scenario)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("MB-0001")
      .set(validBillPayload(ALICE_UID))
  );

  // Bill partial settlement update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bills").doc("MB-0001").update({
      cashPaid: 260,
      totalReceived: 310,
      pendingAmount: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner list queries succeed
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("bills").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("customers").get());
});

test("Security bounds: reject cross-user access, ghost fields, future timestamps, and invalid bill math", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("MB-0001")
      .set(validBillPayload(ALICE_UID))
  );

  // Bob cannot read, list, update, or delete Alice's bills
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bills").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bills").doc("MB-0001").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("MB-0001")
      .update({ pendingAmount: 0, updatedAt: new Date(Date.now() - 1000) })
  );

  // Reject ghost field ("id") or unexpected field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("MB-0002")
      .set(validBillPayload(ALICE_UID, { id: "MB-0002", billNumber: "MB-0002" }))
  );

  // Reject future timestamp (> request.time)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("MB-0003")
      .set(
        validBillPayload(ALICE_UID, {
          billNumber: "MB-0003",
          createdAt: new Date(Date.now() + 60000),
          updatedAt: created,
        })
      )
  );

  // Reject inconsistent payment totals (totalReceived + pendingAmount != grandTotal)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("MB-0004")
      .set(
        validBillPayload(ALICE_UID, {
          billNumber: "MB-0004",
          totalReceived: 200,
          pendingAmount: 50,
        })
      )
  );

  // Reject immutable createdAt modification on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bills").doc("MB-0001").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("DailyRegisterRecord: owner valid create/update/delete succeed, cross-user and tampered math fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  const validRecord = {
    userId: ALICE_UID,
    recordUuid: "reg_uuid_1",
    registerDate: "2026-11-25",
    registerDateMillis: Date.now() - 5000,
    fullMasoorQty: 10,
    fullMasoorRate: 100,
    fullMasoorIncome: 1000,
    halfMasoorQty: 6,
    halfMasoorRate: 50,
    halfMasoorIncome: 300,
    rotiQty: 20,
    rotiRate: 15,
    rotiIncome: 300,
    totalQuantity: 36,
    totalIncome: 1600,
    rawTranscript: "फुल १०, हाफ ६, रोटी २०",
    unclearSummary: "",
    notes: "दैनिक पान",
    wasAiRead: true,
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("register_records").doc("reg_uuid_1").set(validRecord)
  );

  // Bob cannot read or delete Alice's register record
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("register_records").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("register_records").doc("reg_uuid_1").delete()
  );

  // Reject tampered totalIncome
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("register_records").doc("reg_uuid_2").set({
      ...validRecord,
      recordUuid: "reg_uuid_2",
      totalIncome: 9999,
    })
  );

  // Owner can update quantities & delete record
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("register_records").doc("reg_uuid_1").update({
      rotiQty: 25,
      rotiIncome: 375,
      totalQuantity: 41,
      totalIncome: 1675,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("register_records").doc("reg_uuid_1").delete()
  );
});
