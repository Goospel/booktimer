// 바코드 → ISBN-13(v1 REQ-32). 책 아닌 바코드(880 상품 등)와 체크 숫자가 틀린 값은 null.
export function toIsbn13(raw: string): string | null {
  const s = raw.trim();
  if (!/^97[89]\d{10}$/.test(s)) return null;
  let sum = 0;
  for (let i = 0; i < 12; i++) sum += Number(s[i]) * (i % 2 === 0 ? 1 : 3);
  return (10 - (sum % 10)) % 10 === Number(s[12]) ? s : null;
}
