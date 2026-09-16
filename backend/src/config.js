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
  const clientRewards = env.ADMOB_CLIENT_REWARDS_ENABLED === "true";
  const testRewards = env.ADMOB_TEST_REWARDS === "true";
  const ssv = {
    adUnitId: normalizeAdUnitId(env.ADMOB_REWARDED_AD_UNIT_ID),
    rewardAmount: env.ADMOB_SSV_REWARD_AMOUNT?.trim() ?? "",
    rewardItem: env.ADMOB_SSV_REWARD_ITEM?.trim() ?? ""
  };
  if (clientRewards && env.DEVELOPMENT_ACCESS_MODE === "true") {
    throw new Error("AdMob credit access cannot be combined with DEVELOPMENT_ACCESS_MODE.");
  }
  if (clientRewards && !testRewards) {
    throw new Error("Client-reported AdMob completion is allowed only for test ads.");
  }
  if (env.NODE_ENV === "production" && env.DEVELOPMENT_ACCESS_MODE !== "true" && !testRewards && Object.values(ssv).some((value) => !value)) {
    throw new Error("Production AdMob rewards require ADMOB_REWARDED_AD_UNIT_ID, ADMOB_SSV_REWARD_AMOUNT, and ADMOB_SSV_REWARD_ITEM.");
  }
  return { provider: "admob", clientRewards, testRewards, ssv };
}

function normalizeAdUnitId(value) {
  const candidate = value?.trim() ?? "";
  const match = /(?:^|\/)(\d+)$/.exec(candidate);
  return match?.[1] ?? "";
}

function uniqueKeys(keys) {
  const seenValues = new Set();
  return keys.filter((key) => {
    if (!key.value || seenValues.has(key.value)) return false;
    seenValues.add(key.value);
    return true;
  });
}
