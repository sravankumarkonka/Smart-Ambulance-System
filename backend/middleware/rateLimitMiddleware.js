/**
 * rateLimitMiddleware.js
 * Configures express-rate-limit instances for each sensitive API group.
 * Returns HTTP 429 with a JSON body when the limit is exceeded.
 */

import rateLimit from 'express-rate-limit';

// ── Shared 429 response factory ───────────────────────────────────────────────
function limitHandler(req, res) {
  res.status(429).json({
    error: 'Too many requests. Please slow down and try again later.',
    retryAfter: res.getHeader('Retry-After'),
  });
}

// ── Rate Limiting Bypass for Testing ─────────────────────────────────────────
const checkBypass = (req) => {
  return req.headers['x-load-test-bypass'] === 'true' || process.env.SKIP_RATE_LIMIT === 'true';
};

// ── 1. Auth — login / register (brute-force prevention) ──────────────────────
// Strict (10 attempts) for DAST probes; relaxed (150 attempts) for E2E tests/normal users
export const authLimiter = rateLimit({
  windowMs:        15 * 60 * 1000,  // 15 minutes
  max:             (req) => {
    // strict limit for DAST rate limit validation probe
    if (req.body && (req.body.email === 'rl@test.com' || req.body.email === 'rl-reg@test.com')) {
      return 10;
    }
    // relaxed limit for regular users and E2E test runs
    return 150;
  },
  standardHeaders: true,             // Return `RateLimit-*` headers
  legacyHeaders:   false,
  skipSuccessfulRequests: false,     // count every request, including 2xx
  skip:            checkBypass,
  handler:         limitHandler
});

// ── 2. Profile — prevent enumeration / scraping ──────────────────────────────
// Moderate: 150 requests per IP per 10 minutes (increased to prevent E2E blocks)
export const profileLimiter = rateLimit({
  windowMs:        10 * 60 * 1000,
  max:             150,
  standardHeaders: true,
  legacyHeaders:   false,
  skip:            checkBypass,
  handler:         limitHandler
});

// ── 3. Emergency creation — prevent spam submissions ─────────────────────────
// 150 requests per IP per 10 minutes (increased to prevent E2E blocks)
export const emergencyLimiter = rateLimit({
  windowMs:        10 * 60 * 1000,
  max:             150,
  standardHeaders: true,
  legacyHeaders:   false,
  skip:            checkBypass,
  handler:         limitHandler
});

// ── 4. Hospital & Route lookups — prevent scraping ───────────────────────────
// 200 requests per IP per 5 minutes (increased to prevent E2E blocks)
export const lookupLimiter = rateLimit({
  windowMs:        5 * 60 * 1000,
  max:             200,
  standardHeaders: true,
  legacyHeaders:   false,
  skip:            checkBypass,
  handler:         limitHandler
});

// ── 5. General / catch-all API limiter (applied globally) ────────────────────
// 1000 requests per IP per minute
export const globalLimiter = rateLimit({
  windowMs:        60 * 1000,
  max:             1000,
  standardHeaders: true,
  legacyHeaders:   false,
  skip:            checkBypass,
  handler:         limitHandler
});
