function extractId(value) {
  if (!value) return null;
  if (typeof value === 'string') return value;
  if (value._id) return value._id.toString();
  if (value.id) return value.id.toString();
  return value.toString();
}
function extractJoinedUserIds(participants = []) {
  return participants
    .map(p => extractId(p.userId))
    .filter(Boolean);
}
module.exports = { extractId, extractJoinedUserIds };
