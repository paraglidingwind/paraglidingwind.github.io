/* Dimensiunile unei imagini WebP, din antet (VP8, VP8L sau VP8X). Folosit de tools/guide-shots.mjs și de tests/guide.test.mjs. */
export function webpSize(b){
  if (b.toString('ascii', 0, 4) !== 'RIFF' || b.toString('ascii', 8, 12) !== 'WEBP') return null;
  const kind = b.toString('ascii', 12, 16);
  if (kind === 'VP8X') return {width: 1 + b.readUIntLE(24, 3), height: 1 + b.readUIntLE(27, 3)};
  if (kind === 'VP8 ') return {width: b.readUInt16LE(26) & 0x3fff, height: b.readUInt16LE(28) & 0x3fff};
  if (kind === 'VP8L'){ const v = b.readUInt32LE(21); return {width: 1 + (v & 0x3fff), height: 1 + ((v >> 14) & 0x3fff)}; }
  return null;
}
