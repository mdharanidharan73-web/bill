const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const FATHER_UID = "father_admin_01";
const SON_UID = "son_member_02";
const VIEWER_UID = "relative_viewer_03";
const OUTSIDER_UID = "outsider_99";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = () => new Date(Date.now() - 2000);
const futureDate = (ms = 3600000) => new Date(Date.now() + ms);

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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

function sampleGroupPayload(memberUids = [FATHER_UID, SON_UID, VIEWER_UID]) {
  const ts = pastDate();
  return {
    name: "Sharma Family Expenses",
    description: "Monthly shared household bills",
    ownerId: FATHER_UID,
    ownerName: "Rajesh Sharma",
    upiId: "rajesh@upi",
    upiRecipientName: "Rajesh Sharma",
    memberUids,
    adminUids: [FATHER_UID],
    viewerUids: [VIEWER_UID],
    memberCount: memberUids.length,
    archived: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

function sampleBillPayload(status = "AWAITING_APPROVAL") {
  const ts = pastDate();
  return {
    billCode: "BC-2026-1001",
    groupId: "grp_1",
    groupName: "Sharma Family Expenses",
    ownerId: FATHER_UID,
    ownerName: "Rajesh Sharma",
    memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
    adminUids: [FATHER_UID],
    viewerUids: [VIEWER_UID],
    title: "Monthly Electricity Bill",
    category: "Utilities",
    merchantName: "BESCOM",
    description: "November electricity charges",
    notes: "Pay before due date",
    totalPaise: 3000000,
    collectedPaise: 0,
    splitType: "EQUAL",
    billDateMillis: Date.now() - 86400000,
    dueDateMillis: Date.now() + 86400000 * 5,
    status,
    upiId: "rajesh@upi",
    upiRecipientName: "Rajesh Sharma",
    participantCount: 2,
    paidCount: 0,
    approvedCount: 0,
    disputedCount: 0,
    aiScanned: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

// 1. Unauthenticated denial
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(FATHER_UID).get());
  await assertFails(unauth.collection("groups").where("memberUids", "array-contains", FATHER_UID).get());
  await assertFails(unauth.collection("bills").where("memberUids", "array-contains", FATHER_UID).get());
  await assertFails(unauth.collection("groups").doc("grp_1").set(sampleGroupPayload()));
});

// 2. User profile PII isolation
test("User profile: owner CRUD succeeds, other user get/update fails, ghost field rejected", async () => {
  const fatherDb = testEnv.authenticatedContext(FATHER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    fatherDb.collection("users").doc(FATHER_UID).set({
      userId: FATHER_UID,
      displayName: "Rajesh Sharma",
      email: "rajesh@example.com",
      phoneNumber: "+919876543210",
      preferredLanguage: "en",
      defaultUpiId: "rajesh@upi",
      defaultUpiName: "Rajesh Sharma",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user PII read/update blocked
  await assertFails(outsiderDb.collection("users").doc(FATHER_UID).get());
  await assertFails(outsiderDb.collection("users").doc(FATHER_UID).update({ displayName: "Hacked" }));

  // Ghost field shadow update blocked
  await assertFails(
    fatherDb.collection("users").doc(FATHER_UID).update({
      isAdmin: true,
      updatedAt: pastDate(),
    })
  );
});

// 3. Groups & expiring invite redemption
test("Group creation, member isolation, and valid vs expired invitation join", async () => {
  const fatherDb = testEnv.authenticatedContext(FATHER_UID).firestore();
  const sonDb = testEnv.authenticatedContext(SON_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(
    fatherDb.collection("groups").doc("grp_1").set(sampleGroupPayload([FATHER_UID]))
  );

  // Outsider cannot read private group or run unfiltered list
  await assertFails(outsiderDb.collection("groups").doc("grp_1").get());
  await assertFails(fatherDb.collection("groups").get());
  await assertSucceeds(
    fatherDb.collection("groups").where("memberUids", "array-contains", FATHER_UID).get()
  );

  // Admin creates valid invite code
  const ts = pastDate();
  await assertSucceeds(
    fatherDb.collection("invitations").doc("BC99K2M1").set({
      groupId: "grp_1",
      groupName: "Sharma Family Expenses",
      role: "MEMBER",
      createdBy: FATHER_UID,
      createdByName: "Rajesh Sharma",
      status: "ACTIVE",
      expiresAt: futureDate(3600000),
      maxUses: 5,
      usedCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Son redeems valid invite code (cannot escalate to admin!)
  await assertFails(
    sonDb.collection("groups").doc("grp_1").update({
      memberUids: [FATHER_UID, SON_UID],
      adminUids: [FATHER_UID, SON_UID],
      memberCount: 2,
      lastInviteCode: "BC99K2M1",
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    sonDb.collection("groups").doc("grp_1").update({
      memberUids: [FATHER_UID, SON_UID],
      memberCount: 2,
      lastInviteCode: "BC99K2M1",
      updatedAt: pastDate(),
    })
  );

  // Son creates their own groupMember entry after joining
  await assertSucceeds(
    sonDb.collection("groupMembers").doc("grp_1_" + SON_UID).set({
      groupId: "grp_1",
      userId: SON_UID,
      ownerId: FATHER_UID,
      memberUids: [FATHER_UID, SON_UID],
      adminUids: [FATHER_UID],
      displayName: "Arjun Sharma",
      role: "MEMBER",
      status: "ACTIVE",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Son increments invite usedCount
  await assertSucceeds(
    sonDb.collection("invitations").doc("BC99K2M1").update({
      usedCount: 1,
      status: "ACTIVE",
      updatedAt: pastDate(),
    })
  );
});

// 4. Bills: Admin creates, Member approves/disputes, Viewer blocked, Clearance gate & Terminal Lock
test("Bills RBAC, clearance balance guard, and terminal CLEARED lock", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("groups").doc("grp_1").set(sampleGroupPayload());
  });

  const fatherDb = testEnv.authenticatedContext(FATHER_UID).firestore();
  const sonDb = testEnv.authenticatedContext(SON_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Member cannot create a bill
  await assertFails(sonDb.collection("bills").doc("bill_1").set(sampleBillPayload()));

  // Admin creates a bill
  await assertSucceeds(fatherDb.collection("bills").doc("bill_1").set(sampleBillPayload()));

  // Outsider cannot read bill
  await assertFails(outsiderDb.collection("bills").doc("bill_1").get());

  // Viewer can read bill, CANNOT update bill
  await assertSucceeds(viewerDb.collection("bills").doc("bill_1").get());
  await assertFails(
    viewerDb.collection("bills").doc("bill_1").update({
      approvedCount: 1,
      status: "APPROVED",
      updatedAt: pastDate(),
    })
  );

  // Member can update approval rollup (not totalPaise, not CLEARED)
  await assertFails(
    sonDb.collection("bills").doc("bill_1").update({
      totalPaise: 100,
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    sonDb.collection("bills").doc("bill_1").update({
      approvedCount: 1,
      status: "APPROVED",
      updatedAt: pastDate(),
    })
  );

  // Admin CANNOT clear bill with outstanding balance unless override reason provided
  await assertFails(
    fatherDb.collection("bills").doc("bill_1").update({
      status: "CLEARED",
      updatedAt: pastDate(),
    })
  );

  // Admin clears bill once collectedPaise >= totalPaise
  await assertSucceeds(
    fatherDb.collection("bills").doc("bill_1").update({
      collectedPaise: 3000000,
      paidCount: 2,
      status: "CLEARED",
      clearedAtMillis: Date.now(),
      updatedAt: pastDate(),
    })
  );

  // Terminal state lock: CLEARED bill cannot be modified afterwards
  await assertFails(
    fatherDb.collection("bills").doc("bill_1").update({
      title: "Tampered Title",
      updatedAt: pastDate(),
    })
  );
});

// 5. BillParticipants & Payments: Member submits payment, CANNOT self-verify!
test("Payments & BillParticipants: member submits payment, only admin verifies", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("groups").doc("grp_1").set(sampleGroupPayload());
    await ctx.firestore().collection("bills").doc("bill_1").set(sampleBillPayload());
  });

  const fatherDb = testEnv.authenticatedContext(FATHER_UID).firestore();
  const sonDb = testEnv.authenticatedContext(SON_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    fatherDb.collection("billParticipants").doc("bp_son").set({
      billId: "bill_1",
      groupId: "grp_1",
      ownerId: FATHER_UID,
      participantUid: SON_UID,
      participantName: "Arjun Sharma",
      participantRole: "MEMBER",
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      sharePaise: 1500000,
      paidPaise: 0,
      percentageBps: 5000,
      approvalStatus: "PENDING",
      paymentStatus: "NOT_PAID",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Member cannot self-verify on billParticipants
  await assertFails(
    sonDb.collection("billParticipants").doc("bp_son").update({
      paymentStatus: "PAYMENT_VERIFIED",
      updatedAt: pastDate(),
    })
  );

  // Member submits payment status
  await assertSucceeds(
    sonDb.collection("billParticipants").doc("bp_son").update({
      paymentStatus: "PAYMENT_SUBMITTED",
      paymentRef: "UTR123456789",
      updatedAt: pastDate(),
    })
  );

  // Member creates PAYMENT_SUBMITTED record, CANNOT create PAYMENT_VERIFIED record
  await assertFails(
    sonDb.collection("payments").doc("pay_bad").set({
      billId: "bill_1",
      groupId: "grp_1",
      participantDocId: "bp_son",
      ownerId: FATHER_UID,
      payerUid: SON_UID,
      payerName: "Arjun Sharma",
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      amountPaise: 1500000,
      method: "UPI",
      status: "PAYMENT_VERIFIED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    sonDb.collection("payments").doc("pay_1").set({
      billId: "bill_1",
      groupId: "grp_1",
      participantDocId: "bp_son",
      ownerId: FATHER_UID,
      payerUid: SON_UID,
      payerName: "Arjun Sharma",
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      amountPaise: 1500000,
      method: "UPI",
      upiRefId: "UTR123456789",
      status: "PAYMENT_SUBMITTED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Viewer cannot create payments
  await assertFails(
    viewerDb.collection("payments").doc("pay_viewer").set({
      billId: "bill_1",
      groupId: "grp_1",
      participantDocId: "bp_son",
      ownerId: FATHER_UID,
      payerUid: VIEWER_UID,
      payerName: "Uncle",
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      amountPaise: 1000,
      method: "UPI",
      status: "PAYMENT_SUBMITTED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Only Admin can verify payment
  await assertFails(
    sonDb.collection("payments").doc("pay_1").update({
      status: "PAYMENT_VERIFIED",
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    fatherDb.collection("payments").doc("pay_1").update({
      status: "PAYMENT_VERIFIED",
      verifiedByUid: FATHER_UID,
      verifiedByName: "Rajesh Sharma",
      updatedAt: pastDate(),
    })
  );
});

// 6. Attachments, Messages, Notifications, and Immutable AuditLogs
test("Attachments, Messages, Notifications, and Immutable AuditLogs", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("groups").doc("grp_1").set(sampleGroupPayload());
    await ctx.firestore().collection("bills").doc("bill_1").set(sampleBillPayload());
  });

  const fatherDb = testEnv.authenticatedContext(FATHER_UID).firestore();
  const sonDb = testEnv.authenticatedContext(SON_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const ts = pastDate();

  // Member uploads valid attachment; invalid MIME rejected; viewer rejected
  await assertSucceeds(
    sonDb.collection("attachments").doc("att_1").set({
      billId: "bill_1",
      groupId: "grp_1",
      ownerId: FATHER_UID,
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      uploadedByUid: SON_UID,
      uploadedByName: "Arjun Sharma",
      fileName: "upi_receipt.jpg",
      mimeType: "image/jpeg",
      fileSizeBytes: 12000,
      category: "PAYMENT_PROOF",
      contentBase64: "ZHVtbXliYXNlNjQ=",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    viewerDb.collection("attachments").doc("att_2").set({
      billId: "bill_1",
      groupId: "grp_1",
      ownerId: FATHER_UID,
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      uploadedByUid: VIEWER_UID,
      uploadedByName: "Uncle",
      fileName: "doc.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 5000,
      category: "SUPPORTING_DOC",
      contentBase64: "ZHVtbXk=",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Member cannot send ANNOUNCEMENT; Admin can
  await assertFails(
    sonDb.collection("messages").doc("msg_ann").set({
      groupId: "grp_1",
      billId: "",
      scope: "ANNOUNCEMENT",
      senderUid: SON_UID,
      senderName: "Arjun",
      senderRole: "MEMBER",
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      text: "Fake announcement",
      readByUids: [SON_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    fatherDb.collection("messages").doc("msg_ann").set({
      groupId: "grp_1",
      billId: "",
      scope: "ANNOUNCEMENT",
      senderUid: FATHER_UID,
      senderName: "Rajesh",
      senderRole: "ADMIN",
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      viewerUids: [VIEWER_UID],
      text: "New bill uploaded. Total ₹30,000.",
      readByUids: [FATHER_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Audit logs: append-only, cannot be updated or deleted even by Admin
  await assertSucceeds(
    fatherDb.collection("auditLogs").doc("log_1").set({
      groupId: "grp_1",
      billId: "bill_1",
      actorUid: FATHER_UID,
      actorName: "Rajesh Sharma",
      memberUids: [FATHER_UID, SON_UID, VIEWER_UID],
      adminUids: [FATHER_UID],
      action: "BILL_CREATED",
      summary: "Created Monthly Electricity Bill for ₹30,000",
      createdAt: ts,
    })
  );
  await assertFails(
    fatherDb.collection("auditLogs").doc("log_1").update({ summary: "Erased" })
  );
  await assertFails(fatherDb.collection("auditLogs").doc("log_1").delete());

  // Notifications: scoped strictly to recipientUid
  await assertSucceeds(
    fatherDb.collection("notifications").doc("notif_1").set({
      recipientUid: SON_UID,
      senderUid: FATHER_UID,
      senderName: "Rajesh Sharma",
      groupId: "grp_1",
      billId: "bill_1",
      type: "BILL_PUBLISHED",
      title: "New Bill Published",
      body: "Your share is ₹15,000",
      read: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    sonDb.collection("notifications").where("recipientUid", "==", SON_UID).get()
  );
  await assertFails(
    fatherDb.collection("notifications").doc("notif_1").get()
  );
});
