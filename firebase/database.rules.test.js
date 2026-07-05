const fs = require("fs");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

let testEnv;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "guardpulse-parental-control-test",
    database: {
      rules: fs.readFileSync("database.rules.json", "utf8"),
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearDatabase();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.database();
    await db.ref("devices/tv1/meta").set({
      deviceId: "tv1",
      tvUid: "tvUid",
      ownerUid: "parentUid",
      label: "Mi TV",
      androidSdk: 28,
    });
    await db.ref("users/parentUid/devices/tv1").set({
      deviceId: "tv1",
      label: "Mi TV",
      pairedAt: 1,
    });
  });
});

function dbAs(uid) {
  return testEnv.authenticatedContext(uid).database();
}

test("paired parent can read only paired device", async () => {
  await assertSucceeds(dbAs("parentUid").ref("devices/tv1/apps").get());
  await assertFails(dbAs("otherParent").ref("devices/tv1/apps").get());
});

test("parent can write desired policy but not TV state", async () => {
  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/policy/apps/Y29tLnZpZGVv").set({
      packageName: "com.video",
      manualBlocked: true,
      dailyLimitMinutes: 30,
      updatedAt: 1,
    })
  );

  await assertFails(
    dbAs("parentUid").ref("devices/tv1/state/apps/Y29tLnZpZGVv").set({
      packageName: "com.video",
      requestedSuspended: true,
      enforcementMode: "fallback",
      fallbackLocked: true,
      usageMinutesToday: 0,
    })
  );
});

test("parent can manage modes active mode and safe mode", async () => {
  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/policy/modes/mode1").set({
      modeId: "mode1",
      name: "Study",
      createdAt: 1,
      updatedAt: 1,
      updatedBy: "parentUid",
      apps: {
        Y29tLnZpZGVv: {
          packageName: "com.video",
          manualBlocked: true,
          dailyLimitMinutes: 30,
          updatedAt: 1,
        },
      },
    })
  );

  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/policy/activeMode").set({
      modeId: "mode1",
      modeName: "Study",
      activatedAt: 2,
      updatedBy: "parentUid",
    })
  );

  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/security/safeMode").set({
      enabled: true,
      until: 9999999999999,
      startedAt: 3,
      startedBy: "parentUid",
    })
  );

  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/security/safeMode").set({
      enabled: false,
      until: 0,
      updatedAt: 4,
      updatedBy: "parentUid",
    })
  );
});

test("TV and unpaired parents cannot write parent-only controls", async () => {
  await assertFails(
    dbAs("tvUid").ref("devices/tv1/policy/modes/mode1").set({
      modeId: "mode1",
      name: "Study",
    })
  );

  await assertFails(
    dbAs("otherParent").ref("devices/tv1/security/safeMode").set({
      enabled: true,
      until: 9999999999999,
    })
  );
});

test("TV can write runtime state but not desired policy", async () => {
  await assertSucceeds(
    dbAs("tvUid").ref("devices/tv1/security/runtime").update({
      enforcementMode: "fallback",
      accessibility: true,
      usageAccess: true,
      updatedAt: 1,
    })
  );

  await assertFails(
    dbAs("tvUid").ref("devices/tv1/policy/apps/Y29tLnZpZGVv").set({
      packageName: "com.video",
      manualBlocked: true,
    })
  );
});

test("parent creates command and TV updates command status", async () => {
  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/commands/cmd1").set({
      type: "rescanApps",
      requestedBy: "parentUid",
      createdAt: 1,
    })
  );

  await assertSucceeds(
    dbAs("tvUid").ref("devices/tv1/commands/cmd1").update({
      status: "done",
      completedAt: 2,
    })
  );
});

test("parent can create open setup command and unknown commands are rejected", async () => {
  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/commands/cmdOpenSetup").set({
      type: "openSetup",
      requestedBy: "parentUid",
      createdAt: 1,
    })
  );

  await assertFails(
    dbAs("parentUid").ref("devices/tv1/commands/cmdUnknown").set({
      type: "openHiddenThing",
      requestedBy: "parentUid",
      createdAt: 1,
    })
  );
});

test("unpaired parent cannot approve unlock request", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.database();
    await db.ref("devices/tv1/unlockRequests/request1").set({
      requestId: "request1",
      packageName: "com.video",
      reason: "manual",
      status: "pending",
      createdAt: 1,
      expiresAt: 9999999999999,
    });
  });

  await assertFails(
    dbAs("otherParent").ref("devices/tv1/unlockRequests/request1").update({
      status: "approved",
      updatedAt: 2,
      updatedBy: "otherParent",
    })
  );
});

test("paired parent can choose unlock approval type and invalid duration is rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.database();
    await db.ref("devices/tv1/unlockRequests/request1").set({
      requestId: "request1",
      packageName: "com.video",
      reason: "manual",
      status: "pending",
      createdAt: 1,
      expiresAt: 9999999999999,
    });
  });

  await assertSucceeds(
    dbAs("parentUid").ref("devices/tv1/unlockRequests/request1").update({
      status: "approved",
      approvalType: "timed",
      approvalDurationMs: 900000,
      updatedAt: 2,
      updatedBy: "parentUid",
    })
  );

  await assertFails(
    dbAs("parentUid").ref("devices/tv1/unlockRequests/request1").update({
      status: "approved",
      approvalType: "timed",
      approvalDurationMs: 120000,
      updatedAt: 3,
      updatedBy: "parentUid",
    })
  );
});
