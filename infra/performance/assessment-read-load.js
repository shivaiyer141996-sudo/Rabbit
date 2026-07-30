import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  stages: [
    { duration: "30s", target: 20 },
    { duration: "2m", target: 20 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500", "p(99)<1000"],
    checks: ["rate>0.99"],
  },
};

const baseUrl = __ENV.RABBIT_BASE_URL || "http://localhost";
const email = __ENV.RABBIT_STUDENT_EMAIL || "student@demo.rabbit.local";
const password = __ENV.RABBIT_STUDENT_PASSWORD || "Rabbit@123";

export function setup() {
  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(response, {
    "login succeeded": (result) => result.status === 200,
    "access token returned": (result) => Boolean(result.json("accessToken")),
  });
  return { token: response.json("accessToken") };
}

export default function (data) {
  const params = {
    headers: {
      Authorization: `Bearer ${data.token}`,
      Accept: "application/json",
    },
  };
  const assessments = http.get(`${baseUrl}/api/v1/student/assessments`, params);
  const dashboard = http.get(`${baseUrl}/api/v1/dashboard`, params);
  check(assessments, {
    "assessment list is available": (response) => response.status === 200,
  });
  check(dashboard, {
    "student dashboard is available": (response) => response.status === 200,
  });
  sleep(1);
}
