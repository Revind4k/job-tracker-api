// k6 load test: GET /applications/stats for a user with a large history.
// Run with the app up:  k6 run loadtest/stats.js
import http from "k6/http";
import { check } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  vus: 20,
  duration: "30s",
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
};

export function setup() {
  const creds = JSON.stringify({ email: "loadtest@example.com", password: "loadtest-password" });
  const res = http.post(`${BASE}/auth/login`, creds, { headers: { "Content-Type": "application/json" } });
  return { token: res.json("accessToken") };
}

export default function (data) {
  const res = http.get(`${BASE}/applications/stats`, { headers: { Authorization: `Bearer ${data.token}` } });
  check(res, { "200": (r) => r.status === 200 });
}
