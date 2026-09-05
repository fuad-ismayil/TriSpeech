export function loadTokenMixKeys(env = process.env) {
  const numbered = Object.entries(env)
    .map(([name, value]) => {
      const match = /^TOKENMIX_KEY_([1-9]\d*)$/.exec(name);
      const secret = typeof value === "string" ? value.trim() : "";
      return match && secret ? { number: Number(match[1]), value: secret } : null;
    })
    .filter(Boolean)
    .sort((left, right) => left.number - right.number);

  if (numbered.length) return uniqueKeys(numbered.map(({ number, value }) => ({
    alias: `tokenmix-key-${number}`,
    priority: number,
    value
  })));

  return uniqueKeys([
    { alias: "tokenmix-primary", value: env.TOKENMIX_PRIMARY_KEY?.trim(), priority: 1 },
    { alias: "tokenmix-secondary", value: env.TOKENMIX_SECONDARY_KEY?.trim(), priority: 2 }
  ]);
}

export function rewardConfig(env = process.env) {
  const provider = env.AD_PROVIDER ?? "admob";
  if (!["admob", "startio"].includes(provider)) throw new Error("Unsupported AD_PROVIDER.");
  const seconds = Number(env.STARTIO_REWARD_SECONDS ?? 60);
  if (!Number.isInteger(seconds) || seconds < 30 || seconds > 120) throw new Error("STARTIO_REWARD_SECONDS must be an integer from 30 to 120.");
  const clientRewards = env.STARTIO_CLIENT_REWARDS_ENABLED === "true";
  const testRewards = env.STARTIO_TEST_REWARDS === "true";
  if (provider === "startio" && clientRewards && env.DEVELOPMENT_ACCESS_MODE === "true") {
    throw new Error("Start.io credit access cannot be combined with DEVELOPMENT_ACCESS_MODE.");
  }
  if (provider === "startio" && clientRewards && !testRewards && env.NODE_ENV !== "test") {
    const ephemeralLive = env.ALLOW_EPHEMERAL_LIVE_REWARDS === "true";
    const missingDurableInfrastructure = env.USE_MEMORY_STORE === "true" || !env.FIREBASE_PROJECT_ID || !env.UPLOAD_BUCKET || !env.CLOUD_TASKS_QUEUE || !env.WORKER_URL;
    if ((!ephemeralLive && missingDurableInfrastructure) || (ephemeralLive && env.USE_MEMORY_STORE !== "true")) {
      throw new Error("Live Start.io rewards require durable infrastructure, unless ALLOW_EPHEMERAL_LIVE_REWARDS=true is explicitly enabled for a short smoke test.");
    }
    const budget = Number(env.PROVIDER_DAILY_BUDGET_USD);
    if (!(budget > 0 && budget <= 1)) throw new Error("The Start.io experiment requires a positive daily backend budget no greater than $1.");
  }
  return { provider, seconds, clientRewards, testRewards };
}

function uniqueKeys(keys) {
  const seenValues = new Set();
  return keys.filter((key) => {
    if (!key.value || seenValues.has(key.value)) return false;
    seenValues.add(key.value);
    return true;
  });
}
