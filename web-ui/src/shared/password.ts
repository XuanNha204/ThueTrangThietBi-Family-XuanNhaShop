export const passwordHint = 'Ít nhất 8 ký tự, gồm 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt (ví dụ ! hoặc @).';
export const passwordPattern = String.raw`(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[^\p{L}\p{N}\s]).{8,72}`;
