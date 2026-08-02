import http from "k6/http";
import { check, sleep } from "k6";

const configPath = __ENV.RABBIT_M5_3_CONFIG || "/run/secrets/m5-3.env";
const config = parseEnv(open(configPath));

const approvedStudents = positiveInteger(
  config.PILOT_APPROVED_CONCURRENT_STUDENTS,
  "PILOT_APPROVED_CONCURRENT_STUDENTS",
  50,
);
const loadVus = Math.ceil(approvedStudents * 1.5);
const duration = validatedDuration(config.PILOT_LOAD_DURATION || "5m");
const authenticatedLimit = positiveInteger(
  __ENV.RABBIT_AUTHENTICATED_RATE_LIMIT || "300",
  "RABBIT_AUTHENTICATED_RATE_LIMIT",
  100000,
);
const requestsPerIteration = 3;
const thinkSeconds = Math.max(
  1,
  Math.ceil((loadVus * requestsPerIteration * 60) / (authenticatedLimit * 0.75)),
);
const baseUrl = "http://backend:8080/api/v1";
const evidenceRunId = safeRunId(__ENV.RABBIT_EVIDENCE_RUN_ID || "manual");

export const options = {
  scenarios: {
    approved_pilot_plus_headroom: {
      executor: "constant-vus",
      vus: loadVus,
      duration,
      gracefulStop: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500", "p(99)<1000"],
    checks: ["rate>0.99"],
  },
  noConnectionReuse: false,
  userAgent: "Rabbit-M5.3-local-load-evidence",
};

export function setup() {
  const email = required(config.RABBIT_LOAD_STUDENT_EMAIL, "RABBIT_LOAD_STUDENT_EMAIL");
  if (email.toLowerCase().endsWith("@demo.rabbit.local")) {
    throw new Error("M5.3 load evidence cannot use a seeded demo identity.");
  }
  const password = required(
    config.RABBIT_LOAD_STUDENT_PASSWORD,
    "RABBIT_LOAD_STUDENT_PASSWORD",
  );
  const organisationCode = required(
    config.RABBIT_RECOVERY_ORGANISATION_CODE,
    "RABBIT_RECOVERY_ORGANISATION_CODE",
  );

  let response = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "POST /auth/login (setup)" },
    },
  );
  assertStatus(response, 200, "Load-test student login failed");
  let payload = response.json();

  if (payload.requiresOrganisationSelection) {
    const choice = (payload.organisations || []).find(
      (item) => item.code === organisationCode,
    );
    if (!choice) {
      throw new Error("The configured recovery organisation is unavailable to the load user.");
    }
    response = http.post(
      `${baseUrl}/auth/select-organisation`,
      JSON.stringify({
        selectionToken: payload.selectionToken,
        organisationId: choice.id,
      }),
      {
        headers: { "Content-Type": "application/json" },
        tags: { name: "POST /auth/select-organisation (setup)" },
      },
    );
    assertStatus(response, 200, "Load-test organisation selection failed");
    payload = response.json();
  }

  if (!payload.accessToken || payload.role !== "STUDENT") {
    throw new Error("The configured load identity must resolve to an active Student session.");
  }
  return { token: payload.accessToken, refreshToken: payload.refreshToken };
}

export default function (data) {
  const params = {
    headers: {
      Authorization: `Bearer ${data.token}`,
      Accept: "application/json",
    },
  };
  const responses = http.batch([
    ["GET", `${baseUrl}/student/assessments`, null, {
      ...params,
      tags: { name: "GET /student/assessments" },
    }],
    ["GET", `${baseUrl}/dashboard`, null, {
      ...params,
      tags: { name: "GET /dashboard" },
    }],
    ["GET", `${baseUrl}/reports/students/me/analytics`, null, {
      ...params,
      tags: { name: "GET /reports/students/me/analytics" },
    }],
  ]);

  check(responses[0], {
    "assessment list is available": (response) => response.status === 200,
  });
  check(responses[1], {
    "student dashboard is available": (response) => response.status === 200,
  });
  check(responses[2], {
    "student analytics is available": (response) => response.status === 200,
  });

  // One protected account is used only for this read profile. Pacing keeps the
  // aggregate request rate below the configured per-user application limit.
  sleep(thinkSeconds + Math.random() * 2);
}

export function teardown(data) {
  if (!data?.refreshToken) return;
  const response = http.post(
    `${baseUrl}/auth/logout`,
    JSON.stringify({ refreshToken: data.refreshToken }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "POST /auth/logout (teardown)" },
    },
  );
  check(response, {
    "load-test session revoked": (result) => result.status === 204,
  });
}

export function handleSummary(data) {
  const durationMetric = data.metrics.http_req_duration;
  const failedMetric = data.metrics.http_req_failed;
  const checksMetric = data.metrics.checks;
  const result = {
    evidenceType: "Rabbit M5.3 local performance",
    approvedConcurrentStudents: approvedStudents,
    testedVirtualStudents: loadVus,
    headroomPercent: 50,
    duration,
    requestsPerIteration,
    pacingSeconds: thinkSeconds,
    thresholds: {
      errorRateBelowOnePercent: thresholdPassed(failedMetric, "rate<0.01"),
      p95Below500Ms: thresholdPassed(durationMetric, "p(95)<500"),
      p99Below1000Ms: thresholdPassed(durationMetric, "p(99)<1000"),
      checksAbove99Percent: thresholdPassed(checksMetric, "rate>0.99"),
    },
    observed: {
      errorRate: metricValue(failedMetric, "rate"),
      p95Ms: metricValue(durationMetric, "p(95)"),
      p99Ms: metricValue(durationMetric, "p(99)"),
      checkRate: metricValue(checksMetric, "rate"),
      requests: metricValue(data.metrics.http_reqs, "count"),
      iterations: metricValue(data.metrics.iterations, "count"),
    },
  };
  result.passed = Object.values(result.thresholds).every(Boolean);

  return {
    [`/evidence/${evidenceRunId}/performance-summary.json`]: `${JSON.stringify(result, null, 2)}\n`,
    [`/evidence/${evidenceRunId}/performance-raw-summary.json`]: `${JSON.stringify(data, null, 2)}\n`,
    stdout: `Rabbit M5.3 performance ${result.passed ? "PASSED" : "FAILED"}: `
      + `${loadVus} virtual students (${approvedStudents} approved + 50% headroom).\n`,
  };
}

function parseEnv(contents) {
  const values = {};
  for (const rawLine of contents.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const separator = line.indexOf("=");
    if (separator < 1) continue;
    const key = line.slice(0, separator).trim();
    let value = line.slice(separator + 1).trim();
    if (
      value.length >= 2
      && ((value.startsWith('"') && value.endsWith('"'))
        || (value.startsWith("'") && value.endsWith("'")))
    ) {
      value = value.slice(1, -1);
    }
    values[key] = value;
  }
  return values;
}

function required(value, name) {
  if (!value || value.includes("REPLACE_ME")) {
    throw new Error(`${name} must be populated in the protected M5.3 config.`);
  }
  return value;
}

function positiveInteger(value, name, maximum) {
  if (!/^\d+$/.test(value || "")) {
    throw new Error(`${name} must be a positive integer.`);
  }
  const parsed = Number(value);
  if (parsed < 1 || parsed > maximum) {
    throw new Error(`${name} must be between 1 and ${maximum}.`);
  }
  return parsed;
}

function validatedDuration(value) {
  if (!/^([2-9]|[1-9]\d+)[m]$/.test(value) && !/^([1-9]\d*)[h]$/.test(value)) {
    throw new Error("PILOT_LOAD_DURATION must be at least 2m and use m or h units.");
  }
  return value;
}

function safeRunId(value) {
  if (!/^[A-Za-z0-9._-]+$/.test(value)) {
    throw new Error("RABBIT_EVIDENCE_RUN_ID contains unsafe characters.");
  }
  return value;
}

function assertStatus(response, expected, message) {
  if (response.status !== expected) {
    throw new Error(`${message} (HTTP ${response.status}).`);
  }
}

function thresholdPassed(metric, name) {
  return Boolean(metric && metric.thresholds && metric.thresholds[name]?.ok);
}

function metricValue(metric, name) {
  return metric && metric.values ? (metric.values[name] ?? null) : null;
}
