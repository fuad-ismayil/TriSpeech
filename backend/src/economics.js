export function rewardSeconds({ conservativeEcpm, allInMinuteCost, targetMargin = 0.25 }) {
  if (!(conservativeEcpm > 0) || !(allInMinuteCost > 0) || targetMargin < 0 || targetMargin >= 1) return 0;
  const affordableMinutes = (conservativeEcpm / 1000) * (1 - targetMargin) / allInMinuteCost;
  const stepped = Math.floor((affordableMinutes * 60) / 15) * 15;
  return Math.max(30, Math.min(120, stepped));
}

export function marketReward(market, table, targetMargin) {
  const data = table[market] ?? table.ZZ;
  if (!data || data.enabled === false) return 0;
  const rawAffordable = (data.conservativeEcpm / 1000) * (1 - targetMargin) / data.allInMinuteCost * 60;
  if (rawAffordable < 30) return 0;
  return rewardSeconds({ ...data, targetMargin });
}
