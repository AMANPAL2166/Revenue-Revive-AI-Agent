# Seed data

This folder was scaffolded on Day 1 for a raw `.sql` seed script, but the
actual implementation lives in Java instead:

**`com.reviveai.seed.DemoDataSeeder`** (a `CommandLineRunner`).

Why: hand-writing UUID foreign keys across `customers`, `payments`,
`subscriptions`, `recovery_cases`, and `agent_actions` in raw SQL is
error-prone and easy to silently desync from the entity model as it
evolves. The Java seeder instead calls the **real**
`RevenueRiskService`, `PolicyEngine`, and `RecoveryActionExecutor` —
the same production code paths a live webhook would use — so every
number shown in the demo (recovery probability, priority, policy
verdicts) is genuine algorithm output, not hand-typed data that could
drift out of sync with the actual calculations.

See `DemoDataSeeder`'s class-level Javadoc for the full explanation,
including why it deliberately skips the AI/LLM call.
