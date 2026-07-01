const COOKIE_NAME = 'guestId';
const TTL_DAYS = 30;

/** UUID v4 생성 — secure context 여부와 무관하게 동작 */
const generateUUID = () =>
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });

/** 브라우저 쿠키에서 guestId를 읽거나, 없으면 UUID 생성 후 30일 쿠키로 저장 */
export const getOrCreateGuestId = () => {
  const match = document.cookie.split(';').find((c) => c.trim().startsWith(`${COOKIE_NAME}=`));
  if (match) return match.trim().split('=')[1];

  const id = generateUUID();
  const expires = new Date(Date.now() + TTL_DAYS * 24 * 60 * 60 * 1000).toUTCString();
  document.cookie = `${COOKIE_NAME}=${id}; expires=${expires}; path=/; SameSite=Lax`;
  return id;
};
