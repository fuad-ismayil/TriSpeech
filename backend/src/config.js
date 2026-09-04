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

function uniqueKeys(keys) {
  const seenValues = new Set();
  return keys.filter((key) => {
    if (!key.value || seenValues.has(key.value)) return false;
    seenValues.add(key.value);
    return true;
  });
}
