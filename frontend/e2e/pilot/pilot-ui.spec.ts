import fs from "node:fs/promises";
import path from "node:path";
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page, type TestInfo } from "@playwright/test";

type RoleKey = "admin" | "academicHead" | "teacher" | "reviewer" | "student";

interface RoleJourney {
  emailKey: string;
  passwordKey: string;
  expectedRole: string;
  routes: Array<{ path: string; label: string }>;
}

const expectedOrganisationCode = required("PILOT_UI_EXPECTED_ORG_CODE");

const journeys: Record<RoleKey, RoleJourney> = {
  admin: {
    emailKey: "PILOT_UI_ADMIN_EMAIL",
    passwordKey: "PILOT_UI_ADMIN_PASSWORD",
    expectedRole: "ORG ADMIN",
    routes: [
      { path: "/dashboard", label: "dashboard" },
      { path: "/organisations", label: "organisation" },
      { path: "/users", label: "users" },
      { path: "/question-bank", label: "question-bank" },
      { path: "/approvals", label: "approvals" },
      { path: "/assessments", label: "assessments" },
      { path: "/reports", label: "reports" },
      { path: "/reports/teacher", label: "teacher-analytics" },
      { path: "/audit-logs", label: "audit-logs" },
      { path: "/operations", label: "operations" },
      { path: "/pilot-readiness", label: "pilot-readiness" },
      { path: "/notifications", label: "notifications" },
      { path: "/settings", label: "settings" },
    ],
  },
  academicHead: {
    emailKey: "PILOT_UI_ACADEMIC_HEAD_EMAIL",
    passwordKey: "PILOT_UI_ACADEMIC_HEAD_PASSWORD",
    expectedRole: "ACADEMIC HEAD",
    routes: [
      { path: "/dashboard", label: "dashboard" },
      { path: "/question-bank", label: "question-bank" },
      { path: "/approvals", label: "approvals" },
      { path: "/assessments", label: "assessments" },
      { path: "/reports", label: "reports" },
      { path: "/reports/teacher", label: "teacher-analytics" },
      { path: "/notifications", label: "notifications" },
    ],
  },
  teacher: {
    emailKey: "PILOT_UI_TEACHER_EMAIL",
    passwordKey: "PILOT_UI_TEACHER_PASSWORD",
    expectedRole: "FACULTY",
    routes: [
      { path: "/dashboard", label: "dashboard" },
      { path: "/question-bank", label: "question-bank" },
      { path: "/assessments", label: "assessments" },
      { path: "/reports", label: "reports" },
      { path: "/reports/teacher", label: "teacher-analytics" },
      { path: "/notifications", label: "notifications" },
    ],
  },
  reviewer: {
    emailKey: "PILOT_UI_REVIEWER_EMAIL",
    passwordKey: "PILOT_UI_REVIEWER_PASSWORD",
    expectedRole: "REVIEWER",
    routes: [
      { path: "/dashboard", label: "dashboard" },
      { path: "/question-bank", label: "question-bank" },
      { path: "/approvals", label: "approvals" },
      { path: "/notifications", label: "notifications" },
    ],
  },
  student: {
    emailKey: "PILOT_UI_STUDENT_EMAIL",
    passwordKey: "PILOT_UI_STUDENT_PASSWORD",
    expectedRole: "STUDENT",
    routes: [
      { path: "/dashboard", label: "dashboard" },
      { path: "/student/assessments", label: "available-assessments" },
      { path: "/student/history", label: "attempt-history" },
      { path: "/student/reports", label: "performance-report" },
      { path: "/notifications", label: "notifications" },
    ],
  },
};

function required(key: string) {
  const value = process.env[key]?.trim();
  if (!value || /REPLACE_ME|CHANGE_ME/i.test(value)) {
    throw new Error(`${key} must be configured in the protected pilot UI environment file.`);
  }
  return value;
}

function credentials(role: RoleKey) {
  const journey = journeys[role];
  return {
    email: required(journey.emailKey),
    password: required(journey.passwordKey),
  };
}

async function login(page: Page, role: RoleKey) {
  const journey = journeys[role];
  const account = credentials(role);
  await page.goto("/login", { waitUntil: "domcontentloaded" });
  await page.getByLabel("Email address").fill(account.email);
  await page.getByLabel("Password", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL(/\/(dashboard|select-organisation)(?:$|\?)/);

  if (new URL(page.url()).pathname === "/select-organisation") {
    const expectedChoice = page.locator(".org-choice", {
      hasText: expectedOrganisationCode,
    });
    await expect(expectedChoice).toHaveCount(1);
    await expectedChoice.click();
    await page.getByRole("button", { name: "Continue" }).click();
    await page.waitForURL(/\/dashboard(?:$|\?)/);
  }

  await expect(page.locator("main h1").first()).toBeVisible();
  await expect(page.locator(".topbar-context")).toContainText(expectedOrganisationCode);
  await expect(page.locator(".user-mini")).toContainText(journey.expectedRole);
}

async function waitForLivePage(page: Page) {
  await expect(page.locator("h1").first()).toBeVisible();
  await page.locator(".data-state[role='status']").waitFor({ state: "detached", timeout: 15_000 }).catch(() => undefined);
  await expect(page.locator(".data-state-error")).toHaveCount(0);
  await expect(page.getByText("Live data could not be loaded", { exact: true })).toHaveCount(0);
}

async function attachScreenshot(page: Page, testInfo: TestInfo, name: string) {
  const screenshotPath = testInfo.outputPath("screenshots", `${name}.png`);
  await fs.mkdir(path.dirname(screenshotPath), { recursive: true });
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(name, { path: screenshotPath, contentType: "image/png" });
}

async function assertNoDocumentOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.scrollWidth, JSON.stringify(dimensions)).toBeLessThanOrEqual(
    dimensions.clientWidth + 1,
  );
}

async function assertAccessible(page: Page, testInfo: TestInfo, label: string) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const blocking = results.violations.filter((violation) =>
    violation.impact === "critical" || violation.impact === "serious",
  );
  if (blocking.length) {
    await testInfo.attach(`${label}-axe.json`, {
      body: Buffer.from(JSON.stringify(blocking, null, 2)),
      contentType: "application/json",
    });
  }
  expect(blocking, `Serious/critical accessibility violations on ${label}`).toEqual([]);
}

async function inspectRoute(
  page: Page,
  testInfo: TestInfo,
  role: string,
  route: { path: string; label: string },
) {
  const pageErrors: string[] = [];
  const failedResponses: string[] = [];
  const onPageError = (error: Error) => pageErrors.push(error.message);
  const onResponse = (response: { status(): number; url(): string }) => {
    if (response.status() >= 500) failedResponses.push(`${response.status()} ${response.url()}`);
  };
  page.on("pageerror", onPageError);
  page.on("response", onResponse);

  try {
    await page.goto(route.path, { waitUntil: "domcontentloaded" });
    expect(new URL(page.url()).pathname).toBe(route.path);
    await waitForLivePage(page);
    await assertNoDocumentOverflow(page);
    await assertAccessible(page, testInfo, `${role}-${route.label}`);
    await attachScreenshot(page, testInfo, `${role}-${route.label}`);
    expect(pageErrors, `Browser errors on ${route.path}`).toEqual([]);
    expect(failedResponses, `HTTP 5xx responses on ${route.path}`).toEqual([]);
  } finally {
    page.off("pageerror", onPageError);
    page.off("response", onResponse);
  }
}

test.describe("desktop role workspaces @desktop", () => {
  for (const role of Object.keys(journeys) as RoleKey[]) {
    test(`${role} can open every authorised Release 1.0 workspace`, async ({ page }, testInfo) => {
      await login(page, role);
      for (const route of journeys[role].routes) {
        await test.step(`${route.label} renders from live APIs`, async () => {
          await inspectRoute(page, testInfo, role, route);
        });
      }
    });
  }
});

test("student critical pages remain usable at a 200% layout equivalent @zoom", async ({ page }, testInfo) => {
  await login(page, "student");
  for (const route of journeys.student.routes) {
    await inspectRoute(page, testInfo, "student-zoom", route);
  }
});

test("student workspace remains usable at 360 px Android width @mobile", async ({ page }, testInfo) => {
  await login(page, "student");
  for (const route of journeys.student.routes) {
    await inspectRoute(page, testInfo, "student-mobile", route);
  }

  await page.goto("/student/assessments", { waitUntil: "domcontentloaded" });
  await waitForLivePage(page);
  const instructionLink = page.getByRole("link", { name: "Read instructions" }).first();
  if (await instructionLink.count()) {
    await instructionLink.click();
    await waitForLivePage(page);
    await assertNoDocumentOverflow(page);
    await assertAccessible(page, testInfo, "student-mobile-instructions");
    await attachScreenshot(page, testInfo, "student-mobile-instructions");
  } else {
    await testInfo.attach("student-mobile-instructions-not-run.txt", {
      body: Buffer.from("No assessment window was open. Record the instructions/player journey manually during the rehearsal."),
      contentType: "text/plain",
    });
  }
});

test("keyboard entry and reduced-motion preference remain effective @motion", async ({ page }, testInfo) => {
  await page.goto("/login", { waitUntil: "domcontentloaded" });
  await expect(page.getByLabel("Email address")).toHaveValue("");
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
  await expect(page.getByText("Rabbit@123", { exact: false })).toHaveCount(0);
  await expect(page.getByText("Keep me signed in", { exact: true })).toHaveCount(0);
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Email address")).toBeFocused();
  await login(page, "student");
  await page.keyboard.press("Tab");
  const skipLink = page.getByRole("link", { name: "Skip to main content" });
  await expect(skipLink).toBeFocused();
  await skipLink.press("Enter");
  await expect(page.locator("#main-content")).toBeFocused();

  const motion = await page.evaluate(() => {
    const element = document.createElement("div");
    element.className = "spin";
    document.body.appendChild(element);
    const style = getComputedStyle(element);
    const result = {
      animationDuration: style.animationDuration,
      animationIterationCount: style.animationIterationCount,
      reducedMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
    };
    element.remove();
    return result;
  });
  expect(motion.reducedMotion).toBe(true);
  expect(motion.animationIterationCount).toBe("1");
  expect(Number.parseFloat(motion.animationDuration)).toBeLessThanOrEqual(0.01);
  await attachScreenshot(page, testInfo, "student-reduced-motion-dashboard");
});
